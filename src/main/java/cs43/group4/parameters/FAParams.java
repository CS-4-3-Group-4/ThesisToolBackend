package cs43.group4.parameters;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FAParams {
    public int generations = 300;
    public int numFireflies = 50;
    public double alpha0 = 0.6;
    public double alphaFinal = 0.05;
    public double beta0 = 1.0;
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
        if (gamma < 0.1) throw new IllegalArgumentException("Invalid gamma: " + gamma);
        if (gamma > 10) throw new IllegalArgumentException("gamma too large: " + gamma);
    }

    @Override
    public String toString() {
        return "AlgorithmParams {\n"
                + "  generations = " + generations + ",\n"
                + "  numFireflies = " + numFireflies + ",\n"
                + "  alpha0 = " + alpha0 + ",\n"
                + "  alphaFinal = " + alphaFinal + ",\n"
                + "  beta0 = " + beta0 + ",\n"
                + "  gamma = " + gamma + ",\n"
                + "}";
    }
}

/**
 * Number of fireflies (n)
 * - Size of the population (how many candidate solutions are being explored at the same time).
 * - More fireflies → better exploration but more computation.
 * - Typical: 20 – 100.
 *
 * Light Absorption Coefficient (γ, gamma)
 * - Controls how fast attractiveness decays with distance.
 * - High γ → short-range attraction (local search).
 * - Low γ → long-range attraction (global search).
 * - Typical: 0.1 – 10, often γ = 1.
 *
 * Randomization Parameter (α)
 * - Controls the random step size (exploration ability).
 * - High α → more randomness (exploration).
 * - Low α → more deterministic (exploitation).
 * - Often decreases from α₀ → α_final over iterations.
 * - Typical: α ≈ 0.2 – 0.5.
 *
 * Attractiveness (β₀)
 * - Base attractiveness when distance r=0.
 * - Sets the maximum strength of attraction.
 * - Typical: β₀ = 1.
 *
 * Maximum Number of Iterations / Generations (T / MaxGen)
 * How many generations the algorithm runs.
 * Acts as the stopping criterion.
 * Typical: 100 – 1000 depending on problem size.
 **/
