package com.idserver.registry;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PeerSessionRegistry {

	private final Map<String, String> peerSessions = new ConcurrentHashMap<>();
	private final Map<String, String> sessionPeers = new ConcurrentHashMap<>();

	public void register(String peerId, String sessionId) {
		peerSessions.put(peerId, sessionId);
		sessionPeers.put(sessionId, peerId);
	}

	public Optional<String> getSessionId(String peerId) {
		return Optional.ofNullable(peerSessions.get(peerId));
	}

	public void removeByPeerId(String peerId) {
		Optional.ofNullable(peerSessions.remove(peerId))
				.ifPresent(sessionId -> sessionPeers.remove(sessionId));
	}

	public void removeBySessionId(String sessionId) {
		Optional.ofNullable(sessionPeers.remove(sessionId))
				.ifPresent(peerSessions::remove);
	}

	/**
	 * Get peer ID by session ID
	 */
	public String getPeerId(String sessionId) {
		return sessionPeers.get(sessionId);
	}

	/**
	 * Get count of active WebSocket sessions
	 */
	public int getActiveSessionCount() {
		return sessionPeers.size();
	}
}

