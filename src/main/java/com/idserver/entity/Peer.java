package com.idserver.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "peers", uniqueConstraints = {
	@UniqueConstraint(columnNames = "peer_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Peer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "peer_id", unique = true, nullable = false, length = 11)
	private String peerId;

	@Column(nullable = false)
	private String password;

	@Column(name = "ip_address")
	private String ipAddress;

	@Column(name = "port")
	private Integer port;

	@Column(name = "last_seen", nullable = false)
	private LocalDateTime lastSeen;

	@Column(name = "online", nullable = false)
	@Builder.Default
	private Boolean online = false;

	@Column(name = "session_id")
	private String sessionId;

	@Column(name = "created_at", nullable = false, updatable = false)
	@Builder.Default
	private LocalDateTime createdAt = LocalDateTime.now();

	@PrePersist
	protected void onCreate() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
		if (lastSeen == null) {
			lastSeen = LocalDateTime.now();
		}
		if (online == null) {
			online = false;
		}
	}

}

