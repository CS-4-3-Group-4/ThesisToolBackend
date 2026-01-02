package cs43.group4.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Usage pattern (frontend/controller side):
 * - Create one shared instance per algorithm execution: ObjectiveLogger logger = new ObjectiveLogger(true);
 * - After each run, call exactly one method per objective with the variables used and
 *   the final computed objective value. The method stores data internally and returns
 *   a snapshot object whose arrays can be displayed or exported.
 */
public class ObjectiveLogger {

    // Debug print switch. When true, store... methods print variables & finals.
    private boolean debug = false;

    public ObjectiveLogger(boolean debug) {
        this.debug = debug;
    }

    public void setDebug(boolean debug) { this.debug = debug; }

    // -------------------------------
    // Internal storage per objective
    // -------------------------------

    // Objective 1: Coverage Score -> variables: Cz, Z; final: obj1
    private final List<Integer> obj1_Cz = new ArrayList<>();
    private final List<Integer> obj1_Z  = new ArrayList<>();
    private final List<Double>  obj1_final = new ArrayList<>();

    // Objective 2: Prioritization Fulfillment -> variables: A[i][c], r[i], P, Z, C; final: obj2
    private final List<double[][]> obj2_A_mats = new ArrayList<>();
    private final List<double[]>   obj2_r_vecs = new ArrayList<>();
    private final List<Double>     obj2_Ps     = new ArrayList<>();
    private final List<Integer>    obj2_Zs     = new ArrayList<>();
    private final List<Integer>    obj2_Cs     = new ArrayList<>();
    private final List<Double>     obj2_final  = new ArrayList<>();

    // Objective 3: Distribution Imbalance -> variables: totalsPerI[], mean, std, eps; final: obj3
    private final List<double[]> obj3_totalsPerI = new ArrayList<>();
    private final List<Double>   obj3_mean       = new ArrayList<>();
    private final List<Double>   obj3_std        = new ArrayList<>();
    private final List<Double>   obj3_eps        = new ArrayList<>();
    private final List<Double>   obj3_final      = new ArrayList<>();

    // Objective 4: Demand Satisfaction -> variables: A[i][c], D[i][c], Z, C; final: obj4
    private final List<double[][]> obj4_A_mats = new ArrayList<>();
    private final List<double[][]> obj4_D_mats = new ArrayList<>();
    private final List<Integer>    obj4_Zs     = new ArrayList<>();
    private final List<Integer>    obj4_Cs     = new ArrayList<>();
    private final List<Double>     obj4_final  = new ArrayList<>();

    // Objective 5: Displaced Population Index -> variables: A_i (sum across classes), DP_i, Z, eps; final: obj5
    private final List<double[]> obj5_Ai_totals = new ArrayList<>();
    private final List<double[]> obj5_DPi       = new ArrayList<>();
    private final List<Integer>  obj5_Zs        = new ArrayList<>();
    private final List<Double>   obj5_eps       = new ArrayList<>();
    private final List<Double>   obj5_final     = new ArrayList<>();

    // -------------------------------
    // Snapshot DTOs for frontend access
    // -------------------------------

    public static class Objective1Data {
        public final int[] Cz;           // per run
        public final int[] Z;            // per run
        public final double[] finalVals; // obj1 per run
        public Objective1Data(int[] Cz, int[] Z, double[] finalVals) {
            this.Cz = Cz; this.Z = Z; this.finalVals = finalVals;
        }
    }

    public static class Objective2Data {
        public final double[][][] A_runs; // [run][Z][C]
        public final double[][]   r_runs; // [run][Z]
        public final double[]     P_runs; // [run]
        public final int[]        Z_runs; // [run]
        public final int[]        C_runs; // [run]
        public final double[]     finalVals; // obj2 per run
        public Objective2Data(double[][][] A_runs, double[][] r_runs, double[] P_runs,
                              int[] Z_runs, int[] C_runs, double[] finalVals) {
            this.A_runs = A_runs; this.r_runs = r_runs; this.P_runs = P_runs;
            this.Z_runs = Z_runs; this.C_runs = C_runs; this.finalVals = finalVals;
        }
    }

    public static class Objective3Data {
        public final double[][] totalsPerI_runs; // [run][Z]
        public final double[]   mean_runs;       // [run]
        public final double[]   std_runs;        // [run]
        public final double[]   eps_runs;        // [run]
        public final double[]   finalVals;       // obj3 per run
        public Objective3Data(double[][] totalsPerI_runs, double[] mean_runs,
                              double[] std_runs, double[] eps_runs, double[] finalVals) {
            this.totalsPerI_runs = totalsPerI_runs; this.mean_runs = mean_runs;
            this.std_runs = std_runs; this.eps_runs = eps_runs; this.finalVals = finalVals;
        }
    }

    public static class Objective4Data {
        public final double[][][] A_runs;   // [run][Z][C]
        public final double[][][] D_runs;   // [run][Z][C]
        public final int[]        Z_runs;   // [run]
        public final int[]        C_runs;   // [run]
        public final double[]     finalVals;// obj4 per run
        public Objective4Data(double[][][] A_runs, double[][][] D_runs,
                              int[] Z_runs, int[] C_runs, double[] finalVals) {
            this.A_runs = A_runs; this.D_runs = D_runs; this.Z_runs = Z_runs;
            this.C_runs = C_runs; this.finalVals = finalVals;
        }
    }

    public static class Objective5Data {
        public final double[][] AiTotals_runs; // [run][Z]
        public final double[][] DPi_runs;      // [run][Z]
        public final int[]      Z_runs;        // [run]
        public final double[]   eps_runs;      // [run]
        public final double[]   finalVals;     // obj5 per run
        public Objective5Data(double[][] AiTotals_runs, double[][] DPi_runs, int[] Z_runs,
                              double[] eps_runs, double[] finalVals) {
            this.AiTotals_runs = AiTotals_runs; this.DPi_runs = DPi_runs;
            this.Z_runs = Z_runs; this.eps_runs = eps_runs; this.finalVals = finalVals;
        }
    }

    /**
     * Objective 1 (Coverage Score): store Cz, Z and final objective value for this run.
     */
    public Objective1Data storeObjective1Data(int Cz, int Z, double obj1Final) {
        obj1_Cz.add(Cz);
        obj1_Z.add(Z);
        obj1_final.add(obj1Final);
        if (debug) {
            System.out.println("[Objective1] run=" + obj1_final.size() + " Cz=" + Cz + ", Z=" + Z + ", obj1=" + obj1Final);
        }
        return new Objective1Data(listToIntArray(obj1_Cz), listToIntArray(obj1_Z), listToDoubleArray(obj1_final));
    }

    /**
     * Objective 2 (Prioritization Fulfillment): store A[i][c], r[i], P, Z, C and final value.
     */
    public Objective2Data storeObjective2Data(double[][] A, double[] r, double P, int Z, int C, double obj2Final) {
        Objects.requireNonNull(A, "A must not be null");
        Objects.requireNonNull(r, "r must not be null");
        obj2_A_mats.add(deepCopy(A));
        obj2_r_vecs.add(copy(r));
        obj2_Ps.add(P);
        obj2_Zs.add(Z);
        obj2_Cs.add(C);
        obj2_final.add(obj2Final);
        if (debug) {
            System.out.println("[Objective2] run=" + obj2_final.size() + " Z=" + Z + ", C=" + C + ", P=" + P + ", obj2=" + obj2Final);
            System.out.println("           A[0] sample=" + (Z > 0 ? rowPreview(A[0]) : "<none>") + ", r[0]=" + (r.length > 0 ? r[0] : Double.NaN));
        }
        return new Objective2Data(listTo3D(obj2_A_mats), listTo2D(obj2_r_vecs), listToDoubleArray(obj2_Ps),
                                  listToIntArray(obj2_Zs), listToIntArray(obj2_Cs), listToDoubleArray(obj2_final));
    }

    /**
     * Objective 3 (Distribution Imbalance): store totalsPerI[], mean, std, eps and final value.
     */
    public Objective3Data storeObjective3Data(double[] totalsPerI, double mean, double std, double eps, double obj3Final) {
        obj3_totalsPerI.add(copy(totalsPerI));
        obj3_mean.add(mean);
        obj3_std.add(std);
        obj3_eps.add(eps);
        obj3_final.add(obj3Final);
        if (debug) {
            System.out.println("[Objective3] run=" + obj3_final.size() + " mean=" + mean + ", std=" + std + ", eps=" + eps + ", obj3=" + obj3Final);
            System.out.println("           totalsPerI sample=" + rowPreview(totalsPerI));
        }
        return new Objective3Data(listTo2D(obj3_totalsPerI), listToDoubleArray(obj3_mean), listToDoubleArray(obj3_std),
                                  listToDoubleArray(obj3_eps), listToDoubleArray(obj3_final));
    }

    /**
     * Objective 4 (Demand Satisfaction): store A[i][c], D[i][c], Z, C and final value.
     */
    public Objective4Data storeObjective4Data(double[][] A, double[][] D, int Z, int C, double obj4Final) {
        obj4_A_mats.add(deepCopy(A));
        obj4_D_mats.add(deepCopy(D));
        obj4_Zs.add(Z);
        obj4_Cs.add(C);
        obj4_final.add(obj4Final);
        if (debug) {
            System.out.println("[Objective4] run=" + obj4_final.size() + " Z=" + Z + ", C=" + C + ", obj4=" + obj4Final);
            System.out.println("           A[0] sample=" + (Z > 0 ? rowPreview(A[0]) : "<none>") + ", D[0] sample=" + (Z > 0 ? rowPreview(D[0]) : "<none>"));
        }
        return new Objective4Data(listTo3D(obj4_A_mats), listTo3D(obj4_D_mats), listToIntArray(obj4_Zs), listToIntArray(obj4_Cs), listToDoubleArray(obj4_final));
    }

    /**
     * Objective 5 (Displaced Population Index): store A_i totals, DP_i, Z, eps and final value.
     */
    public Objective5Data storeObjective5Data(double[] AiTotals, double[] DPi, int Z, double eps, double obj5Final) {
        obj5_Ai_totals.add(copy(AiTotals));
        obj5_DPi.add(copy(DPi));
        obj5_Zs.add(Z);
        obj5_eps.add(eps);
        obj5_final.add(obj5Final);
        if (debug) {
            System.out.println("[Objective5] run=" + obj5_final.size() + " Z=" + Z + ", eps=" + eps + ", obj5=" + obj5Final);
            System.out.println("           AiTotals sample=" + rowPreview(AiTotals) + ", DPi sample=" + rowPreview(DPi));
        }
        return new Objective5Data(listTo2D(obj5_Ai_totals), listTo2D(obj5_DPi), listToIntArray(obj5_Zs), listToDoubleArray(obj5_eps), listToDoubleArray(obj5_final));
    }

    // ----------------------
    // Helper conversion APIs
    // ----------------------
    private static double[][] deepCopy(double[][] src) {
        if (src == null) return null;
        double[][] out = new double[src.length][];
        for (int i = 0; i < src.length; i++) out[i] = copy(src[i]);
        return out;
    }
    private static double[] copy(double[] src) {
        if (src == null) return null;
        double[] out = new double[src.length];
        System.arraycopy(src, 0, out, 0, src.length);
        return out;
    }
    private static int[] listToIntArray(List<Integer> l) {
        int[] out = new int[l.size()];
        for (int i = 0; i < l.size(); i++) out[i] = l.get(i);
        return out;
    }
    private static double[] listToDoubleArray(List<Double> l) {
        double[] out = new double[l.size()];
        for (int i = 0; i < l.size(); i++) out[i] = l.get(i);
        return out;
    }
    private static double[][] listTo2D(List<double[]> l) {
        double[][] out = new double[l.size()][];
        for (int i = 0; i < l.size(); i++) out[i] = copy(l.get(i));
        return out;
    }
    private static double[][][] listTo3D(List<double[][]> l) {
        double[][][] out = new double[l.size()][][];
        for (int i = 0; i < l.size(); i++) out[i] = deepCopy(l.get(i));
        return out;
    }

    // ------------------
    // Debug print helpers
    // ------------------
    // Only used when debug==true. Easy to disable by setDebug(false).
    private static String rowPreview(double[] row) {
        if (row == null) return "<null>";
        int n = Math.min(5, row.length);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%.4f", row[i]));
        }
        if (row.length > n) sb.append(", ...");
        sb.append("]");
        return sb.toString();
    }
}
