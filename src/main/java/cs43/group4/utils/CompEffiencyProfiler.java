package cs43.group4.utils;

public class CompEffiencyProfiler {

    /**
     * Profiles memory usage and execution time of a given Runnable.
     * This method performs garbage collection before and after execution
     * to get more accurate memory measurements.
     *
     * @param task The Runnable to profile
     * @return ProfileResult containing memory and time metrics
     */
    public ProfileResult profile(Runnable task) {
        Runtime runtime = Runtime.getRuntime();

        // Perform multiple garbage collections to stabilize memory
        System.gc();
        System.gc();

        try {
            Thread.sleep(100); // Give GC time to complete
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Measure initial memory
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        // Measure execution time
        long startTime = System.nanoTime();

        // Execute the task
        task.run();

        long endTime = System.nanoTime();

        // Measure final memory
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();

        // Calculate metrics
        long memoryUsed = memoryAfter - memoryBefore;
        long executionTimeNs = endTime - startTime;

        return new ProfileResult(memoryUsed, executionTimeNs);
    }

    /**
     * Profiles memory usage and execution time with warm-up iterations.
     * This helps stabilize JVM optimizations and provides more consistent results.
     *
     * @param task The Runnable to profile
     * @param warmupIterations Number of warm-up runs before actual profiling
     * @return ProfileResult containing memory and time metrics
     */
    public ProfileResult profileWithWarmup(Runnable task, int warmupIterations) {
        // Warm-up phase
        for (int i = 0; i < warmupIterations; i++) {
            task.run();
        }

        // Perform actual profiling
        return profile(task);
    }

    /**
     * Profiles with multiple iterations and returns average results.
     * This provides more statistically reliable measurements.
     *
     * @param task The Runnable to profile
     * @param iterations Number of profiling iterations
     * @return ProfileResult containing average memory and time metrics
     */
    public ProfileResult profileAverage(Runnable task, int iterations) {
        long totalMemory = 0;
        long totalTimeNs = 0;

        for (int i = 0; i < iterations; i++) {
            ProfileResult result = profile(task);
            totalMemory += result.getMemoryUsedBytes();
            totalTimeNs += result.getExecutionTimeNs();
        }

        return new ProfileResult(
            totalMemory / iterations,
            totalTimeNs / iterations
        );
    }
}
