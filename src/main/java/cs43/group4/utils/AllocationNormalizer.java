package cs43.group4.utils;

import java.util.Arrays;

/**
 * Utility to convert continuous allocation matrices into integer allocations while ensuring
 * per-class totals do not exceed the derived supply totals (from barangays.csv).
 */
public final class AllocationNormalizer {

    private AllocationNormalizer() {}

    /**
     * Rounds A[i][c] to non-negative integers such that, for each class c,
     * the sum over i is less than or equal to supply[c]. If the continuous
     * sum exceeds supply, values are first scaled down proportionally, then
     * rounded using largest-remainder rounding constrained by the integer
     * supply budget.
     *
     * @param A       allocation matrix (Z x C)
     * @param supply  per-class supply totals (length C)
     * @return a new matrix (Z x C) with integer-valued entries as doubles
     */
    public static double[][] enforceSupplyAndRound(double[][] A, double[] supply) {
        int Z = A.length;
        if (Z == 0) return new double[0][0];
        int C = A[0].length;

        double[][] out = new double[Z][C];

        for (int c = 0; c < C; c++) {
            double cap = (c < supply.length) ? Math.max(0.0, supply[c]) : 0.0;

            double[] col = new double[Z];
            double used = 0.0;
            for (int i = 0; i < Z; i++) {
                double v = A[i][c];
                if (v < 0.0) v = 0.0;
                col[i] = v;
                used += v;
            }

            // Proportional downscale if we exceed cap
            if (used > cap + 1e-9 && used > 0) {
                double scale = cap / used;
                for (int i = 0; i < Z; i++) col[i] *= scale;
                used = cap; // after scaling
            }

            // Largest remainder rounding with an integer budget limited by floor(cap)
            long[] floors = new long[Z];
            double[] rema = new double[Z];
            long floorSum = 0L;
            for (int i = 0; i < Z; i++) {
                long fl = (long) Math.floor(col[i]);
                floors[i] = fl;
                floorSum += fl;
                rema[i] = col[i] - fl;
            }

            long capInt = (long) Math.floor(cap + 1e-9); // never exceed cap

            long budget = capInt - floorSum;
            if (budget > 0L) {
                // Indices sorted by descending remainder
                Integer[] idx = new Integer[Z];
                for (int i = 0; i < Z; i++) idx[i] = i;
                Arrays.sort(idx, (a, b) -> Double.compare(rema[b], rema[a]));

                for (int k = 0; k < Z && budget > 0L; k++) {
                    int i = idx[k];
                    // Only increment those that have a positive remainder
                    if (rema[i] > 1e-12) {
                        floors[i] += 1L;
                        budget--;
                    }
                }
            }

            for (int i = 0; i < Z; i++) out[i][c] = (double) floors[i];
        }

        return out;
    }
}
