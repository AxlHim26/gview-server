package com.idserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelayMessage {

	@NotBlank(message = "Source peer ID is required")
	private String sourcePeerId;

	@NotBlank(message = "Target peer ID is required")
	private String targetPeerId;

	@NotBlank(message = "Data type is required")
	private String dataType;

	@NotNull(message = "Payload is required")
	private String base64Data;

	private long timestamp;
}

