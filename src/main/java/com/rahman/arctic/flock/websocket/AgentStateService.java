package com.rahman.arctic.flock.websocket;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class AgentStateService {

	private static final Duration DEFAULT_STALENESS = Duration.ofMinutes(5);

	private final Map<String, AgentLiveState> states = new ConcurrentHashMap<>();

	public void recordHeartbeat(String agentId, long intervalMs) {
		AgentLiveState s = state(agentId);
		s.lastHeartbeat = Instant.now();
		if (intervalMs > 0) s.heartbeatIntervalMs = intervalMs;
	}

	public void recordResult(String agentId, AgentCheckResult result) {
		AgentLiveState s = state(agentId);
		s.lastHeartbeat = Instant.now();
		s.checks.put(result.getCheckId(), result);
	}

	public void recordRegistration(String agentId, AgentRegistration reg) {
		AgentLiveState s = state(agentId);
		if (reg.getHeartbeatIntervalMs() > 0) s.heartbeatIntervalMs = reg.getHeartbeatIntervalMs();
		s.registered.clear();
		if (reg.getChecks() != null) {
			for (AgentCheckDescriptor d : reg.getChecks()) {
				s.registered.put(d.getCheckId(), d);
			}
			s.checks.keySet().retainAll(s.registered.keySet());
		}
	}

	public AgentLiveState get(String agentId) {
		return states.get(agentId);
	}

	public boolean isOnline(String agentId) {
		AgentLiveState s = states.get(agentId);
		if (s == null || s.lastHeartbeat == null) return false;
		Duration threshold = s.heartbeatIntervalMs > 0
				? Duration.ofMillis(s.heartbeatIntervalMs * 3)
				: DEFAULT_STALENESS;
		return Duration.between(s.lastHeartbeat, Instant.now()).compareTo(threshold) < 0;
	}

	private AgentLiveState state(String agentId) {
		return states.computeIfAbsent(agentId, id -> new AgentLiveState());
	}

	public static class AgentLiveState {
		private volatile Instant lastHeartbeat;
		private volatile long heartbeatIntervalMs;
		private final Map<String, AgentCheckResult> checks = new ConcurrentHashMap<>();
		private final Map<String, AgentCheckDescriptor> registered = new ConcurrentHashMap<>();

		public Instant getLastHeartbeat() { return lastHeartbeat; }
		public long getHeartbeatIntervalMs() { return heartbeatIntervalMs; }
		public Collection<AgentCheckResult> getChecks() {
			return Collections.unmodifiableCollection(checks.values());
		}
		public Map<String, AgentCheckDescriptor> getRegistered() {
			return Collections.unmodifiableMap(registered);
		}
		public Map<String, AgentCheckResult> getResultsByCheckId() {
			return Collections.unmodifiableMap(checks);
		}
	}

}
