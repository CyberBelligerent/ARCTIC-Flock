package com.rahman.arctic.flock.websocket;

import lombok.Data;

@Data
public class AgentCheckResult {

	private String checkId;
	private String kind;
	private String target;
	private boolean ok;
	private long latencyMs;
	private long timestamp;
	private String detail;

}
