package com.idserver.service;

import com.idserver.entity.Peer;
import com.idserver.repository.PeerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class PeerService {

	private final PeerRepository peerRepository;
	private final Random random = new Random();
	private final ConcurrentHashMap<String, String> sessionCache = new ConcurrentHashMap<>();

	/**
	 * Generate unique 9-digit peer ID in format XXX-XXX-XXX
	 */
	@Transactional
	public String generatePeerId() {
		String peerId;
		int attempts = 0;
		do {
			int part1 = random.nextInt(900) + 100; // 100-999
			int part2 = random.nextInt(900) + 100;
			int part3 = random.nextInt(900) + 100;
			peerId = String.format("%03d-%03d-%03d", part1, part2, part3);
			attempts++;
			if (attempts > 100) {
				throw new IllegalStateException("Failed to generate unique peer ID after 100 attempts");
			}
		} while (peerRepository.existsByPeerId(peerId));
		
		log.debug("Generated new peer ID: {}", peerId);
		return peerId;
	}

	/**
	 * Register a new peer with password
	 */
	@Transactional
	public String registerPeer(String password) {
		String peerId = generatePeerId();
		
		Peer peer = Peer.builder()
				.peerId(peerId)
				.password(password)
				.online(false)
				.lastSeen(LocalDateTime.now())
				.createdAt(LocalDateTime.now())
				.build();
		
		peerRepository.save(peer);
		log.info("Registered new peer: {}", peerId);
		return peerId;
	}

	/**
	 * Update peer connection information
	 */
	@Transactional
	public void updatePeerInfo(String peerId, String ipAddress, Integer port, String sessionId) {
		Optional<Peer> peerOpt = peerRepository.findByPeerId(peerId);
		if (peerOpt.isEmpty()) {
			throw new IllegalArgumentException("Peer not found: " + peerId);
		}

		Peer peer = peerOpt.get();
		peer.setIpAddress(ipAddress);
		peer.setPort(port);
		peer.setSessionId(sessionId);
		peer.setOnline(true);
		peer.setLastSeen(LocalDateTime.now());
		
		peerRepository.save(peer);
		sessionCache.put(peerId, sessionId);
		log.info("Updated peer info for {}: {}:{}", peerId, ipAddress, port);
	}

	/**
	 * Authenticate peer credentials
	 */
	public boolean authenticate(String peerId, String password) {
		Optional<Peer> peerOpt = peerRepository.findByPeerId(peerId);
		if (peerOpt.isEmpty()) {
			log.warn("Authentication failed: Peer not found - {}", peerId);
			return false;
		}

		Peer peer = peerOpt.get();
		boolean authenticated = peer.getPassword().equals(password);
		if (!authenticated) {
			log.warn("Authentication failed: Invalid password for peer - {}", peerId);
		} else {
			log.debug("Authentication successful for peer: {}", peerId);
		}
		return authenticated;
	}

	/**
	 * Lookup peer connection details
	 */
	public Optional<LookupResult> lookupPeer(String peerId) {
		Optional<Peer> peerOpt = peerRepository.findByPeerId(peerId);
		if (peerOpt.isEmpty()) {
			log.debug("Lookup failed: Peer not found - {}", peerId);
			return Optional.empty();
		}

		Peer peer = peerOpt.get();
		LookupResult result = new LookupResult(
				peer.getIpAddress(),
				peer.getPort(),
				peer.getOnline()
		);
		
		log.debug("Lookup successful for peer {}: online={}", peerId, peer.getOnline());
		return Optional.of(result);
	}

	/**
	 * Mark peer as offline when WebSocket disconnects
	 */
	@Transactional
	public void markOffline(String sessionId) {
		Optional<Peer> peerOpt = peerRepository.findBySessionId(sessionId);
		if (peerOpt.isEmpty()) {
			log.warn("Could not mark peer offline: Session not found - {}", sessionId);
			return;
		}

		Peer peer = peerOpt.get();
		peer.setOnline(false);
		peer.setSessionId(null);
		peerRepository.save(peer);
		
		sessionCache.remove(peer.getPeerId());
		log.info("Marked peer offline: {}", peer.getPeerId());
	}

	/**
	 * Update last seen timestamp (heartbeat)
	 */
	@Transactional
	public void heartbeat(String peerId) {
		Optional<Peer> peerOpt = peerRepository.findByPeerId(peerId);
		if (peerOpt.isEmpty()) {
			log.warn("Heartbeat failed: Peer not found - {}", peerId);
			return;
		}

		Peer peer = peerOpt.get();
		peer.setLastSeen(LocalDateTime.now());
		peerRepository.save(peer);
		log.debug("Heartbeat received from peer: {}", peerId);
	}

	/**
	 * Get WebSocket session ID for a peer
	 */
	public Optional<String> getSessionId(String peerId) {
		String sessionId = sessionCache.get(peerId);
		if (sessionId != null) {
			return Optional.of(sessionId);
		}

		Optional<Peer> peerOpt = peerRepository.findByPeerId(peerId);
		if (peerOpt.isPresent() && peerOpt.get().getSessionId() != null) {
			String sid = peerOpt.get().getSessionId();
			sessionCache.put(peerId, sid);
			return Optional.of(sid);
		}

		return Optional.empty();
	}

	/**
	 * Check if peer is online
	 */
	public boolean isPeerOnline(String peerId) {
		Optional<Peer> peerOpt = peerRepository.findByPeerId(peerId);
		if (peerOpt.isEmpty()) {
			return false;
		}
		Peer peer = peerOpt.get();
		return peer.getOnline() != null && peer.getOnline();
	}

	/**
	 * Get peer ID by session ID
	 */
	public String getPeerIdBySession(String sessionId) {
		Optional<Peer> peerOpt = peerRepository.findBySessionId(sessionId);
		return peerOpt.map(Peer::getPeerId).orElse(null);
	}

	/**
	 * Inner class for lookup result
	 */
	public static class LookupResult {
		private final String ipAddress;
		private final Integer port;
		private final Boolean online;

		public LookupResult(String ipAddress, Integer port, Boolean online) {
			this.ipAddress = ipAddress;
			this.port = port;
			this.online = online;
		}

		public String getIpAddress() {
			return ipAddress;
		}

		public Integer getPort() {
			return port;
		}

		public Boolean getOnline() {
			return online;
		}
	}

}

