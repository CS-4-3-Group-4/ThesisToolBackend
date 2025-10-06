package cs43.group4.parameters;

public class EFAParams {
    public int generations = 300;
    public int numFireflies = 50;
    public double alpha0 = 0.6;
    public double alphaFinal = 0.05;
    public double beta0 = 1.0;
    public double betaMin = 0.2;
    public double gamma = 1.0;

    /**
     * Validate the algorithm parameters.
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public void validate() {
        if (generations < 10) throw new IllegalArgumentException("Invalid generations: " + generations);
        if (generations > 500) throw new IllegalArgumentException("Generations too large: " + generations);
        if (numFireflies < 10) throw new IllegalArgumentException("Invalid numFireflies: " + numFireflies);
        if (numFireflies > 150) throw new IllegalArgumentException("numFireflies too large: " + numFireflies);
        if (alpha0 < 0.01) throw new IllegalArgumentException("Invalid alpha0: " + alpha0);
        if (alpha0 > 1) throw new IllegalArgumentException("alpha0 too large: " + alpha0);
        if (alphaFinal < 0.01) throw new IllegalArgumentException("Invalid alphaFinal: " + alphaFinal);
        if (alphaFinal > 1) throw new IllegalArgumentException("alphaFinal too large: " + alphaFinal);
        if (beta0 < 0.1) throw new IllegalArgumentException("Invalid beta0: " + beta0);
        if (beta0 > 10) throw new IllegalArgumentException("beta0 too large: " + beta0);
        if (betaMin < 0.1) throw new IllegalArgumentException("Invalid betaMin: " + betaMin);
        if (betaMin > 0.5) throw new IllegalArgumentException("betaMin too large: " + betaMin);
        if (betaMin > beta0) throw new IllegalArgumentException("betaMin cannot be greater than beta0");
        if (gamma < 0.1) throw new IllegalArgumentException("Invalid gamma: " + gamma);
        if (gamma > 10) throw new IllegalArgumentException("gamma too large: " + gamma);
    }

    @Override
    public String toString() {
        return "EFAParams {\n" + "  generations = "
                + generations + ",\n" + "  numFireflies = "
                + numFireflies + ",\n" + "  alpha0 = "
                + alpha0 + ",\n" + "  alphaFinal = "
                + alphaFinal + ",\n" + "  beta0 = "
                + beta0 + ",\n" + "  betaMin = "
                + betaMin + ",\n" + "  gamma = "
                + gamma + "\n" + "}";
    }
}

/**
 * Extended Firefly Algorithm Parameters
 *
 * Based on ExtendedFireflyAlgorithm (Abainza et al., 2025)
 *
 * Standard FA Parameters:
 * - generations: Number of iterations (typical: 100-1000)
 * - numFireflies: Population size (typical: 20-100)
 * - alpha0: Initial randomization parameter (typical: 0.2-0.6)
 * - alphaFinal: Final randomization parameter (typical: 0.05-0.2)
 * - beta0: Base attractiveness at r=0 (typical: 1.0)
 * - gamma: Light absorption coefficient (typical: 0.1-10)
 *
 * Extended FA Parameters:
 * - betaMin: Minimum attractiveness floor (typical: 0.2)
 *   Prevents attractiveness from becoming too weak at large distances
 *   Helps maintain exploration capability throughout optimization
 *
 * Note: EFA also includes internal mechanisms:
 * - Objective filtering (infeasible solutions → +∞)
 * - Diversity control via Hamming distance
 * - Dynamic self-adaptation (inertia and step-size)
 */
