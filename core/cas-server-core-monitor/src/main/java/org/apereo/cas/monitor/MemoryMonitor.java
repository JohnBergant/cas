package org.apereo.cas.monitor;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;

/**
 * Monitors JVM memory usage.
 *
 * @author Marvin S. Addison
 * @since 3.5.0
 */
public class MemoryMonitor extends AbstractHealthIndicator {

    private static final int PERCENTAGE_VALUE = 100;

    /**
     * Percent free memory below which a warning is reported.
     */
    private final long freeMemoryWarnThreshold;

    public MemoryMonitor(final long threshold) {
        if (threshold < 0) {
            throw new IllegalArgumentException("Warning threshold must be non-negative.");
        }
        this.freeMemoryWarnThreshold = threshold;
    }

    @Override
    protected void doHealthCheck(final Health.Builder builder) throws Exception {
        final long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        final long total = Runtime.getRuntime().maxMemory();
        final long free = total - used;
        if (free * PERCENTAGE_VALUE / total < this.freeMemoryWarnThreshold) {
            buildHealthCheckStatus(builder.down(), free, total);
        } else {
            buildHealthCheckStatus(builder.up(), free, total);
        }
    }

    private void buildHealthCheckStatus(final Health.Builder builder,
                                        final long freeMemory, final long totalMemory) {
        builder
            .withDetail("freeMemory", freeMemory)
            .withDetail("totalMemory", totalMemory);
    }
}
