package com.idserver.controller;

import com.idserver.dto.RelayMessage;
import com.idserver.handler.WebSocketEventHandler;
import com.idserver.registry.PeerSessionRegistry;
import com.idserver.service.PeerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Base64;

@Controller
@RequiredArgsConstructor
@Slf4j
public class RelayController {

	private static final int MAX_DATA_LENGTH = 512 * 1024;

	private final PeerService peerService;
	private final PeerSessionRegistry peerSessionRegistry;
	private final SimpMessagingTemplate messagingTemplate;
	private final WebSocketEventHandler webSocketEventHandler;

	/**
	 * Handle relay data from peer
	 * Client sends to: /app/relay.data
	 * CRITICAL FIX: Accept RelayMessage object directly (not String)
	 * MappingJackson2MessageConverter will auto-deserialize from JSON
	 */
	@MessageMapping("/relay.data")
	public void relayData(@Payload RelayMessage message, SimpMessageHeaderAccessor headers) {
		if (message == null) {
			log.error("Received null relay message");
			return;
		}

		String sessionId = headers.getSessionId();
		
		// Update activity for source session
		if (sessionId != null) {
			webSocketEventHandler.updateActivity(sessionId);
		}

		String sourcePeerId = message.getSourcePeerId();
		String targetPeerId = message.getTargetPeerId();

		if (sourcePeerId == null || targetPeerId == null) {
			log.warn("Relay message missing source or target: {}", message);
			return;
		}

		int base64Len = message.getBase64Data() != null ? message.getBase64Data().length() : -1;
		log.info("RelayController: received {} message from {} to {}, base64Len={}", 
				message.getDataType(), sourcePeerId, targetPeerId, base64Len);

		// Validate payload size
		if (message.getBase64Data() != null && message.getBase64Data().length() > MAX_DATA_LENGTH * 2) {
			log.warn("Relay message from {} exceeded max payload size", sourcePeerId);
			sendError(sourcePeerId, "Payload too large");
			return;
		}

		try {
			// CRITICAL: Check if target peer is online BEFORE relaying (fast-fail)
			if (!peerService.isPeerOnline(targetPeerId)) {
				log.warn("Relay target peer offline: {}", targetPeerId);
				sendError(sourcePeerId, "Target peer " + targetPeerId + " is offline");
				return;
			}

			// Double-check session exists
			if (peerSessionRegistry.getSessionId(targetPeerId).isEmpty()) {
				log.warn("Relay target peer offline (no session): {}", targetPeerId);
				sendError(sourcePeerId, "Target peer is offline");
				return;
			}

			// Validate base64 encoding
			long payloadBytes = 0;
			if (message.getBase64Data() != null) {
				try {
					payloadBytes = Base64.getDecoder().decode(message.getBase64Data()).length;
				} catch (IllegalArgumentException e) {
					log.warn("Invalid base64 payload from peer {}: {}", sourcePeerId, e.getMessage());
					sendError(sourcePeerId, "Invalid payload encoding");
					return;
				}
			}

			// Forward to target peer via relay topic
			messagingTemplate.convertAndSend("/topic/relay/" + targetPeerId, message);
			
			if ("SCREEN".equalsIgnoreCase(message.getDataType())) {
				log.info("RelayController: SCREEN payload from {} to {}: base64Len={}, decodedBytes={}",
					sourcePeerId, targetPeerId, base64Len, payloadBytes);
			}
			log.info("RelayController: forwarding {} ({} bytes decoded) to /topic/relay/{}", 
					message.getDataType(), payloadBytes, targetPeerId);

		} catch (Exception e) {
			log.error("Error relaying message: {}", e.getMessage(), e);
			
			// Send error back to sender
			try {
				sendError(sourcePeerId, "Relay failed: " + e.getMessage());
			} catch (Exception e2) {
				log.error("Failed to send error message back to sender", e2);
			}
		}
	}

	private void sendError(String sourcePeerId, String errorMessage) {
		// Try to send error to source peer's session
		peerSessionRegistry.getSessionId(sourcePeerId).ifPresent(sessionId -> {
			try {
				messagingTemplate.convertAndSendToUser(
					sessionId,
					"/queue/relay-error",
					errorMessage
				);
				log.debug("Sent error to source peer {}: {}", sourcePeerId, errorMessage);
			} catch (Exception e) {
				log.error("Failed to send error to peer {}: {}", sourcePeerId, e.getMessage());
			}
		});
	}
}

