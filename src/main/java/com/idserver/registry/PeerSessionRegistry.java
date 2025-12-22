package com.idserver.registry;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PeerSessionRegistry {

	private final Map<String, String> sessionPeers = new ConcurrentHashMap<>();

	public void register(String peerId, String sessionId) {
		sessionPeers.put(sessionId, peerId);
	}

	public void removeBySessionId(String sessionId) {
		sessionPeers.remove(sessionId);
	}

	/**
	 * Get peer ID by session ID
	 */
	public String getPeerId(String sessionId) {
		return sessionPeers.get(sessionId);
	}
}
