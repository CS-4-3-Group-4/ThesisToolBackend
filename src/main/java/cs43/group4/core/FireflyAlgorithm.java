// The New Firefly Algorithm implementation based on the original source code with refactoring
// plus changes on the attracivesness function and random walk of the best firefly

package cs43.group4.core;

import java.util.Arrays;

/**
 * Firefly Algorithm (Xin-She Yang, 2008) implementation in Java.
 */
public class FireflyAlgorithm {

    // Algorithm parameters
    private double gamma;     // Light absorption coefficient
    private double alpha;     // Randomization Parameter / Randomness step size
    private double alpha0;    // Initial alpha
    private double alphaFinal;// Final alpha
    private double beta0;     // Base attractiveness at r=0

    private int numFireflies; // Population size
    private int dimensions;   // Number of variables
    private int generations;  // Number of iterations

    // Firefly states
    private double[][] fireflies; // Positions of fireflies
    private double[] brightness;  // Objective values
    private double[] bestSolution;
    private double bestValue;

    private ObjectiveFunction function;

    private double[] lowerBound;
    private double[] upperBound;

    // Optional per-iteration progress reporting
    public interface ProgressListener {
        void onIteration(int generation, double[] bestSolution);
    }
    private ProgressListener progressListener;

    // Optional per-firefly step reporting (called many times)
    public interface StepListener {
        void onStep(double[] bestSolution);
    }
    private StepListener stepListener;

    /**
     * Constructor to initialize the Firefly Algorithm.
     */
    public FireflyAlgorithm(
            ObjectiveFunction function,
            int numFireflies,
            double[] lowerBound,
            double[] upperBound,
            double gamma,
            double beta0,
            double alpha0,
            double alphaFinal,
            int generations
    ) {
        this.function = function;
        this.numFireflies = numFireflies;
        this.dimensions = lowerBound.length;
        this.gamma = gamma;
        this.beta0 = beta0;
        this.alpha0 = alpha0;
        this.alphaFinal = alphaFinal;
        this.generations = generations;

    this.lowerBound = Arrays.copyOf(lowerBound, lowerBound.length);
    this.upperBound = Arrays.copyOf(upperBound, upperBound.length);

    this.fireflies = new double[numFireflies][dimensions];
        this.brightness = new double[numFireflies];
        this.bestSolution = new double[dimensions];
        this.bestValue = Double.MAX_VALUE;

    initializePopulation(lowerBound, upperBound);
    }

    /**
     * Initialize population randomly within given bounds.
     */
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

    /**
     * Run the Firefly Algorithm optimization.
     */
    public void optimize() {

        for (int gen = 0; gen < generations; gen++) {
            for (int i = 0; i < numFireflies; i++) {
                for (int j = 0; j < numFireflies; j++) {
                    if (brightness[i] > brightness[j]) { // move i toward brighter j
                        moveFirefly(i, j);
                    } else {
                        randomWalk(i);
                    }
                }
                brightness[i] = function.evaluate(fireflies[i]);
                updateBest(fireflies[i], brightness[i]);

                // High-frequency callback after updating each firefly i
                if (stepListener != null) {
                    stepListener.onStep(Arrays.copyOf(bestSolution, dimensions));
                }
            }

            // Random walk for the best firefly to avoid stagnation
            randomWalkBest();

            // Update randomness (alpha decreases over time)
            alpha = alphaFinal + (alpha0 - alphaFinal) * Math.exp(-0.1 * gen);

            // Progress callback after each generation
            if (progressListener != null) {
                progressListener.onIteration(gen + 1, Arrays.copyOf(bestSolution, dimensions));
            }
        }
    }

    /**
     * Move firefly i towards firefly j based on attractiveness.
     */
    private void moveFirefly(int i, int j) {
        double beta = calculateAttractiveness(i, j);
        for (int d = 0; d < dimensions; d++) {
            fireflies[i][d] = fireflies[i][d]
                            + beta * (fireflies[j][d] - fireflies[i][d])
                            + alpha * (Math.random() - 0.5);
            fireflies[i][d] = clamp(fireflies[i][d], d);
        }
    }

    /**
     * Random walk for a given firefly.
     */
    private void randomWalk(int i) {
        for (int d = 0; d < dimensions; d++) {
            fireflies[i][d] += alpha * (Math.random() - 0.5);
            fireflies[i][d] = clamp(fireflies[i][d], d);
        }
    }

    /**
     * Random walk for the best firefly to avoid stagnation.
     */
    private void randomWalkBest() {
        // Propose a perturbation of the current best; accept only if it improves the objective
        double[] candidate = Arrays.copyOf(bestSolution, dimensions);
        for (int d = 0; d < dimensions; d++) {
            candidate[d] += alpha * (Math.random() - 0.5);
            candidate[d] = clamp(candidate[d], d);
        }
        double value = function.evaluate(candidate);
        updateBest(candidate, value);
    }

    private double clamp(double v, int d) {
        if (v < lowerBound[d]) return lowerBound[d];
        if (v > upperBound[d]) return upperBound[d];
        return v;
    }

    /**
     * Calculate attractiveness β(r) = β0 * exp(-γ * r^2).
     */
    private double calculateAttractiveness(int i, int j) {
        double distance = euclideanDistance(fireflies[i], fireflies[j]);
        return beta0 * Math.exp(-gamma * distance * distance);
    }

    /**
     * Euclidean distance between two points.
     */
    private double euclideanDistance(double[] a, double[] b) {
        double sum = 0;
        for (int d = 0; d < dimensions; d++) {
            sum += (a[d] - b[d]) * (a[d] - b[d]);
        }
        return Math.sqrt(sum);
    }

    /**
     * Update the global best solution if a better one is found.
     */
    private void updateBest(double[] candidate, double value) {
        if (value < bestValue) {
            bestValue = value;
            bestSolution = Arrays.copyOf(candidate, dimensions);
        }
    }

    /**
     * Get the best solution found.
     */
    public double[] getBestSolution() {
        return bestSolution;
    }

    /**
     * Get the best objective value found.
     */
    public double getBestValue() {
        return bestValue;
    }

    /**
     * Print results.
     */
    public void printResult() {
        System.out.println("Best value = " + bestValue);
    }

    /**
     * Progress listener to receive per-iteration updates.
     */
    public void setProgressListener(ProgressListener listener) {
        this.progressListener = listener;
    }

    /**
     * Step listener to receive updates after each firefly update within a generation.
     */
    public void setStepListener(StepListener listener) {
        this.stepListener = listener;
    }
}
