package com.rahman.arctic.flock.rest;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AgentStatusResponse {

	private String agentId;
	private String hostId;
	private String hostname;
	private String exerciseId;
	private Long lastHeartbeat;
	private Long heartbeatIntervalMs;
	private boolean online;
	private List<AgentStatusCheckEntry> checks;

}
