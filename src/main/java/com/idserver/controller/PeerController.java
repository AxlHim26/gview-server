package com.idserver.controller;

import com.idserver.dto.*;
import com.idserver.service.PeerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/peer")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class PeerController {

	private final PeerService peerService;

	/**
	 * Register a new peer
	 * POST /api/peer/register
	 */
	@PostMapping("/register")
	public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
		log.info("Registration request received");
		try {
			String peerId = peerService.registerPeer(request.getPassword());
			RegisterResponse response = new RegisterResponse(peerId);
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			log.error("Registration failed", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	/**
	 * Connect peer (update connection info)
	 * POST /api/peer/connect
	 */
	@PostMapping("/connect")
	public ResponseEntity<ConnectResponse> connect(@Valid @RequestBody ConnectRequest request) {
		log.info("Connect request received for peer: {}", request.getPeerId());
		
		// Authenticate
		if (!peerService.authenticate(request.getPeerId(), request.getPassword())) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new ConnectResponse(false, "Invalid credentials"));
		}

		try {
			// Note: sessionId will be set when WebSocket connects
			// For REST-only connection, we use a placeholder
			peerService.updatePeerInfo(
					request.getPeerId(),
					request.getIpAddress(),
					request.getPort(),
					"rest-" + System.currentTimeMillis()
			);
			
			return ResponseEntity.ok(new ConnectResponse(true, "Connected successfully"));
		} catch (IllegalArgumentException e) {
			log.error("Connect failed: {}", e.getMessage());
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(new ConnectResponse(false, e.getMessage()));
		} catch (Exception e) {
			log.error("Connect failed", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(new ConnectResponse(false, "Internal server error"));
		}
	}

	/**
	 * Lookup peer connection details
	 * GET /api/peer/lookup/{peerId}?password=xxx
	 */
	@GetMapping("/lookup/{peerId}")
	public ResponseEntity<LookupResponse> lookup(
			@PathVariable String peerId,
			@RequestParam String password) {
		log.info("Lookup request received for peer: {}", peerId);

		// Authenticate
		if (!peerService.authenticate(peerId, password)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		Optional<PeerService.LookupResult> resultOpt = peerService.lookupPeer(peerId);
		if (resultOpt.isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}

		PeerService.LookupResult result = resultOpt.get();
		LookupResponse response = new LookupResponse(
				result.getIpAddress(),
				result.getPort(),
				result.getOnline()
		);

		return ResponseEntity.ok(response);
	}

}

