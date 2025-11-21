package com.idserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;

@Data
public class ConnectRequest {

	@NotBlank(message = "Peer ID is required")
	private String peerId;

	@NotBlank(message = "Password is required")
	private String password;

	@NotBlank(message = "IP address is required")
	private String ipAddress;

	@NotNull(message = "Port is required")
	@Min(value = 1, message = "Port must be between 1 and 65535")
	@Max(value = 65535, message = "Port must be between 1 and 65535")
	private Integer port;

}

