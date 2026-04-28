package com.rahman.arctic.flock.rest;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rahman.arctic.iceberg.objects.RangeDeployment;
import com.rahman.arctic.iceberg.objects.RangeExercise;
import com.rahman.arctic.iceberg.repos.DeploymentRepo;
import com.rahman.arctic.iceberg.repos.ExerciseRepo;
import com.rahman.arctic.iceberg.services.IcebergCreator;
import com.rahman.arctic.orca.objects.role.ExerciseRole;
import com.rahman.arctic.orca.objects.role.UserRole;
import com.rahman.arctic.orca.utils.ArcticUserDetails;
import com.rahman.arctic.orca.utils.ExercisePermissionService;
import com.rahman.arctic.shard.configuration.persistence.ShardProfile;
import com.rahman.arctic.shard.repos.ShardProfileRepo;
import com.rahman.arctic.shard.util.ARCTICLog;

@RestController
@RequestMapping("/range-api/v1")
public class DeploymentRestController {

	private final ObjectProvider<IcebergCreator> icebergCreatorProvider;
	private final ExerciseRepo exRepo;
	private final DeploymentRepo deploymentRepo;
	private final ShardProfileRepo profileRepo;
	private final ExercisePermissionService permissionService;

	public DeploymentRestController(ObjectProvider<IcebergCreator> icebergCreatorProvider,
			ExerciseRepo exRepo,
			DeploymentRepo deploymentRepo,
			ShardProfileRepo profileRepo,
			ExercisePermissionService permissionService) {
		this.icebergCreatorProvider = icebergCreatorProvider;
		this.exRepo = exRepo;
		this.deploymentRepo = deploymentRepo;
		this.profileRepo = profileRepo;
		this.permissionService = permissionService;
	}

	@PostMapping(value = "/deployment", consumes = "application/json", produces = "application/json")
	ResponseEntity<?> deployExercise(@RequestBody DeploymentRequest request) {
		ArcticUserDetails details = currentUser();
		if (details == null) return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);

		if (permissionService.hasGlobalRole(details, UserRole.ENGINEER))
			return new ResponseEntity<>("Forbidden — Engineers cannot trigger hypervisor operations", HttpStatus.FORBIDDEN);

		RangeExercise range = exRepo.findByName(request.getExerciseName().replaceAll(" ", "_"))
				.orElseThrow(() -> new ResourceNotFoundException("Exercise not found: " + request.getExerciseName()));

		if (!permissionService.isGlobalAdmin(details)
				&& !permissionService.hasPermission(details.getUsername(), range.getId(), ExerciseRole.RANGE_CREATE))
			return new ResponseEntity<>("Forbidden", HttpStatus.FORBIDDEN);

		ShardProfile sp = profileRepo.findByUsernameAndProfileName(details.getUsername(), request.getProfileName())
				.orElseThrow(() -> new ResourceNotFoundException("No profile found with name: " + request.getProfileName()));

		RangeDeployment deployment = new RangeDeployment();
		deployment.setExerciseId(range.getId());
		deployment.setExerciseName(range.getName());
		deployment.setDomain(sp.getDomain());
		deployment.setProfileName(sp.getProfileName());
		deployment.setDeployedBy(details.getUsername());
		deployment.setDeployedAt(new Date());
		deployment.setStatus("Building");
		deploymentRepo.save(deployment);

		ARCTICLog.setDeployment(deployment.getId());
		try {
			ARCTICLog.print("Deployment", "POST /deployment by " + details.getUsername()
					+ " exercise=" + range.getName() + " profile=" + sp.getProfileName()
					+ " domain=" + sp.getDomain());

			java.util.Map<String, String> networkIdToName = new java.util.HashMap<>();
			ARCTICLog.print("Deployment", "networks (" + range.getNetworks().size() + ")");
			for (com.rahman.arctic.iceberg.objects.computers.ArcticNetwork net : range.getNetworks()) {
				networkIdToName.put(net.getId(), net.getNetName());
				ARCTICLog.print("Deployment", "  network: id=" + net.getId()
						+ " name=" + net.getNetName() + " cidr=" + net.getNetCidr());
			}

			ARCTICLog.print("Deployment", "host collections (" + range.getHostCollections().size() + ")");
			for (com.rahman.arctic.iceberg.objects.computers.HostCollection hc : range.getHostCollections()) {
				String attachedNetworks = hc.getNetworks().stream()
						.map(nid -> nid + "(" + networkIdToName.getOrDefault(nid, "?") + ")")
						.collect(java.util.stream.Collectors.joining(", "));
				ARCTICLog.print("Deployment", "  collection: id=" + hc.getId() + " name=" + hc.getName()
						+ " count=" + hc.getCount() + " os=" + hc.getOsType()
						+ " networks=[" + attachedNetworks + "]");
			}

			IcebergCreator ic = icebergCreatorProvider.getObject();
			ic.setProfile(sp);
			ic.setExercise(range);
			ic.setDeploymentId(deployment.getId());
			try {
				ic.attemptCreation();
			} catch (Exception e) {
				deployment.setStatus("Error");
				deploymentRepo.save(deployment);
				ARCTICLog.err("Deployment", "client creation failed: " + e.getMessage());
				return new ResponseEntity<>("Error creating client", HttpStatus.BAD_REQUEST);
			}

			range.getNetworks().forEach(ic::createNetwork);
			range.getRouters().forEach(ic::createRouter);
			ic.createAnsibleController(range);
			try {
				range.getHostCollections().forEach(ic::createHostCollection);
			} catch (IllegalArgumentException e) {
				deployment.setStatus("Error");
				deploymentRepo.save(deployment);
				ARCTICLog.err("Deployment", "host collection build rejected: " + e.getMessage());
				return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
			}

			ic.start();

			deployment.setStatus("Running");
			deploymentRepo.save(deployment);

			return new ResponseEntity<>(deployment, HttpStatus.CREATED);
		} finally {
			ARCTICLog.clearDeployment();
		}
	}

	@GetMapping(value = "/deployment", produces = "application/json")
	ResponseEntity<?> getAllDeployments() {
		ArcticUserDetails details = currentUser();
		if (details == null) return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);

		if (permissionService.isGlobalAdmin(details)
				|| permissionService.hasGlobalRole(details, UserRole.ENGINEER)
				|| permissionService.hasGlobalRole(details, UserRole.INSTRUCTOR))
			return new ResponseEntity<>(deploymentRepo.findAll(), HttpStatus.OK);

		return new ResponseEntity<>(deploymentRepo.findAllByDeployedBy(details.getUsername()), HttpStatus.OK);
	}

	@GetMapping(value = "/exercise/{name}/deployments", produces = "application/json")
	ResponseEntity<?> getDeploymentsForExercise(@PathVariable String name) {
		ArcticUserDetails details = currentUser();
		if (details == null) return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);

		RangeExercise range = exRepo.findByName(name.replaceAll(" ", "_"))
				.orElseThrow(() -> new ResourceNotFoundException("Exercise not found: " + name));

		if (!permissionService.isGlobalAdmin(details)
				&& !permissionService.hasGlobalRole(details, UserRole.ENGINEER)
				&& !permissionService.hasGlobalRole(details, UserRole.INSTRUCTOR)
				&& !permissionService.hasPermission(details.getUsername(), range.getId(), ExerciseRole.RANGE_VIEW))
			return new ResponseEntity<>("Forbidden", HttpStatus.FORBIDDEN);

		List<RangeDeployment> deployments = deploymentRepo.findAllByExerciseId(range.getId());
		return new ResponseEntity<>(deployments, HttpStatus.OK);
	}

	@DeleteMapping(value = "/deployment/{id}", produces = "application/json")
	ResponseEntity<?> destroyDeployment(@PathVariable String id) {
		ArcticUserDetails details = currentUser();
		if (details == null) return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);

		if (permissionService.hasGlobalRole(details, UserRole.ENGINEER))
			return new ResponseEntity<>("Forbidden — Engineers cannot trigger hypervisor operations", HttpStatus.FORBIDDEN);

		RangeDeployment deployment = deploymentRepo.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + id));

		RangeExercise range = exRepo.findById(deployment.getExerciseId())
				.orElseThrow(() -> new ResourceNotFoundException("Exercise not found for deployment"));

		if (!permissionService.isGlobalAdmin(details)
				&& !permissionService.hasPermission(details.getUsername(), range.getId(), ExerciseRole.RANGE_DESTROY))
			return new ResponseEntity<>("Forbidden", HttpStatus.FORBIDDEN);

		ShardProfile sp = profileRepo.findByUsernameAndProfileName(details.getUsername(), deployment.getProfileName())
				.orElseThrow(() -> new ResourceNotFoundException("No profile found with name: " + deployment.getProfileName()));

		deployment.setStatus("Destroying");
		deploymentRepo.save(deployment);

		ARCTICLog.setDeployment(deployment.getId());
		try {
			ARCTICLog.print("Deployment", "DELETE /deployment/" + id + " by " + details.getUsername()
					+ " exercise=" + range.getName());

			IcebergCreator ic = icebergCreatorProvider.getObject();
			ic.setProfile(sp);
			ic.setExercise(range);
			ic.setDestroyMode(true);
			ic.setDeploymentId(deployment.getId());
			try {
				ic.attemptCreation();
			} catch (Exception e) {
				deployment.setStatus("Error");
				deploymentRepo.save(deployment);
				ARCTICLog.err("Deployment", "client creation failed: " + e.getMessage());
				return new ResponseEntity<>("Error creating client", HttpStatus.BAD_REQUEST);
			}

			range.getHostCollections().forEach(ic::destroyHostCollection);
			ic.destroyAnsibleController(range);
			range.getRouters().forEach(ic::destroyRouter);
			range.getNetworks().forEach(ic::destroyNetwork);

			ic.start();

			deployment.setStatus("Destroyed");
			deploymentRepo.save(deployment);

			return new ResponseEntity<>(deployment, HttpStatus.OK);
		} finally {
			ARCTICLog.clearDeployment();
		}
	}

	@GetMapping(value = "/deployment/{id}/monitor", produces = "application/json")
	ResponseEntity<?> getDeploymentMonitor(@PathVariable String id) {
		ArcticUserDetails details = currentUser();
		if (details == null) return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);

		RangeDeployment deployment = deploymentRepo.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + id));

		RangeExercise range = exRepo.findById(deployment.getExerciseId())
				.orElseThrow(() -> new ResourceNotFoundException("Exercise not found for deployment"));

		if (!permissionService.isGlobalAdmin(details)
				&& !permissionService.hasGlobalRole(details, UserRole.ENGINEER)
				&& !permissionService.hasGlobalRole(details, UserRole.INSTRUCTOR)
				&& !permissionService.hasPermission(details.getUsername(), range.getId(), ExerciseRole.RANGE_VIEW))
			return new ResponseEntity<>("Forbidden", HttpStatus.FORBIDDEN);

		return new ResponseEntity<>(ARCTICLog.readHistory(id), HttpStatus.OK);
	}

	private ArcticUserDetails currentUser() {
		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		return (principal instanceof ArcticUserDetails) ? (ArcticUserDetails) principal : null;
	}

	public static class DeploymentRequest {
		private String exerciseName;
		private String profileName;

		public String getExerciseName() { return exerciseName; }
		public void setExerciseName(String exerciseName) { this.exerciseName = exerciseName; }

		public String getProfileName() { return profileName; }
		public void setProfileName(String profileName) { this.profileName = profileName; }
	}

}
