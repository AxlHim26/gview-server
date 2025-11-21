package com.idserver.repository;

import com.idserver.entity.Peer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PeerRepository extends JpaRepository<Peer, Long> {

	Optional<Peer> findByPeerId(String peerId);

	Optional<Peer> findBySessionId(String sessionId);

	@Modifying
	@Query("UPDATE Peer p SET p.lastSeen = :lastSeen WHERE p.peerId = :peerId")
	int updateLastSeen(@Param("peerId") String peerId, @Param("lastSeen") LocalDateTime lastSeen);

	@Modifying
	@Query("UPDATE Peer p SET p.online = false, p.sessionId = null WHERE p.sessionId = :sessionId")
	int markOfflineBySessionId(@Param("sessionId") String sessionId);

	boolean existsByPeerId(String peerId);

}

