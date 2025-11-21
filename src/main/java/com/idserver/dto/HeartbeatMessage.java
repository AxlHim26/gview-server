package com.idserver.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class HeartbeatMessage {

	@NotBlank(message = "Peer ID is required")
	private String peerId;

}

