package cs43.group4.core;

/**
 * Simple post-processing allocator to map borrowing flows between barangays. For each class c,
 * matches surplus (current > allocated) to deficit (allocated > current) greedily without
 * distances/costs.
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
            double[] current = currentPerClass[c];
            double[] demand = new double[Z];
            double[] surplus = new double[Z];
            for (int i = 0; i < Z; i++) {
                double need = Math.max(0.0, A[i][c] - current[i]);
                double extra = Math.max(0.0, current[i] - A[i][c]);
                demand[i] = need;
                surplus[i] = extra;
            }
            int iSur = 0, iDef = 0;
            while (true) {
                while (iSur < Z && surplus[iSur] <= 1e-12) iSur++;
                while (iDef < Z && demand[iDef] <= 1e-12) iDef++;
                if (iSur >= Z || iDef >= Z) break;
                double moved = Math.min(surplus[iSur], demand[iDef]);
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
            double[] current = currentPerClass[c];
            double[] demand = new double[Z];
            double[] surplus = new double[Z];
            for (int i = 0; i < Z; i++) {
                double need = Math.max(0.0, A[i][c] - current[i]);
                double extra = Math.max(0.0, current[i] - A[i][c]);
                demand[i] = need;
                surplus[i] = extra;
            }

            // While there is remaining demand and surplus, match nearest pairs
            while (true) {
                int def = -1;
                double needMax = 0.0;
                for (int i = 0; i < Z; i++) {
                    if (demand[i] > 1e-12 && demand[i] > needMax) {
                        needMax = demand[i];
                        def = i;
                    }
                }
                if (def == -1) break; // no more demand

                // Find nearest surplus to this deficit
                int src = -1;
                double bestD = Double.POSITIVE_INFINITY;
                for (int j = 0; j < Z; j++) {
                    if (surplus[j] > 1e-12) {
                        double d = dist[j][def];
                        if (d < bestD) {
                            bestD = d;
                            src = j;
                        }
                    }
                }
                if (src == -1) break; // no more surplus

                double moved = Math.min(surplus[src], demand[def]);
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
}
