package com.rahman.arctic.flock.rest;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rahman.arctic.flock.websocket.AgentCheckDescriptor;
import com.rahman.arctic.flock.websocket.AgentCheckResult;
import com.rahman.arctic.flock.websocket.AgentStateService;
import com.rahman.arctic.flock.websocket.AgentStateService.AgentLiveState;
import com.rahman.arctic.iceberg.objects.RangeAgent;
import com.rahman.arctic.iceberg.objects.RangeExercise;
import com.rahman.arctic.iceberg.objects.computers.ArcticHost;
import com.rahman.arctic.iceberg.repos.ArcticHostRepo;
import com.rahman.arctic.iceberg.repos.ExerciseRepo;
import com.rahman.arctic.iceberg.services.RangeAgentLookupService;
import com.rahman.arctic.orca.objects.role.ExerciseRole;
import com.rahman.arctic.orca.objects.role.UserRole;
import com.rahman.arctic.orca.utils.ArcticUserDetails;
import com.rahman.arctic.orca.utils.ExercisePermissionService;

@RestController
@RequestMapping("/range-api/v1")
public class RangeAgentRestController {

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();
	private static final int TOKEN_BYTES = 32;

	private final RangeAgentLookupService agentService;
	private final ExerciseRepo exerciseRepo;
	private final ArcticHostRepo hostRepo;
	private final BCryptPasswordEncoder passwordEncoder;
	private final ExercisePermissionService permissionService;
	private final AgentStateService stateService;

	public RangeAgentRestController(RangeAgentLookupService agentService,
			ExerciseRepo exerciseRepo,
			ArcticHostRepo hostRepo,
			BCryptPasswordEncoder passwordEncoder,
			ExercisePermissionService permissionService,
			AgentStateService stateService) {
		this.agentService = agentService;
		this.exerciseRepo = exerciseRepo;
		this.hostRepo = hostRepo;
		this.passwordEncoder = passwordEncoder;
		this.permissionService = permissionService;
		this.stateService = stateService;
	}

	@PostMapping("/exercise/{exerciseId}/host/{hostId}/agent")
	ResponseEntity<?> provisionAgent(@PathVariable String exerciseId, @PathVariable String hostId) {
		ArcticUserDetails user = currentUser();
		if (user == null) return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);

		RangeExercise exercise = exerciseRepo.findById(exerciseId)
				.orElseThrow(() -> new ResourceNotFoundException("Exercise not found: " + exerciseId));

		if (!canAdminExercise(user, exercise.getId()))
			return new ResponseEntity<>("Forbidden", HttpStatus.FORBIDDEN);

		ArcticHost host = hostRepo.findById(hostId)
				.orElseThrow(() -> new ResourceNotFoundException("Host not found: " + hostId));

		boolean hostBelongsToExercise = exercise.getHosts().stream().anyMatch(h -> h.getId().equals(host.getId()));
		if (!hostBelongsToExercise)
			return new ResponseEntity<>("Host does not belong to this exercise", HttpStatus.BAD_REQUEST);

		String token = generateToken();
		String tokenHash = passwordEncoder.encode(token);
		RangeAgent agent = agentService.create(exercise.getId(), host.getId(), host.getName(), tokenHash);

		return new ResponseEntity<>(
				new RangeAgentProvisionResponse(agent.getId(), token, exercise.getId(), host.getId()),
				HttpStatus.CREATED);
	}

	@GetMapping("/exercise/{exerciseId}/agents")
	ResponseEntity<List<RangeAgent>> listAgentsForExercise(@PathVariable String exerciseId) {
		ArcticUserDetails user = currentUser();
		if (user == null) return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);

		Optional<RangeExercise> exercise = exerciseRepo.findById(exerciseId);
		if (exercise.isEmpty()) return new ResponseEntity<>(HttpStatus.NOT_FOUND);

		if (!canViewExercise(user, exerciseId)) return new ResponseEntity<>(HttpStatus.FORBIDDEN);

		List<RangeAgent> agents = agentService.findByExerciseId(exerciseId);
		agents.forEach(a -> a.setTokenHash(null));
		return new ResponseEntity<>(agents, HttpStatus.OK);
	}

	@GetMapping("/exercise/{exerciseId}/agent-status")
	ResponseEntity<List<AgentStatusResponse>> agentStatus(@PathVariable String exerciseId) {
		ArcticUserDetails user = currentUser();
		if (user == null) return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		if (exerciseRepo.findById(exerciseId).isEmpty()) return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		if (!canViewExercise(user, exerciseId)) return new ResponseEntity<>(HttpStatus.FORBIDDEN);

		List<AgentStatusResponse> out = new ArrayList<>();
		for (RangeAgent agent : agentService.findByExerciseId(exerciseId)) {
			AgentLiveState s = stateService.get(agent.getId());
			List<AgentStatusCheckEntry> entries = Collections.emptyList();
			if (s != null) {
				entries = new ArrayList<>(s.getRegistered().size());
				for (AgentCheckDescriptor d : s.getRegistered().values()) {
					AgentCheckResult last = s.getResultsByCheckId().get(d.getCheckId());
					entries.add(new AgentStatusCheckEntry(
							d.getCheckId(), d.getKind(), d.getTarget(), d.getIntervalMs(), last));
				}
			}
			out.add(new AgentStatusResponse(
					agent.getId(),
					agent.getHostId(),
					agent.getHostname(),
					agent.getExerciseId(),
					s != null && s.getLastHeartbeat() != null ? s.getLastHeartbeat().toEpochMilli() : null,
					s != null && s.getHeartbeatIntervalMs() > 0 ? s.getHeartbeatIntervalMs() : null,
					stateService.isOnline(agent.getId()),
					entries));
		}
		return new ResponseEntity<>(out, HttpStatus.OK);
	}

	@DeleteMapping("/agent/{agentId}")
	ResponseEntity<?> revokeAgent(@PathVariable String agentId) {
		ArcticUserDetails user = currentUser();
		if (user == null) return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);

		RangeAgent agent = agentService.findById(agentId)
				.orElseThrow(() -> new ResourceNotFoundException("Agent not found: " + agentId));

		if (!canAdminExercise(user, agent.getExerciseId()))
			return new ResponseEntity<>("Forbidden", HttpStatus.FORBIDDEN);

		agentService.delete(agentId);
		return new ResponseEntity<>("", HttpStatus.OK);
	}

	private boolean canAdminExercise(ArcticUserDetails user, String exerciseId) {
		return permissionService.isGlobalAdmin(user)
				|| permissionService.hasGlobalRole(user, UserRole.ENGINEER)
				|| permissionService.hasPermission(user.getUsername(), exerciseId, ExerciseRole.RANGE_ADMIN);
	}

	private boolean canViewExercise(ArcticUserDetails user, String exerciseId) {
		return permissionService.isGlobalAdmin(user)
				|| permissionService.hasGlobalRole(user, UserRole.ENGINEER)
				|| permissionService.hasGlobalRole(user, UserRole.INSTRUCTOR)
				|| permissionService.hasPermission(user.getUsername(), exerciseId, ExerciseRole.RANGE_VIEW);
	}

	private static String generateToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		SECURE_RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private ArcticUserDetails currentUser() {
		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		return (principal instanceof ArcticUserDetails) ? (ArcticUserDetails) principal : null;
	}

}
