package com.idserver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LookupResponse {

	private String ipAddress;
	private Integer port;
	private Boolean online;

}

