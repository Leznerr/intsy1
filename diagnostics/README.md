# Diagnostics Instructions

1. Compile the solver with instrumentation enabled:
   ```bash
   javac -d out $(find src -name "*.java")
   ```
2. Run the baseline diagnostics for the focus maps. The solver prints summary lines
   followed by a JSON block with all counters.
   ```bash
   java -cp out solver.RunSolver --diag original1 original2 original3 fiveboxes3
   ```
3. To increase queue timeline sampling, override the sampling mask (e.g. sample every
   expansion) by passing `--diag-sample=0`:
   ```bash
   java -cp out solver.RunSolver --diag --diag-sample=0 original2
   ```
4. To measure the heuristic without the player proximity term, add
   `--diag-no-proximity`:
   ```bash
   java -cp out solver.RunSolver --diag --diag-no-proximity original2
   ```
5. Each run emits a single JSON object per map. The `timeline` array contains sampled
   top-of-queue snapshots. Diagnostics are no-ops unless `--diag` (or another
   diagnostics flag) is supplied.
