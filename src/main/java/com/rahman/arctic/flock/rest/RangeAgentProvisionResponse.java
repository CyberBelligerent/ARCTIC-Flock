package com.rahman.arctic.flock.rest;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RangeAgentProvisionResponse {

	private String agentId;
	private String token;
	private String exerciseId;
	private String hostId;

}
