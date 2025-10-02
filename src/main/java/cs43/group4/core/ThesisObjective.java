package cs43.group4.core;

/**
 * Thesis objective implementation. Fitness = Objective1 + Objective2 - Objective3 + Objective4
 * FireflyAlgorithm minimizes, so we return -(Fitness) + penalties.
 */
public class ThesisObjective extends ObjectiveFunction {

    private final int Z, C; // barangays, classes
    private final double[] r, f, E, AC; // per barangay
    private final double[] lambda; // per class
    private final double[] supply; // per class (global totals)
    private final double eps; // small constant
    private final double wSupply; // penalty weight for supply violations
    private final Double Ptarget; // optional overall target (nullable)
    private final double wBudget; // penalty weight for P target
    // Optional distance-aware penalty settings
    private final double[][] currentPerClass; // [C][Z] current counts per class and barangay
    private final double[] lat; // [Z] latitude degrees
    private final double[] lon; // [Z] longitude degrees
    private final double wDistance; // weight for average distance moved (km)
    private final double[][] distKm; // [Z][Z] precomputed distances (km) or null

    public ThesisObjective(
            int Z,
            int C,
            double[] r,
            double[] f,
            double[] E,
            double[] AC,
            double[] lambda,
            double[] supply,
            double eps,
            double wSupply,
            Double Ptarget,
            double wBudget) {
        this.Z = Z;
        this.C = C;
        this.r = r;
        this.f = f;
        this.E = E;
        this.AC = AC;
        this.lambda = lambda;
        this.supply = supply;
        this.eps = eps;
        this.wSupply = wSupply;
        this.Ptarget = Ptarget;
        this.wBudget = wBudget;
        this.currentPerClass = null;
        this.lat = null;
        this.lon = null;
        this.wDistance = 0.0;
        this.distKm = null;
    }

    // Overload with distance penalty inputs (optional): if lat/lon or current are null, distance
    // penalty is disabled.
    public ThesisObjective(
            int Z,
            int C,
            double[] r,
            double[] f,
            double[] E,
            double[] AC,
            double[] lambda,
            double[] supply,
            double eps,
            double wSupply,
            Double Ptarget,
            double wBudget,
            double[][] currentPerClass,
            double[] lat,
            double[] lon,
            double wDistance) {
        this.Z = Z;
        this.C = C;
        this.r = r;
        this.f = f;
        this.E = E;
        this.AC = AC;
        this.lambda = lambda;
        this.supply = supply;
        this.eps = eps;
        this.wSupply = wSupply;
        this.Ptarget = Ptarget;
        this.wBudget = wBudget;
        this.currentPerClass = currentPerClass;
        this.lat = lat;
        this.lon = lon;
        this.wDistance = wDistance;
        this.distKm = (enableDistance()) ? precomputeDistances(lat, lon) : null;
    }

    @Override
    public double evaluate(double[] x) {
        // Rebuild A[i][c] as non-negative reals, then repair to respect per-class supply
        double[][] A = new double[Z][C];
        int k = 0;
        for (int i = 0; i < Z; i++) {
            for (int c = 0; c < C; c++, k++) {
                double a = x[k];
                if (a < 0) a = 0;
                A[i][c] = a;
            }
        }

        // Feasibility repair: scale down per-class columns if they exceed supply
        for (int c = 0; c < C; c++) {
            double used = 0.0;
            for (int i = 0; i < Z; i++) used += A[i][c];
            if (used > supply[c] + eps) {
                double scale = supply[c] / (used + eps);
                for (int i = 0; i < Z; i++) A[i][c] *= scale;
            }
        }

        // Totals
        double P = 0.0;
        double[] totalPerI = new double[Z];
        for (int i = 0; i < Z; i++) {
            double s = 0.0;
            for (int c = 0; c < C; c++) s += A[i][c];
            totalPerI[i] = s;
            P += s;
        }
        double denomP = Math.max(P, eps);

        // Objective1: Coverage Ratio
        int Cz = 0;
        for (int i = 0; i < Z; i++) if (totalPerI[i] > 0) Cz++;
        double obj1 = (double) Cz / (double) Z;

        // Objective2: Prioritization Fulfillment
        double obj2sum = 0.0;
        for (int i = 0; i < Z; i++) {
            double logTerm = Math.log(1.0 + Math.max(0.0, r[i]));
            for (int c = 0; c < C; c++) obj2sum += A[i][c] * logTerm;
        }
        double obj2 = obj2sum / denomP;

        // Objective3: Distribution Imbalance (std/mean)
        double mean = 0.0;
        for (double v : totalPerI) mean += v;
        mean /= Math.max(1, Z);
        double var = 0.0;
        for (double v : totalPerI) {
            double d = v - mean;
            var += d * d;
        }
        double std = Math.sqrt(var / Math.max(1, Z));
        double obj3 = std / (mean + eps);

        // Objective4: Demand Satisfaction
        double obj4sum = 0.0;
        for (int i = 0; i < Z; i++) {
            double Si = Math.max(0.0, r[i]) * Math.max(0.0, f[i]);
            for (int c = 0; c < C; c++) {
                double DiC = lambda[c] * (E[i] * Si) / (AC[i] + eps);
                double denom = Math.max(DiC, eps);
                double frac = Math.min(1.0, A[i][c] / denom);
                obj4sum += frac;
            }
        }
        double obj4 = obj4sum / (Z * C);

        double fitness = obj1 + obj2 - obj3 + obj4;

        // Penalties
        double penalty = 0.0;
        // per class supply (should be near-zero after repair; keep as safety)
        for (int c = 0; c < C; c++) {
            double used = 0.0;
            for (int i = 0; i < Z; i++) used += A[i][c];
            double viol = Math.max(0.0, used - supply[c]);
            penalty += wSupply * viol * viol;
        }
        // Total Budget Target
        if (Ptarget != null) {
            double d = (P - Ptarget);
            penalty += wBudget * d * d;
        }

        // Distance penalty: compute average kilometers moved in a greedy nearest-flow sense and
        // penalize it
        if (enableDistance()) {
            double[] tmpDemand = new double[Z];
            double[] tmpSurplus = new double[Z];
            double movedTotal = 0.0;
            double distSum = 0.0;
            for (int c = 0; c < C; c++) {
                // prepare demand/surplus for this class
                for (int i = 0; i < Z; i++) {
                    double need = Math.max(0.0, A[i][c] - currentPerClass[c][i]);
                    double extra = Math.max(0.0, currentPerClass[c][i] - A[i][c]);
                    tmpDemand[i] = need;
                    tmpSurplus[i] = extra;
                }
                // Greedy: repeatedly match nearest surplus for each current largest deficit
                while (true) {
                    int def = -1;
                    double needMax = 0.0;
                    for (int i = 0; i < Z; i++) {
                        if (tmpDemand[i] > 1e-12 && tmpDemand[i] > needMax) {
                            needMax = tmpDemand[i];
                            def = i;
                        }
                    }
                    if (def == -1) break;
                    int src = -1;
                    double bestD = Double.POSITIVE_INFINITY;
                    for (int j = 0; j < Z; j++) {
                        if (tmpSurplus[j] > 1e-12) {
                            double dkm = distKm[j][def];
                            if (dkm < bestD) {
                                bestD = dkm;
                                src = j;
                            }
                        }
                    }
                    if (src == -1) break; // no more surplus
                    double moved = Math.min(tmpSurplus[src], tmpDemand[def]);
                    movedTotal += moved;
                    distSum += moved * bestD;
                    tmpSurplus[src] -= moved;
                    tmpDemand[def] -= moved;
                }
            }
            double avgKm = distSum / Math.max(eps, movedTotal);
            penalty += wDistance * avgKm;
        }

        if (!Double.isFinite(fitness) || !Double.isFinite(penalty)) return 1e30;
        return -(fitness) + penalty;
    }

    private boolean enableDistance() {
        if (currentPerClass == null || lat == null || lon == null) return false;
        if (currentPerClass.length != C) return false;
        for (int c = 0; c < C; c++) if (currentPerClass[c] == null || currentPerClass[c].length != Z) return false;
        if (lat.length != Z || lon.length != Z) return false;
        for (int i = 0; i < Z; i++) if (!Double.isFinite(lat[i]) || !Double.isFinite(lon[i])) return false;
        return true;
    }

    private double[][] precomputeDistances(double[] lat, double[] lon) {
        double[][] d = new double[Z][Z];
        for (int i = 0; i < Z; i++) {
            for (int j = 0; j < Z; j++) {
                d[i][j] = (i == j) ? 0.0 : haversineKm(lat[i], lon[i], lat[j], lon[j]);
            }
        }
        return d;
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0088; // km
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
