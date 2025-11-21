package com.idserver.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {

	@NotBlank(message = "Password is required")
	private String password;

}

