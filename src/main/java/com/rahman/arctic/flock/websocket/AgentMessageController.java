package com.rahman.arctic.flock.websocket;

import java.security.Principal;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import com.rahman.arctic.iceberg.services.RangeAgentLookupService;

@Controller
public class AgentMessageController {

	private final SimpMessagingTemplate messagingTemplate;
	private final RangeAgentLookupService agentService;
	private final AgentStateService stateService;

	public AgentMessageController(SimpMessagingTemplate messagingTemplate,
			RangeAgentLookupService agentService,
			AgentStateService stateService) {
		this.messagingTemplate = messagingTemplate;
		this.agentService = agentService;
		this.stateService = stateService;
	}

	@MessageMapping("/agent/results")
	void onResult(@Payload AgentCheckResult result, Principal principal) {
		AgentPrincipal agent = asAgent(principal);
		if (agent == null) return;
		agentService.touchLastSeen(agent.getAgentId());
		stateService.recordResult(agent.getAgentId(), result);
		messagingTemplate.convertAndSend("/topic/agents/" + agent.getAgentId() + "/results", result);
	}

	@MessageMapping("/agent/heartbeat")
	void onHeartbeat(@Payload AgentHeartbeat beat, Principal principal) {
		AgentPrincipal agent = asAgent(principal);
		if (agent == null) return;
		agentService.touchLastSeen(agent.getAgentId());
		stateService.recordHeartbeat(agent.getAgentId(), beat.getIntervalMs());
		messagingTemplate.convertAndSend("/topic/agents/" + agent.getAgentId() + "/heartbeat", beat);
	}

	@MessageMapping("/agent/register")
	void onRegister(@Payload AgentRegistration reg, Principal principal) {
		AgentPrincipal agent = asAgent(principal);
		if (agent == null) return;
		agentService.touchLastSeen(agent.getAgentId());
		stateService.recordRegistration(agent.getAgentId(), reg);
		messagingTemplate.convertAndSend("/topic/agents/" + agent.getAgentId() + "/register", reg);
	}

	private AgentPrincipal asAgent(Principal principal) {
		if (principal instanceof UsernamePasswordAuthenticationToken token
				&& token.getPrincipal() instanceof AgentPrincipal agent) {
			return agent;
		}
		return null;
	}

}
