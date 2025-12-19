package com.idserver.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idserver.dto.*;
import com.idserver.entity.Peer;
import com.idserver.handler.WebSocketEventHandler;
import com.idserver.registry.PeerSessionRegistry;
import com.idserver.repository.PeerRepository;
import com.idserver.service.PeerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Optional;

@Controller
@RequiredArgsConstructor
@Slf4j
@ConditionalOnBean(SimpMessagingTemplate.class)
public class MessageController {

	private final PeerService peerService;
	private final SimpMessagingTemplate messagingTemplate;
	private final PeerRepository peerRepository;
	private final PeerSessionRegistry peerSessionRegistry;
	private final WebSocketEventHandler webSocketEventHandler;
	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * Handle peer connection via WebSocket
	 * Client sends to: /app/peer.connect
	 * CRITICAL FIX: Accept String payload and parse JSON manually
	 */
	@MessageMapping("/peer.connect")
	public void handlePeerConnect(@Payload String jsonPayload, StompHeaderAccessor headerAccessor) {
		String sessionId = headerAccessor.getSessionId();
		log.info("Peer connect via WebSocket, payload: {}", jsonPayload);

		// Update activity for session
		if (sessionId != null) {
			webSocketEventHandler.updateActivity(sessionId);
		}

		try {
			// Parse JSON manually
			ConnectRequest request = objectMapper.readValue(jsonPayload, ConnectRequest.class);
			
			log.info("Parsed request: peerId={}, ip={}, port={}", 
					request.getPeerId(), request.getIpAddress(), request.getPort());

			// Authenticate
			if (!peerService.authenticate(request.getPeerId(), request.getPassword())) {
				log.warn("WebSocket connect failed: Invalid credentials for peer: {}", request.getPeerId());
				return;
			}

			// Update peer info with actual WebSocket session ID
			peerService.updatePeerInfo(
					request.getPeerId(),
					request.getIpAddress(),
					request.getPort(),
					sessionId
			);
			peerSessionRegistry.register(request.getPeerId(), sessionId);
			log.info("Peer {} connected successfully via WebSocket", request.getPeerId());
		} catch (Exception e) {
			log.error("Error processing peer.connect: {}", e.getMessage(), e);
		}
	}

	/**
	 * Handle heartbeat from peer
	 * Client sends to: /app/peer.heartbeat
	 * CRITICAL FIX: Accept String payload and parse JSON manually
	 */
	@MessageMapping("/peer.heartbeat")
	public void handleHeartbeat(@Payload String jsonPayload, StompHeaderAccessor headerAccessor) {
		String sessionId = headerAccessor.getSessionId();
		
		// Update activity for session
		if (sessionId != null) {
			webSocketEventHandler.updateActivity(sessionId);
		}
		
		try {
			HeartbeatMessage message = objectMapper.readValue(jsonPayload, HeartbeatMessage.class);
			log.debug("Heartbeat received from peer: {}", message.getPeerId());
			peerService.heartbeat(message.getPeerId());
		} catch (Exception e) {
			log.error("Error processing heartbeat: {}", e.getMessage(), e);
		}
	}

	@EventListener
	public void handleSessionDisconnect(SessionDisconnectEvent event) {
		StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
		String sessionId = headerAccessor.getSessionId();
		if (sessionId != null) {
			peerSessionRegistry.removeBySessionId(sessionId);
			log.info("Session disconnected, removed mapping for session {}", sessionId);
		}
	}

	/**
	 * Handle connection request to another peer
	 * Client sends to: /app/peer.request
	 * Server forwards to: /queue/connection-request/{targetPeerId}
	 */
	@MessageMapping("/peer.request")
	public void handleConnectionRequest(@Payload @Valid ConnectionRequestMessage message) {
		log.info("Connection request from {} to {}", message.getSourcePeerId(), message.getTargetPeerId());

		// Verify source peer exists and is authenticated
		Optional<Peer> sourcePeerOpt = peerRepository.findByPeerId(message.getSourcePeerId());
		if (sourcePeerOpt.isEmpty() || !sourcePeerOpt.get().getOnline()) {
			log.warn("Connection request failed: Source peer not found or offline - {}", message.getSourcePeerId());
			return;
		}

		// Verify target peer exists and is online
		Optional<PeerService.LookupResult> targetPeerOpt = peerService.lookupPeer(message.getTargetPeerId());
		if (targetPeerOpt.isEmpty()) {
			log.warn("Connection request failed: Target peer not found - {}", message.getTargetPeerId());
			return;
		}

		PeerService.LookupResult targetPeer = targetPeerOpt.get();
		if (!targetPeer.getOnline()) {
			log.warn("Connection request failed: Target peer is offline - {}", message.getTargetPeerId());
			return;
		}

		// Get source peer details
		Peer sourcePeer = sourcePeerOpt.get();

		// Create connection request notification
		ConnectionRequestNotification notification = new ConnectionRequestNotification(
				message.getSourcePeerId(),
				sourcePeer.getIpAddress(),
				sourcePeer.getPort()
		);

		// Send notification to target peer via queue
		// Client should subscribe to /queue/connection-request
		messagingTemplate.convertAndSend("/queue/connection-request", notification);
		log.info("Connection request sent to target peer: {} (queue: /queue/connection-request)", message.getTargetPeerId());
	}

	/**
	 * DTO for connection request notification
	 */
	public static class ConnectionRequestNotification {
		private String sourcePeerId;
		private String ipAddress;
		private Integer port;

		public ConnectionRequestNotification() {
		}

		public ConnectionRequestNotification(String sourcePeerId, String ipAddress, Integer port) {
			this.sourcePeerId = sourcePeerId;
			this.ipAddress = ipAddress;
			this.port = port;
		}

		public String getSourcePeerId() {
			return sourcePeerId;
		}

		public void setSourcePeerId(String sourcePeerId) {
			this.sourcePeerId = sourcePeerId;
		}

		public String getIpAddress() {
			return ipAddress;
		}

		public void setIpAddress(String ipAddress) {
			this.ipAddress = ipAddress;
		}

		public Integer getPort() {
			return port;
		}

		public void setPort(Integer port) {
			this.port = port;
		}
	}

}
