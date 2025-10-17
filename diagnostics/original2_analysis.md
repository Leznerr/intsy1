# Original2 Diagnostics Report

## Executive Summary
- Region and deadlock pruning dominate the search on `original2`. Strict region gating fires 14.3k times before falling back, and deadlock post-pruning removes 83k candidates, versus 1.1k/6k on `original1`, explaining why the queue grows to 7.7k states without goal progress.【F:diagnostics/baseline.log†L2-L4】
- The assignment lower bound flattens the heuristic landscape. Average assignment cost is ~101 (p50=100, p95=107) with 55% of runtime spent inside the matcher on `original2`, compared to ~68 and 2.7% on `original1`, so most expansions have identical f/h scores and depend on depth ordering.【F:diagnostics/baseline.log†L2-L4】
- Progress tie-breaking rarely triggers on `original2` (0 uses vs 12 on `original1`), so the priority queue essentially degrades to breadth-first over equally scored pushes. Open-queue snapshots show the top three pushes sharing f/h with only one offering positive progress.【F:diagnostics/baseline.log†L2-L4】【F:diagnostics/original2_sample0.log†L2-L2】
- Removing the proximity term (`--diag-no-proximity`) delays the first push to 46 ms and still spends 39% of time in assignment, indicating the structural pruning bottlenecks remain even when the heuristic is made steeper.【F:diagnostics/original2_no_proximity.log†L1-L2】
- Call-path timings confirm `GBFS.considerPushesFrom` (13.7 s) and `Heuristic.assignmentLowerBound` (~7.6 s) dominate the 14 s budget on `original2`, while `Deadlock.regionHasGoalIgnoringBoxes` alone consumes 3.3–3.8 s per run.【F:diagnostics/baseline.log†L3-L4】

## Metrics Comparison
| Map | t_first_push (ms) | Exp to first push | Best h at push | Pre-pruned (strict/total) | Post region | Post wallline | Post deadlock | Duplicate (global/local) | Heuristic INF | Open peak | Assignment avg / p50 / p95 | % time in assignment | % time region strict / loose | % time deadlock |
| --- | ---: | ---: | ---: | --- / --- | --- | --- | --- | --- / --- | --- | --- | --- / --- / --- | ---: | --- / --- | --- |
| original1 | 39 | 2 | 89 | 1112 / 533 | 579 | 0 | 5999 | 7775 / 0 | 0 | 173 | 67.6 / 68 / 81 | 2.74 | 21.63 / 42.37 | 53.09 |
| original2 | 1 | 2 | 111 | 14295 / 5729 | 8566 | 0 | 83189 | 67043 / 0 | 0 | 7730 | 101.3 / 100 / 107 | 55.11 | 8.57 / 23.43 | 30.28 |
| original3 | -1 | -1 | -1 | 3 / 1 | 2 | 0 | 0 | 0 / 0 | 0 | 1 | 128 / 128 / 128 | 0.00 | 0.00 / 0.00 | 0.00 |
| fiveboxes3 | 0 | 2 | 31 | 2 / 0 | 2 | 0 | 0 | 0 / 0 | 0 | 1 | 30.5 / 30 / 30 | 0.00 | 0.00 / 0.00 | 0.00 |
【F:diagnostics/baseline.log†L2-L8】

## Timeline to First Push
- `original1`: queue contained only the initial state prior to the first push; progress tie-break engaged 12 times across the run.【F:diagnostics/baseline.log†L1-L2】
- `original2`: baseline sampling records only the initial plateau. With sampling forced on every expansion, the top of the queue just before the first push shows three pushes at identical f/h where only one reduces goal distance (`progress=1`).【F:diagnostics/original2_sample0.log†L1-L2】
- `original3` and `fiveboxes3`: timelines never reach a push because strict region pruning halts exploration immediately.【F:diagnostics/baseline.log†L5-L8】

## Heuristic Quality
- Original2’s assignment lower bound clusters tightly (p50=100, p95=107) versus original1’s wider spread (p50=68, p95=81), leaving little gradient to guide the search.【F:diagnostics/baseline.log†L2-L4】
- Zero-proximity runs shrink assignment share from 55% to 39% but still spend more time in region/deadlock checks, confirming that the LB cost matrix—not the proximity term—dominates.【F:diagnostics/original2_no_proximity.log†L1-L2】

## Pruning Behavior
- Strict region failures occur ~14k times on original2, and fallback rejects another 5.7k pushes for worsening distance, compared to only 1.6k combined on original1.【F:diagnostics/baseline.log†L2-L4】
- Post-push deadlock detection removes 83k candidates on original2 (14× original1), indicating corridor and freeze detection over-fire when the board is crowded.【F:diagnostics/baseline.log†L2-L4】
- Duplicate suppression is 8.6× higher on original2 (67k vs 7.8k), suggesting the search keeps rediscovering near-identical box arrangements after pruning.【F:diagnostics/baseline.log†L2-L4】

## Call-Path Hot Spots
- `GBFS.considerPushesFrom` absorbs ~13.7 s of the 14 s budget on original2, largely due to repeated `regionHasGoal*` and deadlock checks.【F:diagnostics/baseline.log†L3-L4】
- `Heuristic.assignmentLowerBound` and `Heuristic.evaluate` together exceed 15 s cumulative (due to overlapping calls), confirming that box-goal matching is the dominant CPU consumer.【F:diagnostics/baseline.log†L3-L4】
- `Deadlock.regionHasGoalIgnoringBoxes` accounts for ~3.3 s on original2, more than double the original1 cost, because it runs for almost every strict-region failure before any pruning decision.【F:diagnostics/baseline.log†L2-L4】

## Code References
- GBFS comparator and sampling logic show that pushes are ordered by f/h, then depth, with progress tie-break rarely firing on original2 because all candidates share the same heuristic cost.【F:src/solver/GBFS.java†L32-L58】【F:diagnostics/baseline.log†L2-L4】
- `considerPushesFrom` performs strict region, loose fallback, non-worsening checks, then deadlock and duplicate filtering, matching the heavy pre/post prune counts observed.【F:src/solver/GBFS.java†L269-L373】
- `Heuristic.evaluate` records assignment costs and optionally removes proximity, explaining the observed LB plateaus and the effect of the `--diag-no-proximity` experiment.【F:src/solver/Heuristic.java†L89-L120】
- `Deadlock.regionHasGoalForMove` and `regionHasGoalIgnoringBoxes` both run full BFSes per candidate, aligning with the large timing totals for these checks on original2.【F:src/solver/Deadlock.java†L200-L266】【F:src/solver/Deadlock.java†L423-L458】
