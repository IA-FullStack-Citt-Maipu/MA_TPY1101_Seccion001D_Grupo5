package com.panol_project.backendpanol.bootstrap.config;

import com.sun.management.OperatingSystemMXBean;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalLong;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RuntimeResourceMetricsConfig {

    private static final Path CGROUP_V2_MEMORY_MAX = Path.of("/sys/fs/cgroup/memory.max");
    private static final Path CGROUP_V1_MEMORY_LIMIT = Path.of("/sys/fs/cgroup/memory/memory.limit_in_bytes");
    private static final Path PROC_SELF_STATUS = Path.of("/proc/self/status");
    private static final long UNLIMITED_CGROUP_THRESHOLD_BYTES = 1L << 60;
    private static final long KIBIBYTE = 1024L;

    @Bean
    MeterBinder runtimeResourceMetricsBinder() {
        return registry -> {
            Gauge.builder(
                            "panol_runtime_container_memory_limit_bytes",
                            this,
                            RuntimeResourceMetricsConfig::resolveContainerMemoryLimitBytes
                    )
                    .description("Memory limit available to the backend container")
                    .baseUnit("bytes")
                    .register(registry);

            Gauge.builder(
                            "panol_runtime_process_resident_memory_bytes",
                            this,
                            RuntimeResourceMetricsConfig::resolveProcessResidentMemoryBytes
                    )
                    .description("Resident memory used by the backend process")
                    .baseUnit("bytes")
                    .register(registry);
        };
    }

    private double resolveContainerMemoryLimitBytes() {
        OptionalLong cgroupLimit = readCgroupMemoryLimit(CGROUP_V2_MEMORY_MAX);
        if (cgroupLimit.isEmpty()) {
            cgroupLimit = readCgroupMemoryLimit(CGROUP_V1_MEMORY_LIMIT);
        }
        if (cgroupLimit.isPresent()) {
            return cgroupLimit.getAsLong();
        }

        OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        if (osBean == null) {
            return Double.NaN;
        }

        long totalMemoryBytes = osBean.getTotalMemorySize();
        return totalMemoryBytes > 0 ? totalMemoryBytes : Double.NaN;
    }

    private double resolveProcessResidentMemoryBytes() {
        if (!Files.exists(PROC_SELF_STATUS)) {
            return Double.NaN;
        }

        try {
            List<String> lines = Files.readAllLines(PROC_SELF_STATUS, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (!line.startsWith("VmRSS:")) {
                    continue;
                }

                String[] parts = line.trim().split("\\s+");
                if (parts.length < 3) {
                    return Double.NaN;
                }

                long kibibytes = Long.parseLong(parts[1]);
                return kibibytes * KIBIBYTE;
            }
        } catch (IOException | NumberFormatException ignored) {
            return Double.NaN;
        }

        return Double.NaN;
    }

    private OptionalLong readCgroupMemoryLimit(Path path) {
        if (!Files.exists(path)) {
            return OptionalLong.empty();
        }

        try {
            String rawValue = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (rawValue.isBlank() || "max".equalsIgnoreCase(rawValue)) {
                return OptionalLong.empty();
            }

            long parsedValue = Long.parseLong(rawValue);
            if (parsedValue <= 0 || parsedValue >= UNLIMITED_CGROUP_THRESHOLD_BYTES) {
                return OptionalLong.empty();
            }

            return OptionalLong.of(parsedValue);
        } catch (IOException | NumberFormatException ignored) {
            return OptionalLong.empty();
        }
    }
}
