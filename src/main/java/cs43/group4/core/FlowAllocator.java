package cs43.group4.core;

/**
 * Simple post-processing allocator to map borrowing flows between barangays. For each class c,
 * builds flows so that each barangay uses its own personnel first (self-flows), then borrows from
 * others when needed. Totals per class match the requested allocations exactly (which are already
 * normalized to match per-class supply totals), and self-allocations (i -> i) are included.
 */
public final class FlowAllocator {

    public static final class Result {
        // flows[c][from][to] amount moved from 'from' barangay to 'to' barangay for class c
        public final double[][][] flows;

        public Result(double[][][] flows) {
            this.flows = flows;
        }
    }

    /**
     * Distance-agnostic greedy allocation of borrowing flows.
     *
     * @param A allocation matrix [Z][C]
     * @param currentPerClass current counts per class [C][Z]
     * @return flows per class [C][Z][Z]
     */
    public static Result allocate(double[][] A, double[][] currentPerClass) {
        int Z = A.length;
        int C = A[0].length;
        double[][][] flows = new double[C][Z][Z];

        for (int c = 0; c < C; c++) {
            // Integer demand from allocations (already normalized/rounded upstream)
            long[] D = new long[Z];
            long Dtotal = 0L;
            for (int i = 0; i < Z; i++) {
                long v = Math.max(0L, Math.round(A[i][c]));
                D[i] = v;
                Dtotal += v;
            }

            // Scale current to match total demand, then round to integers with exact sum
            long[] S = scaleAndRoundToSum(currentPerClass[c], Dtotal);

            // Use own personnel first (self-flows)
            long[] demand = new long[Z];
            long[] surplus = new long[Z];
            for (int i = 0; i < Z; i++) {
                long keep = Math.min(S[i], D[i]);
                flows[c][i][i] += keep; // self-allocation shown explicitly
                long remS = S[i] - keep;
                long remD = D[i] - keep;
                surplus[i] = remS;
                demand[i] = remD;
            }

            // Greedy match remaining demand/surplus without distances
            int iSur = 0, iDef = 0;
            while (true) {
                while (iSur < Z && surplus[iSur] <= 0L) iSur++;
                while (iDef < Z && demand[iDef] <= 0L) iDef++;
                if (iSur >= Z || iDef >= Z) break;
                long moved = Math.min(surplus[iSur], demand[iDef]);
                flows[c][iSur][iDef] += moved;
                surplus[iSur] -= moved;
                demand[iDef] -= moved;
            }
        }
        return new Result(flows);
    }

    /**
     * Distance-aware allocation of borrowing flows using lat/lon (degrees). If any coordinate is NaN,
     * falls back to distance-agnostic. Greedy strategy: for each class, repeatedly send from nearest
     * surplus barangay to each deficit until all deficits or surpluses are resolved.
     *
     * @param A allocation matrix [Z][C]
     * @param currentPerClass current counts per class [C][Z]
     * @param lat latitude per barangay (degrees)
     * @param lon longitude per barangay (degrees)
     * @return flows per class [C][Z][Z]
     */
    public static Result allocate(double[][] A, double[][] currentPerClass, double[] lat, double[] lon) {
        int Z = A.length;
        boolean coordsOk = lat != null && lon != null && lat.length == Z && lon.length == Z;
        if (!coordsOk) return allocate(A, currentPerClass);
        for (int i = 0; i < Z; i++) {
            if (Double.isNaN(lat[i]) || Double.isNaN(lon[i])) return allocate(A, currentPerClass);
        }

        int C = A[0].length;
        double[][][] flows = new double[C][Z][Z];

        // Precompute distances in km
        double[][] dist = new double[Z][Z];
        for (int i = 0; i < Z; i++) {
            for (int j = 0; j < Z; j++) {
                if (i == j) {
                    dist[i][j] = 0.0;
                    continue;
                }
                dist[i][j] = haversineKm(lat[i], lon[i], lat[j], lon[j]);
            }
        }

        for (int c = 0; c < C; c++) {
            // Integer demand from allocations
            long[] D = new long[Z];
            long Dtotal = 0L;
            for (int i = 0; i < Z; i++) {
                long v = Math.max(0L, Math.round(A[i][c]));
                D[i] = v;
                Dtotal += v;
            }

            // Scale current to match total demand; round to integers
            long[] S = scaleAndRoundToSum(currentPerClass[c], Dtotal);

            // Use own personnel first (self-flows)
            long[] demand = new long[Z];
            long[] surplus = new long[Z];
            for (int i = 0; i < Z; i++) {
                long keep = Math.min(S[i], D[i]);
                flows[c][i][i] += keep;
                surplus[i] = S[i] - keep;
                demand[i] = D[i] - keep;
            }

            // While there is remaining demand and surplus, match nearest pairs
            while (true) {
                int def = -1;
                long needMax = 0L;
                for (int i = 0; i < Z; i++) {
                    if (demand[i] > 0L && demand[i] > needMax) {
                        needMax = demand[i];
                        def = i;
                    }
                }
                if (def == -1) break; // no more demand

                // Find nearest surplus to this deficit
                int src = -1;
                double bestD = Double.POSITIVE_INFINITY;
                for (int j = 0; j < Z; j++) {
                    if (surplus[j] > 0L) {
                        double dkm = dist[j][def];
                        if (dkm < bestD) {
                            bestD = dkm;
                            src = j;
                        }
                    }
                }
                if (src == -1) break; // no more surplus

                long moved = Math.min(surplus[src], demand[def]);
                flows[c][src][def] += moved;
                surplus[src] -= moved;
                demand[def] -= moved;
            }
        }

        return new Result(flows);
    }

    // Haversine distance in kilometers
    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0088; // mean Earth radius in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2)
                        * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    // Helper: scale an array of non-negative doubles to sum to a target, and round to integers
    // using largest-remainder so the integer sum equals target.
    private static long[] scaleAndRoundToSum(double[] arr, long targetSum) {
        int n = arr.length;
        long[] out = new long[n];
        if (targetSum <= 0L) {
            // Nothing needed: all zeros
            return out;
        }
        double total = 0.0;
        for (double v : arr) total += Math.max(0.0, v);
        if (total <= 1e-12) {
            // No current available: assume all will be sourced locally (self later)
            // Distribute all to zeros and let self-flows handle demands directly.
            // We'll return zeros; self-flows will then take from D explicitly.
            return out;
        }
        double ratio = (double) targetSum / total;
        long floorSum = 0L;
        double[] frac = new double[n];
        for (int i = 0; i < n; i++) {
            double val = Math.max(0.0, arr[i]) * ratio;
            long fl = (long) Math.floor(val);
            out[i] = fl;
            floorSum += fl;
            frac[i] = val - fl;
        }
        long budget = targetSum - floorSum;
        if (budget > 0L) {
            Integer[] idx = new Integer[n];
            for (int i = 0; i < n; i++) idx[i] = i;
            java.util.Arrays.sort(idx, (a, b) -> Double.compare(frac[b], frac[a]));
            for (int k = 0; k < n && budget > 0L; k++) {
                out[idx[k]] += 1L;
                budget--;
            }
        }
        return out;
    }
}
