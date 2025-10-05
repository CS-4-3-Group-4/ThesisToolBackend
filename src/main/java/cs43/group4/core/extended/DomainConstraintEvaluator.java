package cs43.group4.core.extended;

import cs43.group4.core.DataLoader;

/**
 * Evaluates if a given solution vector adheres to the domain-specific constraints
 * outlined in our paper. This is the "Objective Function Filtering" step.
 */
public class DomainConstraintEvaluator {

    /**
     * Checks a solution for feasibility against domain-specific constraints.
     *
     * @param x The solution vector from the Firefly Algorithm.
     * @param data The loaded data object containing all barangay and class info.
     * @param Z Total number of barangays.
     * @param C Total number of personnel classes.
     * @return true if the solution is feasible, false otherwise.
     */
    public static boolean isFeasible(double[] x, DataLoader.Data data, int Z, int C) {
        // Rebuild the allocation matrix A[i][c] from the 1D solution vector x.
        double[][] A = new double[Z][C];
        int k = 0;
        for (int i = 0; i < Z; i++) {
            for (int c = 0; c < C; c++, k++) {
                A[i][c] = Math.max(0, x[k]); // Ensure non-negative allocations.
            }
        }

        // Iterate through each barangay and check its allocation against its specific constraint.
        for (int i = 0; i < Z; i++) {
            double floodDepthFt = data.f[i]; // Get the flood depth for the current barangay.

            // Constraint for "No flood" (G) zones: Allocation must be 0.
            if (floodDepthFt < 0.13) {
                double totalAllocated = 0.0;
                for (int c = 0; c < C; c++) {
                    totalAllocated += A[i][c];
                }
                // If any personnel are allocated to a no-flood zone, it's infeasible.
                if (totalAllocated > 1e-6) { // Use a small epsilon for float comparison
                    return false; // Infeasible solution.
                }
                continue; // This barangay is fine, check the next one.
            }

            // Determine the required minimum percentage for zones with flooding.
            double requiredPercentage;
            if (floodDepthFt >= 5.0) { // A: Over the head
                requiredPercentage = 1.00;
            } else if (floodDepthFt >= 4.75) { // B: Neck-deep
                requiredPercentage = 0.75;
            } else if (floodDepthFt >= 4.0) { // C: Chest-deep
                requiredPercentage = 0.50;
            } else if (floodDepthFt >= 2.75) { // D: Waist-deep
                requiredPercentage = 0.40;
            } else if (floodDepthFt >= 1.5) { // E: Knee-deep
                requiredPercentage = 0.30;
            } else { // F: Gutter-deep (0.5 to 1.49 ft)
                requiredPercentage = 0.10;
            }

            // Check the constraint for each personnel class in the barangay.
            for (int c = 0; c < C; c++) {
                double initialPersonnelInBarangay;
                // Get the initial personnel count for the specific class 'c'.
                // This assumes class 0 is SAR and class 1 is EMS, adjust if necessary.
                if (data.classIds[c].equalsIgnoreCase("SAR")) {
                    initialPersonnelInBarangay = data.sarCurrent[i];
                } else if (data.classIds[c].equalsIgnoreCase("EMS")) {
                    initialPersonnelInBarangay = data.emsCurrent[i];
                } else {
                    initialPersonnelInBarangay = 0; // Or handle other classes if they exist.
                }

                double requiredAmount = requiredPercentage * initialPersonnelInBarangay;
                double actualAmount = A[i][c];

                // If the actual allocated amount is less than what's required, it's infeasible.
                if (actualAmount < requiredAmount) {
                    return false; // Infeasible solution.
                }
            }
        }

        // If the method has not returned false after checking all barangays, the solution is feasible.
        return true;
    }
}
