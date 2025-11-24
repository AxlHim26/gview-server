package com.idserver.metrics;

import com.idserver.registry.PeerSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Aggregates relay throughput stats so we can diagnose server-side bottlenecks without
 * logging every SCREEN frame. Provides periodic summaries of relay activity.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RelayMetricsTracker {

	private final PeerSessionRegistry peerSessionRegistry;

	private final LongAdder screenFrameCount = new LongAdder();
	private final LongAdder screenBase64Bytes = new LongAdder();
	private final LongAdder screenDecodedBytes = new LongAdder();
	private final AtomicLong lastLogTime = new AtomicLong(System.currentTimeMillis());

	/**
	 * Record a SCREEN frame relayed through the server.
	 * @param base64Length Length of base64-encoded payload in characters
	 * @param decodedBytes Length of decoded JPEG bytes
	 */
	public void recordScreenFrame(int base64Length, long decodedBytes) {
		screenFrameCount.increment();
		if (base64Length > 0) {
			screenBase64Bytes.add(base64Length);
		}
		if (decodedBytes > 0) {
			screenDecodedBytes.add(decodedBytes);
		}
	}

	/**
	 * Log aggregated relay statistics every 5 seconds.
	 * Only logs when there is actual activity to avoid noise.
	 */
	@Scheduled(fixedRate = 5000)
	public void logScreenStats() {
		long frames = screenFrameCount.sumThenReset();
		long base64Bytes = screenBase64Bytes.sumThenReset();
		long decodedBytes = screenDecodedBytes.sumThenReset();
		long now = System.currentTimeMillis();
		long windowMs = now - lastLogTime.getAndSet(now);

		// Only log if there was activity
		if (frames == 0 || windowMs <= 0) {
			return;
		}

		double fps = frames * 1000.0 / windowMs;
		double avgBase64Kb = (base64Bytes / (double) frames) / 1024.0;
		double avgDecodedKb = (decodedBytes / (double) frames) / 1024.0;
		double estMbps = decodedBytes * 8.0 / windowMs / 1000.0;
		
		// Get active session count for context
		int activeSessions = peerSessionRegistry.getActiveSessionCount();

		log.info("Relay server stats: fps≈{}, avgDecodedKB≈{}, avgBase64KB≈{}, estMbps≈{}, frames={}, activeSessions={}, window={}ms",
			String.format("%.1f", fps),
			String.format("%.1f", avgDecodedKb),
			String.format("%.1f", avgBase64Kb),
			String.format("%.2f", estMbps),
			frames,
			activeSessions,
			windowMs);
	}
}

