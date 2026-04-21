package com.rahman.arctic.flock.rest;

import com.rahman.arctic.flock.websocket.AgentCheckResult;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AgentStatusCheckEntry {

	private String checkId;
	private String kind;
	private String target;
	private long intervalMs;
	private AgentCheckResult lastResult;

}
