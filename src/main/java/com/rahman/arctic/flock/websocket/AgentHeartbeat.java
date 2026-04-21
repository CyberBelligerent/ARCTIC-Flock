package com.rahman.arctic.flock.websocket;

import lombok.Data;

@Data
public class AgentHeartbeat {

	private long timestamp;
	private String version;
	private long intervalMs;

}
