package com.idserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.DefaultContentTypeResolver;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
	private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

	@Override
	public void configureMessageBroker(MessageBrokerRegistry config) {
		// Enable a simple in-memory message broker for /queue destinations
		// CRITICAL: Set heartbeat to 10 seconds for fast disconnect detection
		// CRITICAL: TaskScheduler is REQUIRED when using setHeartbeatValue()
		// Create TaskScheduler locally - AVOID circular dependency
		TaskScheduler taskScheduler = createTaskScheduler();
		
		config.enableSimpleBroker("/queue", "/topic", "/topic/relay")
				.setHeartbeatValue(new long[]{10000, 10000}) // 10 second heartbeat
				.setTaskScheduler(taskScheduler); // Provide TaskScheduler for heartbeat
		// Prefix for messages FROM clients TO server
		config.setApplicationDestinationPrefixes("/app");
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		// Register WebSocket endpoint at /ws with SockJS
		// CRITICAL: Set heartbeat and disconnect delay for fast detection
		registry.addEndpoint("/ws")
				.setAllowedOriginPatterns("*")
				.withSockJS()
				.setHeartbeatTime(20000) // 20 second SockJS heartbeat
				.setDisconnectDelay(3000); // Detect disconnect after 3 seconds
		
		// Also support native WebSocket (without SockJS)
		registry.addEndpoint("/ws")
				.setAllowedOriginPatterns("*");
	}

	/**
	 * CRITICAL FIX: Configure message converters
	 * Add String converter FIRST (for JSON strings), then Jackson converter
	 */
	@Override
	public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
		// Add String converter FIRST (for JSON strings from client)
		messageConverters.add(new StringMessageConverter(StandardCharsets.UTF_8));
		
		// Then add Jackson converter (for automatic deserialization of RelayMessage objects)
		messageConverters.add(mappingJackson2MessageConverter());
		
		return false; // Keep other default converters
	}

	/**
	 * Bean for MappingJackson2MessageConverter with proper ObjectMapper configuration
	 */
	@Bean
	public MappingJackson2MessageConverter mappingJackson2MessageConverter() {
		MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.registerModule(new JavaTimeModule());
		converter.setObjectMapper(objectMapper);
		
		DefaultContentTypeResolver resolver = new DefaultContentTypeResolver();
		resolver.setDefaultMimeType(MimeTypeUtils.APPLICATION_JSON);
		converter.setContentTypeResolver(resolver);
		
		return converter;
	}

	@Override
	public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
		// CRITICAL: Add timeouts for faster disconnect detection
		registry.setSendTimeLimit(15 * 1000) // 15 seconds send timeout
				.setSendBufferSizeLimit(512 * 1024)
				.setMessageSizeLimit(512 * 1024) // Increase for relay data (512KB)
				.setTimeToFirstMessage(30 * 1000); // 30 seconds for first message
		log.info("WebSocketTransport configured: messageSizeLimit=512KB, sendBufferSizeLimit=512KB, sendTimeLimit=15s");
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		// Increase thread pool for handling incoming messages
		registration.taskExecutor().corePoolSize(10);
	}

	@Override
	public void configureClientOutboundChannel(ChannelRegistration registration) {
		// Increase thread pool for handling outgoing messages
		registration.taskExecutor().corePoolSize(10);
	}

	@Bean
	public StompSubProtocolErrorHandler stompErrorHandler() {
		return new StompSubProtocolErrorHandler() {
			@Override
			public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
				String errorMsg = ex.getMessage();
				String sessionId = clientMessage != null ? 
					(String) clientMessage.getHeaders().get("simpSessionId") : "unknown";
				
				// Detect common slow client / buffer issues
				if (errorMsg != null) {
					if (errorMsg.contains("sendTimeLimit") || errorMsg.contains("timeout")) {
						log.warn("STOMP sendTimeLimit exceeded for session {} - client may be too slow or network congested", 
							sessionId);
					} else if (errorMsg.contains("buffer") || errorMsg.contains("size limit")) {
						log.warn("STOMP buffer limit exceeded for session {} - message too large or client too slow", 
							sessionId);
					} else {
						log.error("STOMP client message processing error (session={}): {}", sessionId, errorMsg, ex);
					}
				} else {
					log.error("STOMP client message processing error (session={}): {}", sessionId, ex.getClass().getSimpleName(), ex);
				}
				return super.handleClientMessageProcessingError(clientMessage, ex);
			}

			@Override
			public Message<byte[]> handleErrorMessageToClient(Message<byte[]> errorMessage) {
				// Log error details but don't spam - only log if it's a significant issue
				Object errorType = errorMessage.getHeaders().get("errorType");
				if (errorType != null && !errorType.toString().contains("heartbeat")) {
					log.warn("STOMP error message to client: type={}, payload={}",
						errorType,
						new String(errorMessage.getPayload()).substring(0, 
							Math.min(200, errorMessage.getPayload().length))); // Limit log size
				}
				return super.handleErrorMessageToClient(errorMessage);
			}
		};
	}

	/**
	 * CRITICAL: Configure underlying Servlet WebSocket container buffer sizes
	 * This prevents Tomcat/Jetty from closing connections when handling large STOMP text frames
	 * (e.g., base64-encoded SCREEN payloads ~40-80 KB)
	 * 
	 * Without this, even if STOMP messageSizeLimit is 512KB, the Servlet container
	 * may reject frames at a lower level, causing ConnectionLostException on clients.
	 * 
	 * Note: This bean is only active in a servlet container context (e.g., Tomcat).
	 * In test contexts without a servlet container, Spring Boot will skip this bean gracefully.
	 */
	@Bean
	public ServletServerContainerFactoryBean createWebSocketContainer() {
		ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
		container.setMaxTextMessageBufferSize(512 * 1024);   // 512 KB text buffer
		container.setMaxBinaryMessageBufferSize(512 * 1024);  // 512 KB binary buffer
		container.setMaxSessionIdleTimeout(60_000L);          // 60s idle timeout
		container.setAsyncSendTimeout(20_000L);               // 20s async send timeout (WAN-friendly)
		log.info("ServletServerContainerFactoryBean configured: maxTextMessageBufferSize=512KB, maxBinaryMessageBufferSize=512KB, asyncSendTimeout=20s, idleTimeout=60s");
		return container;
	}

	/**
	 * Log WebSocket configuration at startup to verify both STOMP and Servlet container limits
	 */
	@PostConstruct
	public void logWebSocketConfig() {
		log.info("=== WebSocket Configuration Summary ===");
		log.info("STOMP Transport: messageSizeLimit=512KB, sendBufferSizeLimit=512KB, sendTimeLimit=15s");
		log.info("Servlet Container: maxTextMessageBufferSize=512KB, maxBinaryMessageBufferSize=512KB, asyncSendTimeout=20s, idleTimeout=60s");
		log.info("Heartbeat: STOMP=10s, SockJS=20s");
		log.info("Thread pools: inbound=10, outbound=10");
		log.info("Configured for: SCREEN frames up to ~80KB base64, WAN latency tolerance");
		log.info("========================================");
	}

	/**
	 * Create TaskScheduler locally for WebSocket heartbeat management
	 * CRITICAL: SimpleBroker requires TaskScheduler to send/receive heartbeat frames
	 * DO NOT create as @Bean to avoid circular dependency
	 * 
	 * @return ThreadPoolTaskScheduler configured for heartbeat tasks
	 */
	private TaskScheduler createTaskScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(4); // 4 threads for better heartbeat management
		scheduler.setThreadNamePrefix("ws-heartbeat-");
		scheduler.setAwaitTerminationSeconds(60); // Wait up to 60 seconds for tasks to complete
		scheduler.setWaitForTasksToCompleteOnShutdown(true); // Graceful shutdown
		scheduler.initialize();
		return scheduler;
	}

}

