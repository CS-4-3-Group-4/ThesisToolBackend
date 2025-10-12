# Thesis Tool Defense – Questions and Model Answers

This guide prepares you for common and high-impact questions during a defense. Each answer is tailored to this repository’s design and implementation. Where relevant, we reference files and endpoints so you can point the panel to concrete artifacts.

Tip: Keep answers structured. Lead with the big idea, justify with evidence (data, algorithm choices, code decisions), then close with trade‑offs or future work.

---

## 1) What problem are you solving, and why is it important?

-   Big idea: We allocate limited emergency personnel across barangays during floods to maximize coverage and prioritize the most at‑risk areas while respecting resource limits and practical constraints (travel distance, existing personnel).
-   Importance: In disasters, where we place scarce responders can affect time‑to‑assist and survivability. Automating and optimizing this planning reduces bias and scales to many barangays.
-   Where in code: Optimization is driven by `cs43.group4.core.ThesisObjective` and executed by FA/EFA (`FireflyAlgorithm`, `ExtendedFireflyAlgorithm`). Results are exposed via HTTP in `Main` and controllers.

## 2) Why did you choose Firefly Algorithm (FA) and extend it (EFA)?

-   Short answer:

    -   FA fits because our objective is non‑convex and mixes soft and hard considerations; it’s efficient to evaluate and easy to extend. We extend it to EFA to make it constraint‑aware, diversity‑preserving, and less sensitive to hyperparameters—properties vanilla FA lacks in this domain.

-   Why FA suits this problem:

    -   Non‑convex landscape: Fitness combines log priority, distribution penalties (std/mean), and capped demand satisfaction; gradients aren’t available or reliable.
    -   Mixed soft/hard goals: Some rules are preferences (balance), others are requirements (no allocation in no‑flood zones). FA tolerates such mixtures.
    -   Continuous search + cheap objective: Allocations are optimized in R^n then integerized post‑hoc; `ThesisObjective#evaluate` is fast enough for many evaluations per second.
    -   Simple, extensible core: `cs43.group4.core.FireflyAlgorithm` is compact and instrumentable (progress callbacks), ideal for adding domain logic.

-   Why we had to extend FA (pain points observed):

    1. Feasibility blindness: Vanilla FA spends time in clearly invalid regions (e.g., allocating to no‑flood zones).
    2. Premature convergence: Population collapses to a mode early under strong attraction or unlucky starts.
    3. Hyperparameter sensitivity: γ and β0 that work on one dataset don’t transfer cleanly to others (different scales/feature ranges).
    4. No adaptive schedule: Fixed α/step can over‑explore late or under‑explore early.

-   What EFA changes (and where in code) to solve these:

    -   Hard feasibility filter: Rejects infeasible candidates immediately via `extended.DomainConstraintEvaluator.isFeasible(...)` inside `ExtendedFireflyAlgorithm#optimize` → higher feasible‑rate, less wasted compute.
    -   Diversity control: `applyDiversityControl` uses Hamming distance over `solutionToIntegerBits` and resets outliers with `reinitializeFirefly` → mitigates mode collapse/premature convergence.
    -   Self‑adaptive dynamics: `computeSelfAdaptiveInertiaWeight` and `computeDynamicStepFactor` modulate exploration→exploitation over iterations → steadier convergence without manual schedules.
    -   Attractiveness floor: `calculateAttractivenessWithMin` ensures β ≥ β_min → preserves long‑range pull so distant promising areas remain reachable.
    -   γ tuning + normalization: `tuneGammaByInfluenceRadius` and `normalizedDistance` make interaction length‑scales consistent across problem sizes → reduces retuning between datasets.

-   Outcome in practice:

    -   More stable results across seeds (lower variance) and fewer infeasible proposals evaluated; faster time‑to‑target fitness for the same population/generations in our internal runs. You can verify via multiple‑run endpoints (`/efa/multiple/run`) and iteration curves (`/efa/iterations`).
    -   Trade‑off: Slight extra overhead per iteration for feasibility/diversity bookkeeping and a few additional parameters; acceptable given the robustness gain.

-   When to use which:
    -   Use FA for quick exploratory baselines or tiny problems.
    -   Use EFA for production‑quality plans where feasibility and stability matter.

### Deep dive: FA vs EFA in this project

-   Representation and search space:
    -   A solution is a continuous vector that maps to an allocation matrix A[i][c] (barangay i, class c). Constraints are enforced by repair inside the objective and by integer normalization post‑optimization.
    -   We minimize `−Fitness + penalties` where Fitness combines coverage, priority, balance, and demand satisfaction (see Q3).
-   Baseline FA mechanics (where to look):
    -   `cs43.group4.core.FireflyAlgorithm`
    -   Initialize population, evaluate all; iteratively move each firefly toward brighter ones using attractiveness β(r) and random walks (with α decay). Update the global best each iteration and emit progress/step callbacks.
    -   Key methods: `initializePopulation`, `calculateAttractiveness`, `moveFirefly`, `randomWalk`, `randomWalkBest`, `updateBest`.
-   EFA upgrades (where to look): `cs43.group4.core.ExtendedFireflyAlgorithm`
    -   Feasibility filter: `extended.DomainConstraintEvaluator.isFeasible(...)` rejects candidates that violate flood‑band rules (e.g., no allocation in no‑flood zones; minimum proportions in higher bands).
    -   Diversity control: `applyDiversityControl` computes pairwise Hamming distance via `solutionToIntegerBits` + `calculateHammingDistance`. When similarity is too high, `reinitializeFirefly` resets outliers to restore diversity.
    -   Self‑adaptive dynamics: `computeSelfAdaptiveInertiaWeight` and `computeDynamicStepFactor` adjust exploration/exploitation as iterations progress, improving stability and escape from local minima.
    -   Attractiveness floor: `calculateAttractivenessWithMin` ensures β ≥ β_min so distant but promising regions remain reachable.
    -   Gamma tuning by influence radius: `tuneGammaByInfluenceRadius` sets γ so that attraction decays at a problem‑appropriate scale (roughly the distance where neighborhood influence should halve), reducing tedious manual tuning across datasets.
    -   Normalized distances: `normalizedDistance` compares solutions on a scale‑free basis across dimensions so γ and β behave consistently when the problem size changes.
-   Stopping/control:
    -   Fixed generations by default; progress is observable via iteration history endpoints. FA exposes a stop endpoint; the algorithm also supports interruption flags in the runners.
-   Complexity and cost drivers:
    -   Per iteration cost ≈ O(P^2) pairwise interactions + objective evaluations; EFA adds feasibility checks/diversity bookkeeping. Dominant cost remains the objective (`ThesisObjective#evaluate`) and distance calculations.
-   Practical tuning guidance:
    -   Increase population for exploration (but watch quadratic neighbor checks); increase generations for refinement.
    -   β0, β_min, γ interact: larger β0 speeds convergence; β_min avoids “dead” attraction at scale; γ controls locality (higher = more local search).
    -   Keep α relatively high early; decay toward the end (already implemented). Adjust diversity thresholds if you see stagnation.

## 3) What exactly does the Thesis Objective optimize? (Explain the 4 objectives)

-   Obj1 – Coverage ratio: Maximize barangays that receive any personnel.
-   Obj2 – Prioritization: Reward allocation to higher‑hazard barangays using log(1 + hazard) for diminishing returns.
-   Obj3 – Distribution balance: Penalize inequality using std/mean of per‑barangay totals (we subtract this term).
-   Obj4 – Demand satisfaction: For each barangay/class, estimate demand `DiC = lambda[c] * (E[i] * r[i]*f[i]) / (AC[i] + eps)`, then credit up to full satisfaction `min(1, A[i][c] / DiC)`.
-   Overall fitness: Fitness = Obj1 + Obj2 − Obj3 + Obj4. We minimize `−Fitness + penalties`.
-   Where: `cs43.group4.core.ThesisObjective#evaluate`.

## 4) How do you ensure feasibility (supply limits, no‑flood constraints, etc.)?

-   Per‑class supply: We repair allocations inside the objective by scaling columns down if they exceed class supply; also apply a penalty for safety.
-   Domain rules: In EFA we apply `DomainConstraintEvaluator.isFeasible` to reject solutions that, for example, allocate anything to no‑flood zones or provide less than the required fraction in high‑flood zones.
-   Integer outputs: After optimization, `AllocationNormalizer.enforceSupplyAndRound` converts continuous A[i][c] to integer counts without exceeding supplies.

## 5) How are flows computed and why are they needed?

-   Why: A barangay may require more than its current in‑barangay personnel. Flows describe borrowing from surplus barangays.
-   How:
    -   Step 1: Self‑flows (use own current personnel first).
    -   Step 2: Greedy matching of remaining surplus to deficits.
    -   Distance‑aware option: If lat/lon exist, borrow from nearest sources (shorter travel time/cost).
-   Where: `cs43.group4.core.FlowAllocator` with `allocate(A, current)` and `allocate(A, current, lat, lon)`.

## 6) What inputs does the system require? How are missing values handled?

-   Inputs: `data/classes.csv` (class_id, class_name, lambda, supply) and `data/barangays.csv` (id, name, hazard, flood, optional population/exposure/total_personnel, optional sar_current/ems_current, optional lat/lon).
-   Missing values:
    -   Exposure derived from population mean scaling if missing.
    -   AC estimated from population share if total_personnel missing.
    -   Current SAR/EMS inferred from hazard split ratios when absent.
    -   Coordinates optional; if missing we still run in distance‑agnostic mode.
-   Where: `cs43.group4.core.DataLoader`.

## 7) What are the key differences between single run and multiple runs?

-   Single run: Stores iteration history, allocations, and flows; these are retrievable via `/fa/allocations`, `/fa/flows`, `/fa/iterations` (similarly for EFA).
-   Multiple runs: Focus on robustness; we report aggregated statistics (best/average/worst fitness, time, memory). Iterations and per‑run allocations/flows are not stored to keep it light.
-   Where: `FARunner`/`EFARunner` methods and controllers.

## 8) How do you measure performance and resource usage?

-   Execution time: Measured per run in milliseconds.
-   Memory usage: Thread‑allocated bytes via `com.sun.management.ThreadMXBean`.
-   Aggregation: For multiple runs we compute average/min/max across runs.
-   Where: `FARunner`/`EFARunner` around `optimize()` calls.

## 9) How do hyperparameters affect the results? (alpha, beta0, gamma, population size, generations)

-   Population size (numFireflies): Higher → better exploration, more compute.
-   Generations: More iterations → more refinement, longer time.
-   Alpha (randomness): Higher early encourages exploration; decays to focus on exploitation.
-   Beta0 (base attractiveness): Stronger pull toward the best; too high can cause early clustering.
-   Gamma (light absorption): Higher makes attraction short‑range (local search); lower broadens search.
-   EFA‑specific betaMin: Ensures attraction never vanishes at large distances.
-   Where to set: `FAParams` and `EFAParams`; both validated before runs.

## 10) How do you justify the demand model formula (DiC)?

-   Rationale: Demand should increase with hazard severity (r) and flood depth (f) and with exposure (E); it should decrease with existing capacity (AC). Class weights (lambda) enable prioritizing classes differently.
-   Practical effect: High‑hazard/deep‑flood barangays with high exposure and low capacity will be preferred.
-   Sensitivity: Lambda and penalty weights can be tuned. Future work can calibrate to historical incident/response data.

## 11) What happens if the data contains errors or is incomplete?

-   CSV parsing is tolerant: many fields are optional; missing values use safe defaults/derivations.
-   Domain filtering in EFA shields the optimizer from unfit candidates.
-   Controllers validate query/body parameters and return clear error messages.

## 12) How do you explain the output to non‑technical stakeholders?

-   Fitness score: Represents overall quality (higher is better in UI) combining coverage, priority, balance, and demand matching.
-   Allocation table: How many SAR/EMS units each barangay receives.
-   Flows: Who lends to whom, showing resource movement.
-   Multiple runs: Stability/robustness overview (average/variance) rather than a single number.

## 13) What are the limitations and potential biases?

-   Demand model relies on hazard, flood depth, exposure, and AC proxies. If inputs are biased or outdated, results may be skewed.
-   Metaheuristics don’t guarantee a global optimum; results are high‑quality but approximate.
-   Distance‑aware flows assume straight‑line (haversine) distances; real travel times may differ due to roads and conditions.

## 14) What alternatives did you consider and why were they not chosen?

-   Linear/Integer Programming: Attractive for hard constraints and exactness, but the non‑linear objective (log terms, std/mean) and soft constraints complicate formulation.
-   Other metaheuristics (GA, PSO, DE): Comparable choices; FA/EFA offered a good balance of simplicity and customizability for this domain.

## 15) How would you validate the model in the real world?

-   Backtesting: Compare results to historical deployments and outcomes.
-   Expert review: Have domain experts assess allocations qualitatively.
-   Sensitivity analysis: Vary lambda/penalties and observe allocation changes.
-   Pilot drills: Simulate scenarios and measure time‑to‑assist improvements.

## 16) Can you walk us through the request flow from API to result?

-   Client POSTs `/efa/single/run` (optionally with parameters).
-   Controller (`EFAController`) parses/validates and triggers a background run with `EFARunner`.
-   `EFARunner` loads data (`DataLoader`), builds `ThesisObjective`, configures `ExtendedFireflyAlgorithm`, runs optimization.
-   After optimize(): results/allocations/flows are stored. Client polls `/efa/status` then requests `/efa/results`, `/efa/allocations`, and `/efa/flows`.

## 17) What would you improve next?

-   Integrate road‑network travel times (e.g., OSRM/Graph algorithms) for flows and distance penalties.
-   Add calibration/learning to estimate lambda and weights from data.
-   Provide reproducible seeds and richer experiment logging.
-   Add unit tests for controllers and edge cases in `DataLoader` and `FlowAllocator`.

---

## EFA emphasis – additional questions you can expect

### EFA‑A1) What is the exact movement equation you use?

-   Directional move toward a brighter neighbor j from i uses attractiveness with an exponential decay and an additive random component around the current or best solution.
-   In code: `moveFirefly` (deterministic attraction term scaled by β), `randomWalk`/`randomWalkBest` (stochastic exploration scaled by α and step factor). See `ExtendedFireflyAlgorithm` methods mentioned in the deep dive.

### EFA‑A2) How are constraints encoded—hard vs. soft?

-   Hard constraints (EFA): Domain feasibility is a hard filter; infeasible candidates are rejected before acceptance (`DomainConstraintEvaluator.isFeasible`).
-   Soft constraints: Supply/budget (if configured) are penalized/auto‑repaired inside `ThesisObjective`. Post‑run integer normalization enforces final supply hard caps.

### EFA‑A3) How do you quantify diversity and when do you reinitialize?

-   We binarize/encode solution structure via `solutionToIntegerBits` and compute Hamming distance (`calculateHammingDistance`). If population similarity rises above a threshold (clustered), `applyDiversityControl` reinitializes select individuals using `reinitializeFirefly`.
-   Rationale: Maintain exploration capacity and avoid premature convergence.

### EFA‑A4) Which parameters matter most in EFA, and how sensitive is it?

-   β_min: Prevents attraction collapse at long range; too high can reduce local refinement, too low can stall exploration.
-   γ (via influence radius): Sets effective neighborhood size; mismatched γ can cause either aimless wandering (too low) or myopic search (too high).
-   Adaptive step/inertia: Too aggressive → oscillations; too conservative → slow progress.
-   Mitigation: Use moderate defaults, verify via multiple runs; adjust thresholds based on convergence curves from `/efa/iterations`.

### EFA‑A5) How did you validate EFA’s benefits over FA?

-   Ablation plan:
    1. FA baseline (no feasibility/diversity/adaptation).
    2.  - Feasibility filter only.
    3.  - Diversity control only.
    4.  - Self‑adaptive schedules.
    5. Full EFA (all features).
-   Metrics: Best/avg fitness, variance across runs, time to reach a fixed fitness level, proportion of infeasible proposals evaluated.
-   Expected: EFA improves stability (lower variance), feasibility rate, and time to good solutions, with modest overhead.

### EFA‑A6) What are typical failure modes and mitigations?

-   Poor data quality → bad guidance: add validation in `DataLoader`, sanity checks on hazard/flood ranges.
-   Stagnation: lower γ or strengthen diversity thresholds; increase α early iterations.
-   Over‑exploration: increase β0 or decrease α decay; raise generations for late refinement.

### EFA‑A7) Computational complexity and scaling

-   Per iteration O(P^2 + P·cost(eval)). With P up to a few hundred, this is manageable; for very large P, consider neighbor pruning or spatial indexing to approximate pairwise pulls.
-   Distance calculations can dominate when distance‑aware flows are recomputed frequently—keep that post‑processing, not per‑iteration.

### EFA‑A8) Why not only hard constraints (LP/IP)?

-   Our objective uses non‑linear terms (log, std/mean) and soft trade‑offs; encoding all as MILP would require approximations that complicate the model. EFA accommodates non‑convexity and mixed constraints naturally.

## Appendix – Code Pointers

-   Objective math: `cs43.group4.core.ThesisObjective#evaluate`
-   EFA loop with constraints/diversity: `cs43.group4.core.ExtendedFireflyAlgorithm#optimize`
-   Domain rules: `cs43.group4.core.extended.DomainConstraintEvaluator`
-   Integer rounding: `cs43.group4.utils.AllocationNormalizer`
-   Flows (distance‑aware): `cs43.group4.core.FlowAllocator`
-   API wiring: `cs43.group4.Main`, controllers in `cs43.group4.controllers`

## Appendix – Useful Endpoints (Bruno-ready)

-   Health: `GET /health`
-   FA: `/fa/single/run`, `/fa/multiple/run?runs=N`, `/fa/status`, `/fa/results`, `/fa/iterations`, `/fa/allocations`, `/fa/flows`
-   EFA: `/efa/single/run`, `/efa/multiple/run?runs=N`, `/efa/status`, `/efa/results`, `/efa/iterations`, `/efa/allocations`, `/efa/flows`
