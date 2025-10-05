package cs43.group4.core;

/**
 * Extended Firefly Algorithm (EFA).
 *
 * Additions over the baseline FA (Yang, 2008):
 * - Objective filtering: discard infeasible candidates.
 * - Diversity control: Hamming distance based reinitialization.
 * - Dynamic self-adaptation: inertia (w_t) and step-size (c).
 *
 * Authors: Rendel Abainza, Wendel de Dios, Lester Osana, John Paul Viado (PUP Manila, 2025)
 */
import cs43.group4.core.extended.DomainConstraintEvaluator;
import java.util.Arrays;

/** Core optimizer implementing the Extended Firefly Algorithm. */
public class ExtendedFireflyAlgorithm {

    // Main algorithm parameters
    private double gamma; // Light absorption coefficient
    private double alpha; // Random step scale (decays from alpha0 to alphaFinal)
    private double alpha0; // Initial alpha
    private double alphaFinal; // Final alpha
    private double beta0; // Attractiveness at r = 0
    private double betaMin; // Minimum attractiveness (floor)

    // Self-adaptation (defaults are safe to start with)
    private double inertiaW1 = 0.9; // w_t starts here (more exploration)
    private double inertiaW2 = 0.4; // w_t ends here (more exploitation)
    private double inertiaB = 1.0; // how fast w_t decays (log schedule)

    // Step-size factor base (0..1), adjusted by dimension
    private double theta = 0.9;

    // Per-iteration values
    private double currentInertia = 1.0; // w_t
    private double currentStepFactor = 0.0; // c

    // Per-iteration diagnostics
    private double iterStepSum = 0.0;
    private int iterStepCount = 0;
    private double iterBetaSum = 0.0;
    private int iterBetaCount = 0;
    private int iterBetaFlooredCount = 0;
    private int iterMovesToward = 0;
    private int iterRandomWalks = 0;

    // Metrics exposed to the runner each generation
    private double lastAvgStep = 0.0;
    private double lastAvgBeta = 0.0;
    private double lastFlooredBetaRate = 0.0; // share of beta at floor
    private int lastMovesToward = 0;
    private int lastRandomWalks = 0;

    private int numFireflies; // Population size
    private int dimensions; // Number of variables
    private int generations; // Number of iterations

    // Hamming distance diversity control
    private double diversityConstant; // c in TH = c * L
    private int stringLength; // L = dimensions * bitsPerDimension
    private int bitsPerDimension; // bits to encode each dimension

    // Firefly states
    private double[][] fireflies; // Positions of fireflies
    private double[] brightness; // Objective values
    private double[] bestSolution;
    private double bestValue;

    private ObjectiveFunction function;
    private DataLoader.Data data; // For objective filtering

    private double[] lowerBound;
    private double[] upperBound;

    // Optional per-iteration progress callback
    public interface ProgressListener {
        void onIteration(int generation, double[] bestSolution, int reinitializedCount);
    }

    private ProgressListener progressListener;

    // Optional per-firefly step callback (called often)
    public interface StepListener {
        void onStep(double[] bestSolution);
    }

    private StepListener stepListener;

    /** Create an EFA instance. */
    public ExtendedFireflyAlgorithm(
            ObjectiveFunction function,
            DataLoader.Data data, // For Objective Function Filtering
            int numFireflies,
            double[] lowerBound,
            double[] upperBound,
            double gamma,
            double beta0,
            double betaMin,
            double alpha0,
            double alphaFinal,
            int generations) {
        // Validate bounds length
        if (lowerBound.length != upperBound.length) {
            throw new IllegalArgumentException("Lower bound array length (" + lowerBound.length
                    + ") must equal upper bound array length (" + upperBound.length + ")");
        }

        this.function = function;
        this.data = data;
        this.numFireflies = numFireflies;
        this.dimensions = lowerBound.length;
        this.gamma = gamma;
        this.beta0 = beta0;
        this.betaMin = betaMin;
        this.alpha0 = alpha0;
        this.alphaFinal = alphaFinal;
        this.generations = generations;
        this.diversityConstant = 5;
        this.bitsPerDimension = 8;
        this.stringLength = dimensions * bitsPerDimension;

        this.lowerBound = Arrays.copyOf(lowerBound, lowerBound.length);
        this.upperBound = Arrays.copyOf(upperBound, upperBound.length);

        this.fireflies = new double[numFireflies][dimensions];
        this.brightness = new double[numFireflies];
        this.bestSolution = new double[dimensions];
        this.bestValue = Double.MAX_VALUE;

        initializePopulation(lowerBound, upperBound);
    }

    /** Initialize population uniformly within bounds and evaluate. */
    private void initializePopulation(double[] lowerBound, double[] upperBound) {
        for (int i = 0; i < numFireflies; i++) {
            for (int d = 0; d < dimensions; d++) {
                fireflies[i][d] = lowerBound[d] + Math.random() * (upperBound[d] - lowerBound[d]);
                fireflies[i][d] = clamp(fireflies[i][d], d);
            }
            brightness[i] = function.evaluate(fireflies[i]);
            updateBest(fireflies[i], brightness[i]);
        }
        alpha = alpha0;
    }

    /** Run optimization for the configured number of generations. */
    public void optimize() {

        for (int gen = 0; gen < generations; gen++) {
            // 1) Self-adaptive schedules (1-based iteration index)
            currentInertia = computeSelfAdaptiveInertiaWeight(gen + 1, generations, inertiaW1, inertiaW2, inertiaB);
            currentStepFactor = computeDynamicStepFactor(gen + 1, generations, theta, dimensions);

            // 2) Reset diagnostics
            iterStepSum = 0.0;
            iterStepCount = 0;
            iterBetaSum = 0.0;
            iterBetaCount = 0;
            iterBetaFlooredCount = 0;
            iterMovesToward = 0;
            iterRandomWalks = 0;

            for (int i = 0; i < numFireflies; i++) {
                // 3) Update firefly i versus all j (move toward brighter, else random walk)
                for (int j = 0; j < numFireflies; j++) {
                    if (brightness[i] > brightness[j]) { // move i toward brighter j
                        moveFirefly(i, j);
                    } else {
                        randomWalk(i);
                    }
                }

                // 4) Objective filtering (minimization contract): infeasible -> +INF
                boolean feasible =
                        DomainConstraintEvaluator.isFeasible(fireflies[i], this.data, dimensions / data.C, data.C);
                brightness[i] = feasible ? function.evaluate(fireflies[i]) : Double.POSITIVE_INFINITY;

                updateBest(fireflies[i], brightness[i]);

                // Optional callback after each i
                if (stepListener != null) {
                    stepListener.onStep(Arrays.copyOf(bestSolution, dimensions));
                }
            }

            // 5) Diversity control (Hamming-based reinit) and best random walk
            int reinitializedCount = applyDiversityControl(gen);
            randomWalkBest();

            // 6) Decay alpha (legacy randomness schedule; noise uses c)
            alpha = alphaFinal + (alpha0 - alphaFinal) * Math.exp(-0.1 * gen);

            // 7) Finalize diagnostics
            lastAvgStep = (iterStepCount > 0) ? (iterStepSum / (double) iterStepCount) : 0.0;
            lastAvgBeta = (iterBetaCount > 0) ? (iterBetaSum / (double) iterBetaCount) : 0.0;
            lastFlooredBetaRate = (iterBetaCount > 0) ? ((double) iterBetaFlooredCount / (double) iterBetaCount) : 0.0;
            lastMovesToward = iterMovesToward;
            lastRandomWalks = iterRandomWalks;

            // 8) Progress callback per generation
            if (progressListener != null) {
                progressListener.onIteration(gen + 1, Arrays.copyOf(bestSolution, dimensions), reinitializedCount);
            }
        }
    }

    /** Diversity control using Hamming distance. Returns reinit count. */
    private int applyDiversityControl(int generation) {
        double c = (diversityConstant * 0.05) * Math.exp(-0.001 * generation);
        double threshold = c * stringLength; // TH = c × L
        int reinitializedCount = 0;
        boolean[] reinitialized = new boolean[numFireflies];

        for (int i = 0; i < numFireflies; i++) {
            if (reinitialized[i]) continue;

            for (int j = i + 1; j < numFireflies; j++) {
                if (reinitialized[j]) continue;

                double hammingDistance = calculateHammingDistance(fireflies[i], fireflies[j]);

                if (hammingDistance < threshold) {
                    // Reinitialize one of the two fireflies
                    int toReinitialize = (Math.random() < 0.5) ? j : i;

                    if (!reinitialized[toReinitialize]) {
                        reinitializeFirefly(toReinitialize);
                        reinitialized[toReinitialize] = true;
                        reinitializedCount++;
                    }
                }
            }
        }

        return reinitializedCount;
    }

    /**
     * Calculate Hamming distance between two fireflies' solutions without using strings.
     * Uses direct bitwise operations for memory efficiency.
     * This replaces our method that takes two solution arrays.
     */
    private double calculateHammingDistance(double[] solution1, double[] solution2) {
        int hammingDistance = 0;

        // Process each dimension of both solutions
        for (int d = 0; d < dimensions; d++) {
            // Convert both values to integers in one step
            int intValue1 = solutionToIntegerBits(solution1[d], d);
            int intValue2 = solutionToIntegerBits(solution2[d], d);

            // XOR to find different bits, then count them
            int xorResult = intValue1 ^ intValue2;
            hammingDistance += Integer.bitCount(xorResult);
        }

        return hammingDistance;
    }

    /**
     * Convert a single dimension value to integer bits representation.
     */
    private int solutionToIntegerBits(double value, int dimension) {
        // Normalize the value to [0, 1] range
        double normalizedValue = (value - lowerBound[dimension]) / (upperBound[dimension] - lowerBound[dimension]);

        // Convert to integer in range [0, 2^bitsPerDimension - 1]
        int maxValue = (1 << bitsPerDimension) - 1;
        return (int) Math.round(normalizedValue * maxValue);
    }

    /** Reinitialize a firefly uniformly within bounds and evaluate. */
    private void reinitializeFirefly(int index) {
        for (int d = 0; d < dimensions; d++) {
            fireflies[index][d] = lowerBound[d] + Math.random() * (upperBound[d] - lowerBound[d]);
            fireflies[index][d] = clamp(fireflies[index][d], d);
        }
        brightness[index] = function.evaluate(fireflies[index]);
        updateBest(fireflies[index], brightness[index]);
    }

    /** Move firefly i toward j using normalized distance and floored β. */
    private void moveFirefly(int i, int j) {
        double distance = normalizedDistance(fireflies[i], fireflies[j]);

        // Use attractiveness with floor
        double raw = beta0 * Math.exp(-gamma * distance * distance);
        double beta = (raw < betaMin) ? betaMin : raw;
        if (raw < betaMin) {
            iterBetaFlooredCount++;
        }

        double deltaSq = 0.0;
        for (int d = 0; d < dimensions; d++) {
            // Inertia scales noise; add a small alpha blend for stability
            double noiseScale = currentInertia * currentStepFactor + 0.25 * alpha;
            double old = fireflies[i][d];
            double updated = old + beta * (fireflies[j][d] - old) + noiseScale * (Math.random() - 0.5);
            updated = clamp(updated, d);
            fireflies[i][d] = updated;
            double diff = updated - old;
            deltaSq += diff * diff;
        }
        // Diagnostics
        double stepNorm = Math.sqrt(deltaSq / Math.max(1, dimensions));
        iterStepSum += stepNorm;
        iterStepCount++;
        iterBetaSum += beta;
        iterBetaCount++;
        iterMovesToward++;
    }

    /** Random walk for a given firefly (pure exploration). */
    private void randomWalk(int i) {
        double deltaSq = 0.0;
        for (int d = 0; d < dimensions; d++) {
            // Pure exploration (no attraction)
            double noiseScale = currentInertia * currentStepFactor + 0.25 * alpha;
            double old = fireflies[i][d];
            double updated = old + noiseScale * (Math.random() - 0.5);
            updated = clamp(updated, d);
            fireflies[i][d] = updated;
            double diff = updated - old;
            deltaSq += diff * diff;
        }
        double stepNorm = Math.sqrt(deltaSq / Math.max(1, dimensions));
        iterStepSum += stepNorm;
        iterStepCount++;
        iterRandomWalks++;
    }

    /** Try to improve the best solution with a small random perturbation. */
    private void randomWalkBest() {
        // Propose a perturbation of the current best; accept only if it improves the objective
        double[] candidate = Arrays.copyOf(bestSolution, dimensions);
        for (int d = 0; d < dimensions; d++) {
            // Use same noise scaling as other moves
            double noiseScale = currentInertia * currentStepFactor + 0.25 * alpha;
            candidate[d] = candidate[d] + noiseScale * (Math.random() - 0.5);
            candidate[d] = clamp(candidate[d], d);
        }
        double value = function.evaluate(candidate);
        updateBest(candidate, value);
    }

    // Optional setters for tuning (constructor remains stable)
    public void setInertiaSchedule(double w1, double w2, double b) {
        this.inertiaW1 = w1;
        this.inertiaW2 = w2;
        this.inertiaB = b;
    }

    public void setTheta(double theta) {
        this.theta = theta;
    }

    /** Explicitly set gamma (light absorption). */
    public void setGamma(double gamma) {
        this.gamma = gamma;
    }
    /** Current gamma value. */
    public double getGamma() {
        return this.gamma;
    }

    /**
     * Tune gamma from a desired influence radius r0 on the normalized distance scale.
     * Sets gamma = -ln(tau) / r0^2 where tau = beta(r0)/beta0, tau in (0,1).
     * Example: r0=1, tau=0.6 => gamma≈0.5108
     */
    public void tuneGammaByInfluenceRadius(double r0, double tau) {
        double rr = Math.max(1e-9, r0);
        double tt = Math.min(0.999999, Math.max(1e-9, tau));
        this.gamma = -Math.log(tt) / (rr * rr);
    }

    // Diagnostics getters
    public double getCurrentInertia() {
        return currentInertia;
    }

    public double getCurrentStepFactor() {
        return currentStepFactor;
    }

    public double getLastAvgStep() {
        return lastAvgStep;
    }

    public double getLastAvgBeta() {
        return lastAvgBeta;
    }

    public double getLastFlooredBetaRate() {
        return lastFlooredBetaRate;
    }

    public int getLastMovesTowardCount() {
        return lastMovesToward;
    }

    public int getLastRandomWalkCount() {
        return lastRandomWalks;
    }

    /** Compute inertia weight w_t in [w1, w2] using a log-shaped decay. */
    public double computeSelfAdaptiveInertiaWeight(int t, int T, double w1, double w2, double b) {

        // Guard invalid inputs
        if (Double.isNaN(w1) || Double.isNaN(w2) || Double.isNaN(b)) {
            return Double.NaN;
        }

        // Clamp iteration domain
        int Tmax = Math.max(1, T);
        int tt = Math.min(Math.max(1, t), Tmax);

        // progress in [0,1]: log_T(t) or linear fallback
        double logDen = Math.log(Tmax);
        double progress;
        if (logDen > 0.0) {
            progress = Math.log(tt) / logDen; // result in [0, 1]
        } else {
            // Fallback: linear progress. When T==1, define progress=1 at t=1.
            progress = (Tmax == 1) ? 1.0 : (double) (tt - 1) / (double) (Tmax - 1);
        }

        // w_t moves from w1 toward w2; b controls speed
        double wt = w1 - b * (w1 - w2) * progress;

        // Clamp to [min(w1,w2), max(w1,w2)]
        double lo = Math.min(w1, w2);
        double hi = Math.max(w1, w2);
        if (wt < lo) wt = lo;
        if (wt > hi) wt = hi;

        return wt;
    }

    /** Compute dynamic step size c with dimension-aware theta and a small decaying floor. */
    public double computeDynamicStepFactor(int t, int T, double theta, int D) {

        // Guard invalid inputs
        if (Double.isNaN(theta)) {
            return Double.NaN;
        }

        // Clamp domains
        int Tmax = Math.max(1, T);
        int tt = Math.min(Math.max(1, t), Tmax);
        int dim = Math.max(0, D);

        // Compensate theta for dimension so theta^D is stable around a baseline D0
        double thRaw = Math.max(0.0, Math.min(1.0, theta));
        final double D0 = 50.0; // baseline dimension; tune if desired
        double th = Math.pow(thRaw, D0 / Math.max(1.0, (double) dim));

        // c = th^D * T * exp(-t/T)
        double scaleDim = Math.pow(th, dim); // effectively ~ theta^D0
        double decay = Math.exp(
                -(double) tt / (double) Tmax); // e^{-t/T}: exponential decay from ~1 (early) to ~e^{-1} (at t=T).
        double c = scaleDim * (double) Tmax * decay; // combined result to compute c

        // Add a small, decaying floor based on average range to avoid vanishing steps
        try {
            double avgRange = 0.0;
            if (lowerBound != null && upperBound != null && lowerBound.length == upperBound.length) {
                int L = Math.min(lowerBound.length, upperBound.length);
                for (int k = 0; k < L; k++) avgRange += Math.max(0.0, upperBound[k] - lowerBound[k]);
                avgRange /= Math.max(1, L);
            }
            // Floor = 0.1% of avg range, decays with exp(-t/T)
            double cMin = (avgRange > 0.0 ? 0.001 * avgRange * decay : 0.0);
            c = Math.max(c, cMin);
        } catch (Throwable ignore) {
            // Use c as computed if bounds are unavailable
        }

        // Keep non-negative and finite
        if (!Double.isFinite(c) || c < 0) c = 0.0;
        return c;
    }

    /** Compute β(r) with a minimum floor: max(β0*exp(-γ r^2), βmin). */
    public double computeAttractivenessWithMin(double r, double gamma, double beta0, double betaMin) {
        double beta = beta0 * Math.exp(-gamma * r * r); // Baseline FA formula
        return Math.max(beta, betaMin); // Enforce minimum floor for EFA
    }

    /** Clamp a coordinate to [lowerBound[d], upperBound[d]]. */
    private double clamp(double v, int d) {
        if (v < lowerBound[d]) return lowerBound[d];
        if (v > upperBound[d]) return upperBound[d];
        return v;
    }

    /** Baseline β without floor (kept for reference). */
    @SuppressWarnings("unused")
    private double calculateAttractiveness(int i, int j) {
        double distance = euclideanDistance(fireflies[i], fireflies[j]); // Baseline FA attractiveness (Yang 2009):
        return beta0 * Math.exp(-gamma * distance * distance); // Currently unused, but kept for reference/comparison.
    }

    /** Euclidean distance (unscaled). */
    private double euclideanDistance(double[] a, double[] b) {
        double sum = 0;
        for (int d = 0; d < dimensions; d++) {
            sum += (a[d] - b[d]) * (a[d] - b[d]);
        }
        return Math.sqrt(sum);
    }

    /**
     * Normalized RMS distance: each axis scaled by (ub - lb), then averaged.
     * This avoids √D growth and keeps distances near O(1) across dimensions.
     */
    private double normalizedDistance(double[] a, double[] b) {
        double sum = 0.0;
        for (int d = 0; d < dimensions; d++) {
            double range = Math.max(1e-12, upperBound[d] - lowerBound[d]);
            double diff = (a[d] - b[d]) / range;
            sum += diff * diff;
        }
        return Math.sqrt(sum / Math.max(1, dimensions));
    }

    /** Update the global best if improved (minimization). */
    private void updateBest(double[] candidate, double value) {
        if (value < bestValue) {
            bestValue = value;
            bestSolution = Arrays.copyOf(candidate, dimensions);
        }
    }

    /** Best solution vector. */
    public double[] getBestSolution() {
        return bestSolution;
    }

    /** Best objective value (lower is better). */
    public double getBestValue() {
        return bestValue;
    }

    /** Print the best objective value. */
    public void printResult() {
        System.out.println("Best value = " + bestValue);
    }

    /** Set per-iteration progress listener. */
    public void setProgressListener(ProgressListener listener) {
        this.progressListener = listener;
    }

    /** Set per-firefly step listener. */
    public void setStepListener(StepListener listener) {
        this.stepListener = listener;
    }
}
