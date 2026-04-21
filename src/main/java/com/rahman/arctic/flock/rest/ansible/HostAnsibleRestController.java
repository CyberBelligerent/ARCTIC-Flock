package com.rahman.arctic.flock.rest.ansible;

import java.util.HashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rahman.arctic.iceberg.ansible.AnsibleRole;
import com.rahman.arctic.iceberg.ansible.HostAnsibleViewDTO;
import com.rahman.arctic.iceberg.ansible.HostInlineScript;
import com.rahman.arctic.iceberg.ansible.HostInlineScriptDTO;
import com.rahman.arctic.iceberg.ansible.HostRoleAssignment;
import com.rahman.arctic.iceberg.ansible.HostRoleAssignmentDTO;
import com.rahman.arctic.iceberg.objects.RangeExercise;
import com.rahman.arctic.iceberg.objects.computers.ArcticHost;
import com.rahman.arctic.iceberg.repos.AnsibleRoleRepo;
import com.rahman.arctic.iceberg.repos.ArcticHostRepo;
import com.rahman.arctic.iceberg.repos.ExerciseRepo;
import com.rahman.arctic.orca.objects.role.ExerciseRole;
import com.rahman.arctic.orca.objects.role.UserRole;
import com.rahman.arctic.orca.utils.ArcticUserDetails;
import com.rahman.arctic.orca.utils.ExercisePermissionService;

@RestController
@RequestMapping("/range-api/v1/exercise/{name}/host/{hostName}/ansible")
public class HostAnsibleRestController {

	private final ExercisePermissionService permissionService;

	@Autowired
	public HostAnsibleRestController(ExercisePermissionService eps) {
		this.permissionService = eps;
	}

	@Autowired
	private ExerciseRepo exRepo;

	@Autowired
	private ArcticHostRepo hostRepo;

	@Autowired
	private AnsibleRoleRepo roleRepo;

	@GetMapping
	ResponseEntity<HostAnsibleViewDTO> getHostAnsible(@PathVariable String name, @PathVariable String hostName) {
		ArcticUserDetails details = currentUser();
		if (details == null) return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		RangeExercise range = lookupExercise(name);
		if (!canView(details, range.getId())) return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		ArcticHost host = lookupHost(range, hostName);

		HostAnsibleViewDTO view = new HostAnsibleViewDTO();
		view.setRoleAssignments(host.getRoleAssignments());
		view.setInlineScripts(host.getInlineScripts());
		return new ResponseEntity<>(view, HttpStatus.OK);
	}

	@PostMapping(value = "/role", produces = "application/json", consumes = "application/json")
	ResponseEntity<HostRoleAssignment> addRoleAssignment(@PathVariable String name, @PathVariable String hostName,
			@RequestBody HostRoleAssignmentDTO dto) {
		ArcticUserDetails details = currentUser();
		if (details == null) return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		RangeExercise range = lookupExercise(name);
		if (!canEdit(details, range.getId())) return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		ArcticHost host = lookupHost(range, hostName);

		AnsibleRole role = roleRepo.findById(dto.getRoleId())
				.orElseThrow(() -> new ResourceNotFoundException("Ansible role not found with id: " + dto.getRoleId()));

		HostRoleAssignment assignment = new HostRoleAssignment();
		assignment.setRoleId(role.getId());
		assignment.setRunOrder(dto.getRunOrder());
		assignment.setOverrideVariables(dto.getOverrideVariables() != null ? dto.getOverrideVariables() : new HashMap<>());
		host.getRoleAssignments().add(assignment);
		hostRepo.save(host);
		return new ResponseEntity<>(assignment, HttpStatus.CREATED);
	}

	@PutMapping(value = "/role/{assignmentId}", produces = "application/json", consumes = "application/json")
	ResponseEntity<HostRoleAssignment> updateRoleAssignment(@PathVariable String name, @PathVariable String hostName,
			@PathVariable String assignmentId, @RequestBody HostRoleAssignmentDTO dto) {
		ArcticUserDetails details = currentUser();
		if (details == null) return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		RangeExercise range = lookupExercise(name);
		if (!canEdit(details, range.getId())) return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		ArcticHost host = lookupHost(range, hostName);

		HostRoleAssignment assignment = host.getRoleAssignments().stream()
				.filter(a -> assignmentId.equals(a.getId())).findAny()
				.orElseThrow(() -> new ResourceNotFoundException("Assignment not found with id: " + assignmentId));
		assignment.setRunOrder(dto.getRunOrder());
		assignment.setOverrideVariables(dto.getOverrideVariables() != null ? dto.getOverrideVariables() : new HashMap<>());
		hostRepo.save(host);
		return new ResponseEntity<>(assignment, HttpStatus.OK);
	}

	@DeleteMapping("/role/{assignmentId}")
	ResponseEntity<?> removeRoleAssignment(@PathVariable String name, @PathVariable String hostName,
			@PathVariable String assignmentId) {
		ArcticUserDetails details = currentUser();
		if (details == null) return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
		RangeExercise range = lookupExercise(name);
		if (!canEdit(details, range.getId())) return new ResponseEntity<>("Forbidden", HttpStatus.FORBIDDEN);
		ArcticHost host = lookupHost(range, hostName);

		HostRoleAssignment assignment = host.getRoleAssignments().stream()
				.filter(a -> assignmentId.equals(a.getId())).findAny()
				.orElseThrow(() -> new ResourceNotFoundException("Assignment not found with id: " + assignmentId));
		host.getRoleAssignments().remove(assignment);
		hostRepo.save(host);
		return new ResponseEntity<>("", HttpStatus.OK);
	}

	@PostMapping(value = "/script", produces = "application/json", consumes = "application/json")
	ResponseEntity<HostInlineScript> addInlineScript(@PathVariable String name, @PathVariable String hostName,
			@RequestBody HostInlineScriptDTO dto) {
		ArcticUserDetails details = currentUser();
		if (details == null) return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		RangeExercise range = lookupExercise(name);
		if (!canEdit(details, range.getId())) return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		ArcticHost host = lookupHost(range, hostName);

		HostInlineScript script = new HostInlineScript();
		script.setName(dto.getName());
		script.setRunOrder(dto.getRunOrder());
		script.setContent(dto.getContent());
		host.getInlineScripts().add(script);
		hostRepo.save(host);
		return new ResponseEntity<>(script, HttpStatus.CREATED);
	}

	@PutMapping(value = "/script/{scriptId}", produces = "application/json", consumes = "application/json")
	ResponseEntity<HostInlineScript> updateInlineScript(@PathVariable String name, @PathVariable String hostName,
			@PathVariable String scriptId, @RequestBody HostInlineScriptDTO dto) {
		ArcticUserDetails details = currentUser();
		if (details == null) return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		RangeExercise range = lookupExercise(name);
		if (!canEdit(details, range.getId())) return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		ArcticHost host = lookupHost(range, hostName);

		HostInlineScript script = host.getInlineScripts().stream()
				.filter(s -> scriptId.equals(s.getId())).findAny()
				.orElseThrow(() -> new ResourceNotFoundException("Script not found with id: " + scriptId));
		script.setName(dto.getName());
		script.setRunOrder(dto.getRunOrder());
		script.setContent(dto.getContent());
		hostRepo.save(host);
		return new ResponseEntity<>(script, HttpStatus.OK);
	}

	@DeleteMapping("/script/{scriptId}")
	ResponseEntity<?> removeInlineScript(@PathVariable String name, @PathVariable String hostName,
			@PathVariable String scriptId) {
		ArcticUserDetails details = currentUser();
		if (details == null) return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
		RangeExercise range = lookupExercise(name);
		if (!canEdit(details, range.getId())) return new ResponseEntity<>("Forbidden", HttpStatus.FORBIDDEN);
		ArcticHost host = lookupHost(range, hostName);

		HostInlineScript script = host.getInlineScripts().stream()
				.filter(s -> scriptId.equals(s.getId())).findAny()
				.orElseThrow(() -> new ResourceNotFoundException("Script not found with id: " + scriptId));
		host.getInlineScripts().remove(script);
		hostRepo.save(host);
		return new ResponseEntity<>("", HttpStatus.OK);
	}

	private boolean canView(ArcticUserDetails details, String exerciseId) {
		return permissionService.isGlobalAdmin(details)
				|| permissionService.hasGlobalRole(details, UserRole.ENGINEER)
				|| permissionService.hasGlobalRole(details, UserRole.INSTRUCTOR)
				|| permissionService.hasPermission(details.getUsername(), exerciseId, ExerciseRole.RANGE_VIEW);
	}

	private boolean canEdit(ArcticUserDetails details, String exerciseId) {
		return permissionService.isGlobalAdmin(details)
				|| permissionService.hasGlobalRole(details, UserRole.ENGINEER)
				|| permissionService.hasPermission(details.getUsername(), exerciseId, ExerciseRole.RANGE_ADMIN);
	}

	private RangeExercise lookupExercise(String name) {
		return exRepo.findByName(name.replaceAll(" ", "_"))
				.orElseThrow(() -> new ResourceNotFoundException("Exercise Not Found With Name: " + name));
	}

	private ArcticHost lookupHost(RangeExercise range, String hostName) {
		return range.getHosts().stream().filter(h -> hostName.equals(h.getName())).findAny()
				.orElseThrow(() -> new ResourceNotFoundException("Host Not Found With Name: " + hostName));
	}

	private ArcticUserDetails currentUser() {
		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		return (principal instanceof ArcticUserDetails) ? (ArcticUserDetails) principal : null;
	}

}
