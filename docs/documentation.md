# ThesisToolBackend – A Practical Guide for New Developers

Welcome! This guide explains what this backend does, the optimization concepts behind it, and how the code is organized. It is written for readers who may be new to the project and to metaheuristic optimization.

The system allocates limited emergency personnel (e.g., SAR and EMS) to barangays under flood conditions. It uses two algorithms:

-   Firefly Algorithm (FA) – the baseline optimizer
-   Extended Firefly Algorithm (EFA) – FA with constraint filtering, diversity control, and self-adaptation to improve robustness and convergence

You can run either algorithm via HTTP endpoints and inspect results such as best fitness, iteration history, allocations, and inter-barangay flows.

---

## Big picture: Data → Optimizer → Results

1. Input data (CSV)

-   `data/classes.csv`: personnel classes (e.g., SAR/EMS) with supply (how many are available) and lambda (importance weight used in demand estimation).
-   `data/barangays.csv`: barangay-level hazard, flood depth, optional population, exposure, current personnel counts, and coordinates.

2. Objective function (what “good” means)

-   The optimizer minimizes `ThesisObjective` which encodes four goals and penalties. Higher real-world quality corresponds to a lower objective value. We also expose a “fitness” value to the UI that is just the negative of the minimized objective (so higher is better on the dashboard).

3. Optimizer (FA/EFA)

-   Searches the space of all feasible allocations to maximize fitness (minimize objective). EFA adds guardrails (constraint filtering) and diversity boosts.

4. Post-processing

-   Convert continuous allocations to integer counts that don’t exceed class supplies.
-   Compute inter-barangay flows (who lends personnel to whom) with options to prefer short travel distances if coordinates are available.

5. API responses

-   Status, iteration history (single-run only), final results, allocations, and flows.

---

## The Thesis Objective (what the algorithm optimizes)

`ThesisObjective` translates domain goals into mathematics. Given an allocation matrix A[i][c] (barangay i, class c), we compute:

Notation

-   Z = number of barangays; C = number of classes
-   r[i] = hazard level; f[i] = flood depth (feet); E[i] = exposure; AC[i] = adaptive capacity
-   lambda[c] = class weight; supply[c] = total available units for class c
-   P = total allocated units = sum_i sum_c A[i][c]
-   totalPerI[i] = sum_c A[i][c]

The fitness to maximize (we minimize its negative) is:

1. Objective 1 – Coverage ratio

    - “How many barangays received any personnel?”
    - Cz = count of barangays with totalPerI[i] > 0
    - Obj1 = Cz / Z

2. Objective 2 – Prioritization (hazard-aware)

    - “Allocate more where hazard is higher.”
    - Uses log(1 + r[i]) to reward allocation in higher hazard areas but gently (diminishing returns)
    - Obj2 = (sum_i sum_c A[i][c] \* log(1 + max(0, r[i]))) / max(P, eps)

3. Objective 3 – Distribution balance

    - “Avoid extreme inequality of totals across barangays.”
    - Compute mean and standard deviation of totalPerI[i]
    - Obj3 = std / (mean + eps) – smaller is better, so this term is subtracted in the fitness

4. Objective 4 – Demand satisfaction (need vs allocation)
    - “Meet estimated demand informed by hazard, exposure, and capacity.”
    - For each (i,c): DiC = lambda[c] _ (E[i] _ (max(0,r[i]) \* max(0,f[i]))) / (AC[i] + eps)
    - Contribution is capped: min(1, A[i][c] / max(DiC, eps))
    - Obj4 = average over (i,c)

Combined fitness

-   Fitness = Obj1 + Obj2 − Obj3 + Obj4
-   Optimizer works with Objective = −Fitness + Penalties (to minimize)

Penalties (constraints)

-   Per-class supply safety: even after soft repair, we add a penalty if any class column exceeds supply;
    -   penalty += wSupply \* max(0, (sum_i A[i][c] − supply[c]))^2
-   Optional total budget Ptarget: discourages allocations far from a target total
    -   penalty += wBudget \* (P − Ptarget)^2
-   Optional distance penalty (if current counts and geo available): discourage long-distance borrowing in aggregate
    -   Compute a greedy matching between deficits and surpluses and penalize the average km moved
    -   penalty += wDistance \* avgKm

Why this matters

-   Obj1 ensures wide coverage; Obj2 ensures priority to hazardous zones; Obj3 keeps distributions fair; Obj4 encourages meeting need. Penalties preserve feasibility and practical constraints.

Where to read this in code

-   `cs43.group4.core.ThesisObjective#evaluate` contains the full implementation and comments.

---

## Firefly Algorithm (FA) vs Extended Firefly Algorithm (EFA)

Both algorithms maintain a population of “fireflies” (candidate allocations). Brighter = better (lower objective value). Fireflies move toward brighter neighbors with a distance-weighted attraction and some random exploration.

Key ideas

-   Attractiveness β(r) = β0 \* exp(−γ r^2); r is distance between solutions.
-   Randomness α decays over time (exploration → exploitation).
-   Best solution also gets a small random perturbation to escape plateaus.

EFA adds three upgrades

1. Objective filtering (Domain constraints)
    - Infeasible candidates (e.g., allocations too low for high-flood barangays or any allocation in a “no flood” zone) get +∞ objective and are discarded from consideration.
    - Implemented by `DomainConstraintEvaluator.isFeasible` and invoked inside EFA.
2. Diversity control (Hamming distance)
    - If two solutions are too “similar” (below a threshold on a bit-encoded representation), one is reinitialized to keep the population diverse.
3. Self-adaptation
    - Auto-adjusts inertia and step factor based on iteration count and dimensionality, helping balance exploration and convergence across problem sizes.

When to use which

-   Start with EFA for robust results on this problem; it respects domain rules automatically and tends to converge more reliably.
-   FA is still useful as a simpler baseline and for comparison.

---

## Data model and loading

`DataLoader` reads the two CSVs and:

-   Parses required and optional columns
-   Derives exposure from population (if missing)
-   Estimates AC (adaptive capacity) if total personnel is missing
-   Estimates per-barangay SAR/EMS split if not provided (based on hazard split ratios: High 85/15, Medium 75/25, Low 65/35)
-   Reads coordinates (lat/lon) if present

The runner reconstructs the decision vector as an allocation matrix A[Z][C] and builds variable bounds. Per-class upper bounds are chosen conservatively using class supply and barangay AC to keep the search effective.

---

## Post-processing: integer allocations and flows

Why integer allocations?

-   Real deployments need whole units. After optimization (continuous), we convert to integers per class without exceeding supplies using largest-remainder rounding (`AllocationNormalizer.enforceSupplyAndRound`).

Why flows?

-   If a barangay needs more than it has locally, we compute who “lends” to it. We first count self-usage (stay local), then re-distribute surplus to deficits. With lat/lon we prefer shorter routes.

Where

-   `cs43.group4.core.FlowAllocator` has two allocate(...) methods: one distance-agnostic and one distance-aware.

---

## HTTP API (quick reference)

-   Health: `GET /health` → { status: "UP" }

-   FA

    -   `POST /fa/single/run` – starts single run (optional JSON body with FAParams)
    -   `POST /fa/multiple/run?runs=N` – runs N experiments (2–100)
    -   `GET /fa/status` – running/progress info
    -   `GET /fa/results` – final metrics (or aggregated stats for multiple runs)
    -   `GET /fa/iterations` – per-iteration fitness (single-run only)
    -   `GET /fa/allocations` – integer allocations per barangay (single-run only)
    -   `GET /fa/flows` – flow entries between barangays (single-run only)

-   EFA (same set with `/efa/...`)

See `ThesisToolAPIs/` for Bruno requests.

---

## Per-file and per-method reference

Below are concise references with purpose, parameters, returns, and short code snippets.

### cs43.group4.Main

Starts the Javalin server and registers all endpoints.

-   main(String[] args)
    -   Parameters: `args` (String[])
    -   Returns: void
    -   Snippet:

```java
Javalin app = Javalin.create(config -> {
    config.http.defaultContentType = "application/json";
    config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.anyHost()));
}).start(PORT);
```

### cs43.group4.controllers.FAController / EFAController

HTTP handlers that orchestrate runs and serve results.

-   getStatus(Context ctx): JSON with running/progress, mode (single/multiple)
-   postStop(Context ctx): stop a running job
-   getResults(Context ctx): final results or aggregated stats
-   getIterations(Context ctx): iteration history (single-run only)
-   postSingleRun(Context ctx): starts a single run (validates params)
-   postMultipleRun(Context ctx): starts multiple runs (`?runs=...`)
-   getAllocations(Context ctx): single-run allocations
-   getFlows(Context ctx): single-run flows

Snippet:

```java
if (runner != null && runner.isRunning()) {
    runner.stop();
    ctx.json(Map.of("message", "Algorithm stopped"));
}
```

### cs43.group4.FARunner / cs43.group4.EFARunner

Run lifecycle managers. EFA runner uses `ExtendedFireflyAlgorithm` and adds diagnostics.

-   run(): executes a single optimization
-   runMultiple(int numRuns): sequential runs with aggregation
-   getStatus(), getResults(): maps for UI/clients
-   getAllocations(), getFlows(), getIterationHistory(): lists for UI
-   stop(), isRunning(), setError(...): controls

Core snippet (FA):

```java
FireflyAlgorithm fa = new FireflyAlgorithm(
    thesisObj, params.numFireflies, lower, upper,
    params.gamma, params.beta0, params.alpha0, params.alphaFinal, params.generations);
fa.setProgressListener((generation, bestX) -> { /* track iteration */ });
fa.optimize();
```

Core snippet (EFA):

```java
ExtendedFireflyAlgorithm efa = new ExtendedFireflyAlgorithm(
    thesisObj, data, params.numFireflies, lower, upper,
    params.gamma, params.beta0, params.betaMin,
    params.alpha0, params.alphaFinal, params.generations);
efa.tuneGammaByInfluenceRadius(1.0, 0.6);
```

### cs43.group4.core.ObjectiveFunction

Abstract base for any objective minimized by the optimizers.

-   evaluate(double[] x): returns objective value (lower is better)

### cs43.group4.core.ThesisObjective

Implements the four objectives and penalties described earlier.

-   evaluate(double[] x): computes `-(Obj1 + Obj2 − Obj3 + Obj4) + penalties`, after repairing per-class supply.
-   Private helpers: `enableDistance`, `precomputeDistances`, `haversineKm`.

Snippet (supply repair):

```java
for (int c = 0; c < C; c++) {
    double used = 0.0; for (int i = 0; i < Z; i++) used += A[i][c];
    if (used > supply[c] + eps) {
        double scale = supply[c] / (used + eps);
        for (int i = 0; i < Z; i++) A[i][c] *= scale;
    }
}
```

### cs43.group4.core.FireflyAlgorithm

Baseline optimizer.

-   optimize(): move-toward-brighter or random-walk, update best, perturb best, decay alpha, notify progress.
-   setProgressListener(...), setStepListener(...)

Snippet:

```java
if (brightness[i] > brightness[j]) { moveFirefly(i, j); } else { randomWalk(i); }
```

### cs43.group4.core.ExtendedFireflyAlgorithm

Enhanced optimizer with constraint filtering, diversity, and self-adaptation.

-   optimize(): adds: feasibility filter, Hamming-based reinit, diagnostics, and gamma tuning support.
-   tuneGammaByInfluenceRadius(double r0, double tau)
-   computeSelfAdaptiveInertiaWeight(...), computeDynamicStepFactor(...)

Snippet (feasibility filter):

```java
boolean feasible = DomainConstraintEvaluator.isFeasible(
    fireflies[i], this.data, dimensions / data.C, data.C);
brightness[i] = feasible ? function.evaluate(fireflies[i]) : Double.POSITIVE_INFINITY;
```

### cs43.group4.core.extended.DomainConstraintEvaluator

Domain rules referenced by EFA (also useful to reason about acceptable solutions):

-   No-flood (< 0.13 ft): total allocation must be 0
-   Flooded zones: minimum required percentage (by flood band) of the initial personnel per class must remain

-   isFeasible(double[] x, DataLoader.Data data, int Z, int C) → boolean

### cs43.group4.core.DataLoader

Parses CSV files and builds a `Data` object with all arrays needed by the objective and algorithms. Derives exposure, AC, and current per-class when missing; supports lat/lon.

-   load(Path barangaysCsv, Path classesCsv) → Data
-   Data inner class fields: ids/names, r, f, E, AC, sarCurrent, emsCurrent, lat, lon, classIds/classNames, lambda, supply

### cs43.group4.core.FlowAllocator

Greedy flow reconstruction after rounding allocations.

-   allocate(A, current): distance-agnostic greedy
-   allocate(A, current, lat, lon): distance-aware greedy (haversine), fallback to agnostic if coordinates invalid

Snippet:

```java
flows[c][i][i] += keep; // self-allocation
// then greedily match remaining surplus to remaining demand
```

### cs43.group4.parameters.FAParams / EFAParams

Hyperparameters with validation and safe defaults.

-   generations, numFireflies, alpha0/alphaFinal, beta0, gamma, and EFA’s betaMin
-   validate(): throws if out of range

### cs43.group4.utils.\*

Small helpers and DTOs:

-   AllocationNormalizer.enforceSupplyAndRound(A, supply): integerize per-class without exceeding supply (largest remainder)
-   AllocationResult, FlowResult, IterationResult: lightweight data structures for API outputs
-   Log: leveled, colored console logging

---

## Tips for reading/running the code

-   Minimization vs. maximization: The algorithms minimize the objective; the “fitness” shown in logs and API is `-objective` so higher means better.
-   Single vs. multiple runs: Allocations/flows/iterations are only for single runs. Multiple runs produce aggregated statistics across repeats.
-   Distance-aware behavior appears in both the objective (optional) and flow building (optional) when coordinates are present.

If you’re new to FA/EFA, start by:

1. Reading `ThesisObjective#evaluate` to see what “good” means.
2. Skimming `ExtendedFireflyAlgorithm#optimize` to understand the search loop.
3. Calling `POST /efa/single/run` and watching `/efa/status` ➝ `/efa/results` ➝ `/efa/allocations`.

This guide aims to give you both the why and the how. For exact method signatures and in-code details, the snippets and references above point you to the corresponding lines.
