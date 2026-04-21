package com.rahman.arctic.flock.websocket;

import java.security.Principal;

public class AgentPrincipal implements Principal {

	private final String agentId;
	private final String exerciseId;
	private final String hostId;

	public AgentPrincipal(String agentId, String exerciseId, String hostId) {
		this.agentId = agentId;
		this.exerciseId = exerciseId;
		this.hostId = hostId;
	}

	@Override
	public String getName() {
		return agentId;
	}

	public String getAgentId() {
		return agentId;
	}

	public String getExerciseId() {
		return exerciseId;
	}

	public String getHostId() {
		return hostId;
	}

}
