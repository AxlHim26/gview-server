package com.idserver.repository;

import com.idserver.entity.Peer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PeerRepository extends JpaRepository<Peer, Long> {

	Optional<Peer> findByPeerId(String peerId);

	Optional<Peer> findBySessionId(String sessionId);

	boolean existsByPeerId(String peerId);

}
