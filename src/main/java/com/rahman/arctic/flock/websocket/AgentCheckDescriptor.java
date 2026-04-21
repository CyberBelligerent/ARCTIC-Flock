package com.rahman.arctic.flock.websocket;

import lombok.Data;

@Data
public class AgentCheckDescriptor {

	private String checkId;
	private String kind;
	private String target;
	private long intervalMs;

}
