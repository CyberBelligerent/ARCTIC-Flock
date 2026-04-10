package com.rahman.arctic.flock.websocket;

import java.security.Principal;

import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.rahman.arctic.iceberg.objects.RangeExercise;
import com.rahman.arctic.iceberg.repos.ExerciseRepo;
import com.rahman.arctic.orca.objects.role.ExerciseRole;
import com.rahman.arctic.orca.utils.ArcticUserDetails;
import com.rahman.arctic.orca.utils.ExercisePermissionService;

@Controller
public class GraphWebSocketController {

	private final ExerciseRepo exRepo;
	private final ExercisePermissionService permissionService;
	private final SimpMessagingTemplate messagingTemplate;

	public GraphWebSocketController(ExerciseRepo exRepo, ExercisePermissionService permissionService,
			SimpMessagingTemplate messagingTemplate) {
		this.exRepo = exRepo;
		this.permissionService = permissionService;
		this.messagingTemplate = messagingTemplate;
	}

	@MessageMapping("/graph/{name}")
	void handleGraphUpdate(@DestinationVariable String name,
			@Payload GraphUpdateMessage message,
			Principal principal) {

		if (principal == null) return;

		ArcticUserDetails user;
		try {
			user = (ArcticUserDetails) ((org.springframework.security.authentication.UsernamePasswordAuthenticationToken) principal).getPrincipal();
		} catch (ClassCastException e) {
			return;
		}

		RangeExercise range = exRepo.findByName(name.replaceAll(" ", "_")).orElse(null);
		if (range == null) return;

		boolean allowed = permissionService.isGlobalAdmin(user)
				|| permissionService.hasPermission(user.getUsername(), range.getId(), ExerciseRole.RANGE_ADMIN);
		if (!allowed) return;

		range.setGraphNodes(message.getNodes());
		range.setGraphLinks(message.getLinks());
		exRepo.save(range);

		message.setSenderName(user.getUsername());
		message.setExerciseName(range.getName());
		messagingTemplate.convertAndSend("/topic/graph/" + range.getName(), message);
	}

}
