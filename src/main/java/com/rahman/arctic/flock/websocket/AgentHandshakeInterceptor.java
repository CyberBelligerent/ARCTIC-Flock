package com.rahman.arctic.flock.websocket;

import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.rahman.arctic.iceberg.objects.RangeAgent;
import com.rahman.arctic.iceberg.services.RangeAgentLookupService;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class AgentHandshakeInterceptor implements HandshakeInterceptor {

	public static final String AGENT_PRINCIPAL_ATTR = "ARCTIC_AGENT";

	private final RangeAgentLookupService agentService;
	private final BCryptPasswordEncoder passwordEncoder;

	public AgentHandshakeInterceptor(RangeAgentLookupService agentService, BCryptPasswordEncoder passwordEncoder) {
		this.agentService = agentService;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
			WebSocketHandler wsHandler, Map<String, Object> attributes) {

		if (!(request instanceof ServletServerHttpRequest servletRequest)) return false;
		HttpServletRequest http = servletRequest.getServletRequest();

		String agentId = http.getHeader("X-Agent-Id");
		String token = http.getHeader("X-Agent-Token");
		if (agentId == null || token == null) return false;

		RangeAgent agent = agentService.findById(agentId).orElse(null);
		if (agent == null) return false;
		if (!passwordEncoder.matches(token, agent.getTokenHash())) return false;

		agentService.touchLastSeen(agent.getId());
		attributes.put(AGENT_PRINCIPAL_ATTR,
				new AgentPrincipal(agent.getId(), agent.getExerciseId(), agent.getHostId()));
		return true;
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
			WebSocketHandler wsHandler, Exception exception) {
	}

}
