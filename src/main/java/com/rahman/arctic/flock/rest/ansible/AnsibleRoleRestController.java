package com.rahman.arctic.flock.rest.ansible;

import java.util.List;

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

import com.rahman.arctic.flock.exceptions.ResourceAlreadyExistsException;
import com.rahman.arctic.iceberg.ansible.AnsibleRole;
import com.rahman.arctic.iceberg.ansible.AnsibleRoleDTO;
import com.rahman.arctic.iceberg.repos.AnsibleRoleRepo;
import com.rahman.arctic.orca.utils.ArcticUserDetails;
import com.rahman.arctic.orca.utils.ExercisePermissionService;

@RestController
@RequestMapping("/range-api/v1/ansible/role")
public class AnsibleRoleRestController {

	private final ExercisePermissionService permissionService;

	@Autowired
	public AnsibleRoleRestController(ExercisePermissionService eps) {
		this.permissionService = eps;
	}

	@Autowired
	private AnsibleRoleRepo roleRepo;

	@GetMapping
	ResponseEntity<List<AnsibleRole>> listRoles() {
		if (currentUser() == null) return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		return new ResponseEntity<>(roleRepo.findAll(), HttpStatus.OK);
	}

	@GetMapping("/{id}")
	ResponseEntity<AnsibleRole> getRole(@PathVariable String id) {
		if (currentUser() == null) return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		AnsibleRole role = roleRepo.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Ansible role not found with id: " + id));
		return new ResponseEntity<>(role, HttpStatus.OK);
	}

	@PostMapping(produces = "application/json", consumes = "application/json")
	ResponseEntity<AnsibleRole> createRole(@RequestBody AnsibleRoleDTO dto) throws ResourceAlreadyExistsException {
		ArcticUserDetails details = currentUser();
		if (details == null) return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		if (!permissionService.isGlobalAdmin(details)) return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		if (roleRepo.findByName(dto.getName()).isPresent())
			throw new ResourceAlreadyExistsException("Ansible role with name: " + dto.getName() + " already exists!");

		AnsibleRole role = new AnsibleRole();
		role.setName(dto.getName());
		role.setDescription(dto.getDescription());
		role.setContent(dto.getContent());
		roleRepo.save(role);
		return new ResponseEntity<>(role, HttpStatus.CREATED);
	}

	@PutMapping(value = "/{id}", produces = "application/json", consumes = "application/json")
	ResponseEntity<AnsibleRole> updateRole(@PathVariable String id, @RequestBody AnsibleRoleDTO dto) {
		ArcticUserDetails details = currentUser();
		if (details == null) return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		if (!permissionService.isGlobalAdmin(details)) return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		AnsibleRole role = roleRepo.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Ansible role not found with id: " + id));
		role.setName(dto.getName());
		role.setDescription(dto.getDescription());
		role.setContent(dto.getContent());
		roleRepo.save(role);
		return new ResponseEntity<>(role, HttpStatus.OK);
	}

	@DeleteMapping("/{id}")
	ResponseEntity<?> deleteRole(@PathVariable String id) {
		ArcticUserDetails details = currentUser();
		if (details == null) return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
		if (!permissionService.isGlobalAdmin(details)) return new ResponseEntity<>("Forbidden", HttpStatus.FORBIDDEN);
		AnsibleRole role = roleRepo.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Ansible role not found with id: " + id));
		roleRepo.delete(role);
		return new ResponseEntity<>("", HttpStatus.OK);
	}

	private ArcticUserDetails currentUser() {
		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		return (principal instanceof ArcticUserDetails) ? (ArcticUserDetails) principal : null;
	}

}
