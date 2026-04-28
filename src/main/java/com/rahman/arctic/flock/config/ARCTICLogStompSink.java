package com.rahman.arctic.flock.config;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.rahman.arctic.shard.util.ARCTICLog;

import jakarta.annotation.PostConstruct;

@Component
public class ARCTICLogStompSink {

	private final SimpMessagingTemplate messagingTemplate;

	public ARCTICLogStompSink(SimpMessagingTemplate messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}

	@PostConstruct
	void register() {
		ARCTICLog.setSink((topic, line) -> messagingTemplate.convertAndSend(topic, line));
	}
}
