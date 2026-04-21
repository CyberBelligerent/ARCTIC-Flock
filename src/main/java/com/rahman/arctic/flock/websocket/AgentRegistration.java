package com.rahman.arctic.flock.websocket;

import java.util.List;

import lombok.Data;

@Data
public class AgentRegistration {

	private long heartbeatIntervalMs;
	private String version;
	private List<AgentCheckDescriptor> checks;

}
