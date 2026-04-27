package com.rahman.arctic.flock.rest;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rahman.arctic.flock.exceptions.ResourceAlreadyExistsException;
import com.rahman.arctic.flock.websocket.GraphUpdateMessage;
import com.rahman.arctic.iceberg.ansible.AnsibleStager;
import com.rahman.arctic.iceberg.objects.RangeDTO;
import com.rahman.arctic.iceberg.objects.RangeExercise;
import com.rahman.arctic.iceberg.objects.RangeGraphDTO;
import com.rahman.arctic.iceberg.repos.ExerciseRepo;
import com.rahman.arctic.orca.objects.role.ExerciseRole;
import com.rahman.arctic.orca.objects.role.UserRole;
import com.rahman.arctic.orca.utils.ArcticUserDetails;
import com.rahman.arctic.orca.utils.ExercisePermissionService;

@RestController
@RequestMapping("/range-api/v1")
public class ExerciseRestController {

	private final ExercisePermissionService permissionService;
	private final SimpMessagingTemplate messagingTemplate;
	private final AnsibleStager ansibleStager;

	@Autowired
	public ExerciseRestController(ExercisePermissionService eps, SimpMessagingTemplate messagingTemplate,
			AnsibleStager ansibleStager) {
		permissionService = eps;
		this.messagingTemplate = messagingTemplate;
		this.ansibleStager = ansibleStager;
	}

	@Autowired
	private ExerciseRepo exRepo;

	@GetMapping("/exercise")
	ResponseEntity<List<RangeExercise>> getAllExercises() {
		ArcticUserDetails details = currentUser();
		if (details == null) return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		if (permissionService.isGlobalAdmin(details)
				|| permissionService.hasGlobalRole(details, UserRole.ENGINEER)
				|| permissionService.hasGlobalRole(details, UserRole.INSTRUCTOR)) {
			return new ResponseEntity<>(exRepo.findAll(), HttpStatus.OK);
		}
		List<RangeExercise> ranges = exRepo.findAllById(permissionService.getAccessibleExerciseIds(details.getUsername()));
		return new ResponseEntity<>(ranges, HttpStatus.OK);
	}

	@GetMapping("/exercise/{name}")
	ResponseEntity<RangeExercise> getExercise(@PathVariable(value = "name", required = true) String name) {
		ArcticUserDetails details = currentUser();
		if (details == null) return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		RangeExercise range = exRepo.findByName(name.replaceAll(" ", "_")).orElseThrow(() -> new ResourceNotFoundException("Exercise Not Found With Name: " + name));
		if (!permissionService.isGlobalAdmin(details)
				&& !permissionService.hasGlobalRole(details, UserRole.ENGINEER)
				&& !permissionService.hasGlobalRole(details, UserRole.INSTRUCTOR)
				&& !permissionService.hasPermission(details.getUsername(), range.getId(), ExerciseRole.RANGE_VIEW))
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		return new ResponseEntity<>(range, HttpStatus.OK);
	}

	@DeleteMapping(value = "/exercise/{name}", produces = "application/json")
	ResponseEntity<?> deleteExercise(@PathVariable String name) {
		ArcticUserDetails details = currentUser();
		if (details == null) return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
		RangeExercise range = exRepo.findByName(name.replaceAll(" ", "_")).orElseThrow(() -> new ResourceNotFoundException("Exercise Not Found With Name: " + name));
		if (!permissionService.isGlobalAdmin(details)
				&& !permissionService.hasGlobalRole(details, UserRole.ENGINEER)
				&& !permissionService.hasPermission(details.getUsername(), range.getId(), ExerciseRole.RANGE_ADMIN))
			return new ResponseEntity<>("Forbidden", HttpStatus.FORBIDDEN);
		exRepo.delete(range);
		ansibleStager.cleanup(range.getName());
		return new ResponseEntity<>("", HttpStatus.OK);
	}

	@GetMapping("/exercise/{name}/graph")
	ResponseEntity<?> getGraph(@PathVariable(value = "name", required = true) String name) {
		ArcticUserDetails details = currentUser();
		if (details == null) return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
		RangeExercise range = exRepo.findByName(name.replaceAll(" ", "_")).orElseThrow(() -> new ResourceNotFoundException("Exercise Not Found With Name: " + name));
		if (!permissionService.isGlobalAdmin(details)
				&& !permissionService.hasGlobalRole(details, UserRole.ENGINEER)
				&& !permissionService.hasGlobalRole(details, UserRole.INSTRUCTOR)
				&& !permissionService.hasPermission(details.getUsername(), range.getId(), ExerciseRole.RANGE_VIEW))
			return new ResponseEntity<>("Forbidden", HttpStatus.FORBIDDEN);
		RangeGraphDTO dto = new RangeGraphDTO();
		dto.setLinks(range.getGraphLinks());
		dto.setNodes(range.getGraphNodes());
		return new ResponseEntity<>(dto, HttpStatus.OK);
	}

	@PostMapping(value = "/exercise/{name}/graph", consumes = "application/json")
	ResponseEntity<?> setGraph(@PathVariable(value = "name", required = true) String name, @RequestBody RangeGraphDTO dto) {
		ArcticUserDetails details = currentUser();
		if (details == null) return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
		RangeExercise range = exRepo.findByName(name.replaceAll(" ", "_")).orElseThrow(() -> new ResourceNotFoundException("Exercise Not Found With Name: " + name));
		if (!permissionService.isGlobalAdmin(details)
				&& !permissionService.hasGlobalRole(details, UserRole.ENGINEER)
				&& !permissionService.hasPermission(details.getUsername(), range.getId(), ExerciseRole.RANGE_ADMIN))
			return new ResponseEntity<>("Forbidden", HttpStatus.FORBIDDEN);
		range.setGraphLinks(dto.getLinks());
		range.setGraphNodes(dto.getNodes());
		exRepo.save(range);

		GraphUpdateMessage broadcast = new GraphUpdateMessage();
		broadcast.setNodes(range.getGraphNodes());
		broadcast.setLinks(range.getGraphLinks());
		broadcast.setSenderName(details.getUsername());
		broadcast.setExerciseName(range.getName());
		messagingTemplate.convertAndSend("/topic/graph/" + range.getName(), broadcast);

		return new ResponseEntity<>("", HttpStatus.OK);
	}

	@PostMapping(value = "/exercise", produces = "application/json", consumes = "application/json")
	ResponseEntity<RangeExercise> addExercise(@RequestBody RangeDTO dto) throws ResourceAlreadyExistsException {
		ArcticUserDetails details = currentUser();
		if (details == null) return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		if (!permissionService.isGlobalAdmin(details) && !permissionService.hasGlobalRole(details, UserRole.ENGINEER))
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		RangeExercise test = exRepo.findByName(dto.getRangeName().replaceAll(" ", "_")).orElse(null);
		if (test != null) throw new ResourceAlreadyExistsException("Range with name: " + dto.getRangeName() + " already exists!");

		RangeExercise range = new RangeExercise();
		range.setName(dto.getRangeName().replaceAll(" ", "_"));
		range.setDescription(dto.getRangeDescription());
		range.setProviderName(dto.getProviderName());

		exRepo.save(range);
		return new ResponseEntity<>(range, HttpStatus.CREATED);
	}

	private ArcticUserDetails currentUser() {
		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		return (principal instanceof ArcticUserDetails) ? (ArcticUserDetails) principal : null;
	}

}
