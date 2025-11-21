package com.idserver.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LookupRequest {

	@NotBlank(message = "Peer ID is required")
	private String peerId;

	@NotBlank(message = "Password is required")
	private String password;

}

