package com.idserver.handler;

import com.idserver.registry.PeerSessionRegistry;
import com.idserver.service.PeerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventHandler {

	private final PeerService peerService;
	private final PeerSessionRegistry peerSessionRegistry;
	
	// Track last activity time for each session
	private final Map<String, Long> sessionLastActivity = new ConcurrentHashMap<>();

	@EventListener
	public void handleWebSocketConnectListener(SessionConnectedEvent event) {
		StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
		String sessionId = headerAccessor.getSessionId();
		log.info("WebSocket session connected: {}", sessionId);
		
		// Track activity
		sessionLastActivity.put(sessionId, System.currentTimeMillis());
		// Note: Peer info will be updated when /app/peer.connect message is received
	}

	@EventListener
	public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
		StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
		String sessionId = headerAccessor.getSessionId();
		log.info("WebSocket session disconnected: {}", sessionId);
		
		// Clean up activity tracking
		sessionLastActivity.remove(sessionId);
		
		// Mark peer offline IMMEDIATELY
		String peerId = peerSessionRegistry.getPeerId(sessionId);
		if (peerId != null) {
			peerService.markOffline(sessionId);
			peerSessionRegistry.removeBySessionId(sessionId);
			log.info("Marked peer offline immediately: {}", peerId);
		} else {
			log.warn("No peer found for disconnected session: {}", sessionId);
		}
	}

	/**
	 * Periodic check for stale sessions (sessions that haven't sent heartbeat)
	 * Runs every 30 seconds
	 */
	@Scheduled(fixedRate = 30000) // Every 30 seconds
	public void checkStaleSessions() {
		long now = System.currentTimeMillis();
		long timeout = 60000; // 60 seconds timeout (3 missed heartbeats at 10s interval)
		
		sessionLastActivity.entrySet().removeIf(entry -> {
			String sessionId = entry.getKey();
			long lastActivity = entry.getValue();
			
			if (now - lastActivity > timeout) {
				log.warn("Session {} timed out (last activity: {}ms ago), marking offline", 
					sessionId, now - lastActivity);
				
				String peerId = peerSessionRegistry.getPeerId(sessionId);
				if (peerId != null) {
					peerService.markOffline(sessionId);
					peerSessionRegistry.removeBySessionId(sessionId);
					log.info("Marked stale peer offline: {}", peerId);
				}
				return true; // Remove from map
			}
			return false; // Keep in map
		});
	}

	/**
	 * Validate active WebSocket sessions
	 * Runs every 30 seconds to check session health
	 */
	@Scheduled(fixedRate = 30000) // Every 30 seconds
	public void validateActiveSessions() {
		log.debug("Validating active WebSocket sessions: {} sessions tracked", 
			sessionLastActivity.size());
		// Additional validation logic can be added here if needed
	}

	/**
	 * Update activity timestamp for a session
	 * Called when receiving any message from the session
	 */
	public void updateActivity(String sessionId) {
		if (sessionId != null) {
			sessionLastActivity.put(sessionId, System.currentTimeMillis());
		}
	}
}

