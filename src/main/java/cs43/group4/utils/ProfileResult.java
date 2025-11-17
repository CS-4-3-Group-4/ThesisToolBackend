package cs43.group4.utils;

public class ProfileResult {
    private final long memoryUsedBytes;
    private final long executionTimeNs;

    public ProfileResult(long memoryUsedBytes, long executionTimeNs) {
        this.memoryUsedBytes = memoryUsedBytes;
        this.executionTimeNs = executionTimeNs;
    }

    // Memory getters
    public long getMemoryUsedBytes() {
        return memoryUsedBytes;
    }

    public double getMemoryUsedKB() {
        return memoryUsedBytes / 1024.0;
    }

    public double getMemoryUsedMB() {
        return memoryUsedBytes / (1024.0 * 1024.0);
    }

    // Time getters
    public long getExecutionTimeNs() {
        return executionTimeNs;
    }

    public double getExecutionTimeMs() {
        return executionTimeNs / 1_000_000.0;
    }

    public double getExecutionTimeS() {
        return executionTimeNs / 1_000_000_000.0;
    }

    @Override
    public String toString() {
        return String.format(
                "Memory Used: %.2f MB (%.2f KB, %d bytes)\n" + "Execution Time: %.3f s (%.3f ms, %d ns)",
                getMemoryUsedMB(),
                getMemoryUsedKB(),
                memoryUsedBytes,
                getExecutionTimeS(),
                getExecutionTimeMs(),
                executionTimeNs);
    }
}
