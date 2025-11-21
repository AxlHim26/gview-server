package com.idserver.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConnectionRequestMessage {

	@NotBlank(message = "Target peer ID is required")
	private String targetPeerId;

	@NotBlank(message = "Source peer ID is required")
	private String sourcePeerId;

}

