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
		System.out.println("[Deployment] POST /deployment called");
		ArcticUserDetails details = currentUser();
		System.out.println("[Deployment] currentUser = " + (details != null ? details.getUsername() : "null"));
		if (details == null) return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);

		System.out.println("[Deployment] roles = " + details.getAuthorities());

		// Engineers cannot trigger hypervisor operations
		if (permissionService.hasGlobalRole(details, UserRole.ENGINEER))
			return new ResponseEntity<>("Forbidden — Engineers cannot trigger hypervisor operations", HttpStatus.FORBIDDEN);

		System.out.println("[Deployment] exerciseName = " + request.getExerciseName() + ", profileName = " + request.getProfileName());

		RangeExercise range = exRepo.findByName(request.getExerciseName().replaceAll(" ", "_"))
				.orElseThrow(() -> new ResourceNotFoundException("Exercise not found: " + request.getExerciseName()));

		System.out.println("[Deployment] found exercise: " + range.getId() + " / " + range.getName());

		// Build a quick id→name map for cross-referencing host attachments
		java.util.Map<String, String> networkIdToName = new java.util.HashMap<>();
		System.out.println("[Deployment] networks (" + range.getNetworks().size() + "):");
		for (com.rahman.arctic.iceberg.objects.computers.ArcticNetwork net : range.getNetworks()) {
			networkIdToName.put(net.getId(), net.getNetName());
			System.out.println("  network: id=" + net.getId() + " name=" + net.getNetName() + " cidr=" + net.getNetCidr());
		}

		System.out.println("[Deployment] hosts (" + range.getHosts().size() + "):");
		for (com.rahman.arctic.iceberg.objects.computers.ArcticHost host : range.getHosts()) {
			String attachedNetworks = host.getNetworks().stream()
					.map(nid -> nid + "(" + networkIdToName.getOrDefault(nid, "?") + ")")
					.collect(java.util.stream.Collectors.joining(", "));
			System.out.println("  host: id=" + host.getId() + " name=" + host.getName()
					+ " os=" + host.getOsType() + " networks=[" + attachedNetworks + "]");
		}

		System.out.println("[Deployment] isGlobalAdmin = " + permissionService.isGlobalAdmin(details));

		if (!permissionService.isGlobalAdmin(details)
				&& !permissionService.hasPermission(details.getUsername(), range.getId(), ExerciseRole.RANGE_CREATE))
			return new ResponseEntity<>("Forbidden", HttpStatus.FORBIDDEN);

		ShardProfile sp = profileRepo.findByUsernameAndProfileName(details.getUsername(), request.getProfileName())
				.orElseThrow(() -> new ResourceNotFoundException("No profile found with name: " + request.getProfileName()));

		System.out.println("[Deployment] found profile: " + sp.getProfileName() + " domain=" + sp.getDomain());

		RangeDeployment deployment = new RangeDeployment();
		deployment.setExerciseId(range.getId());
		deployment.setExerciseName(range.getName());
		deployment.setDomain(sp.getDomain());
		deployment.setProfileName(sp.getProfileName());
		deployment.setDeployedBy(details.getUsername());
		deployment.setDeployedAt(new Date());
		deployment.setStatus("Building");
		deploymentRepo.save(deployment);

		IcebergCreator ic = icebergCreatorProvider.getObject();
		ic.setProfile(sp);
		try {
			ic.attemptCreation();
		} catch (Exception e) {
			deployment.setStatus("Error");
			deploymentRepo.save(deployment);
			return new ResponseEntity<>("Error creating client", HttpStatus.BAD_REQUEST);
		}

		range.getNetworks().forEach(ic::createNetwork);
		range.getRouters().forEach(ic::createRouter);
		range.getHosts().forEach(ic::createHost);

		ic.start();

		deployment.setStatus("Running");
		deploymentRepo.save(deployment);

		return new ResponseEntity<>(deployment, HttpStatus.CREATED);
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

		IcebergCreator ic = icebergCreatorProvider.getObject();
		ic.setProfile(sp);
		ic.setDestroyMode(true);
		try {
			ic.attemptCreation();
		} catch (Exception e) {
			deployment.setStatus("Error");
			deploymentRepo.save(deployment);
			return new ResponseEntity<>("Error creating client", HttpStatus.BAD_REQUEST);
		}

		// Register hosts and routers first so their tasks exist when destroyNetwork
		// builds its dependency list (networks must wait for VMs to release leases).
		range.getHosts().forEach(ic::destroyHost);
		range.getRouters().forEach(ic::destroyRouter);
		range.getNetworks().forEach(ic::destroyNetwork);

		ic.start();

		deployment.setStatus("Destroyed");
		deploymentRepo.save(deployment);

		return new ResponseEntity<>(deployment, HttpStatus.OK);
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
