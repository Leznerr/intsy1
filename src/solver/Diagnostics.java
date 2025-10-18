package solver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;

/**
 * Lightweight diagnostics instrumentation gated behind {@link #ENABLED}.
 * The solver must behave identically when diagnostics are disabled.
 */
public final class Diagnostics {
    private Diagnostics() {}

    public static boolean ENABLED = false;
    public static boolean ZERO_PROXIMITY = false;
    public static int SAMPLE_MASK = 0x7F; // sample 1/128 events by default

    private static String currentMap = "";
    private static long searchStartNano;
    private static long firstPushNano = -1L;
    private static long expansionsToFirstPush = -1L;
    private static int bestOpenHeuristicAtFirstPush = Integer.MAX_VALUE;
    private static long firstProgressPushNano = -1L;
    private static long expansionsToFirstProgressPush = -1L;
    private static int firstProgressValue = 0;
    private static int bestOpenHeuristicAtFirstProgress = Integer.MAX_VALUE;
    private static boolean timelineFrozen = false;
    private static boolean searchStarted = false;
    private static long lastQueueSampleExpansion = -1L;

    private static long expandedStates = 0L;
    private static long openPeak = 0L;
    private static long heuristicInfHits = 0L;
    private static long heuristicEvalCalls = 0L;
    private static long assignmentTimeNs = 0L;
    private static long deadlockRegionTimeNs = 0L;
    private static long deadlockRegionCalls = 0L;
    private static long deadlockLooseTimeNs = 0L;
    private static long deadlockLooseCalls = 0L;
    private static long deadlockFreezeTimeNs = 0L;
    private static long deadlockFreezeCalls = 0L;
    private static long deadlockCheckTimeNs = 0L;
    private static long deadlockCheckCalls = 0L;
    private static long considerPushTimeNs = 0L;
    private static long considerPushCalls = 0L;
    private static long assignmentCostSum = 0L;
    private static long assignmentCostSamples = 0L;

    private static long prePrunedRegionStrict = 0L;
    private static long prePrunedRegionStrictFast = 0L;
    private static long prePrunedRegionLooseFail = 0L;
    private static long prePrunedNonWorsen = 0L;
    private static long prePrunedRoomQuota = 0L;
    private static long postPrunedRegion = 0L;
    private static long postPrunedWallLine = 0L;
    private static long postPrunedDeadlock = 0L;
    private static long duplicatePrunedLocal = 0L;
    private static long duplicatePrunedGlobal = 0L;
    private static long stickyGoalBlocked = 0L;
    private static long lbWorsePruned = 0L;
    private static long macroStepsTotal = 0L;
    private static long macroStepsSaved = 0L;

    private static long comparatorDepthDecisions = 0L;
    private static long comparatorPushDecisions = 0L;
    private static long comparatorHeuristicDecisions = 0L;
    private static long comparatorProgressDecisions = 0L;
    private static long comparatorInsertionDecisions = 0L;

    private static final int ASSIGNMENT_SAMPLE_LIMIT = 10_000;
    private static final int TIMELINE_SAMPLE_LIMIT = 256;
    private static final int HISTOGRAM_LIMIT = 5_000;

    private static final List<Integer> assignmentValues = new ArrayList<>();
    private static final List<Integer> histogramValues = new ArrayList<>();
    private static final Deque<String> queueTimeline = new ArrayDeque<>();
    private static final Map<Integer, Integer> histogram = new TreeMap<>();
    private static final Map<String, Long> timingTotals = new HashMap<>();
    private static final Map<String, Long> timingCounts = new HashMap<>();

    private static boolean firstPushRecorded = false;
    private static boolean progressPushRecorded = false;
    private static long solveEndNano = 0L;
    private static boolean solved = false;
    private static boolean limitHit = false;

    public static void resetForMap(String map) {
        if (!ENABLED) {
            return;
        }
        currentMap = map;
        searchStartNano = 0L;
        solveEndNano = 0L;
        firstPushNano = -1L;
        expansionsToFirstPush = -1L;
        bestOpenHeuristicAtFirstPush = Integer.MAX_VALUE;
        firstProgressPushNano = -1L;
        expansionsToFirstProgressPush = -1L;
        firstProgressValue = 0;
        bestOpenHeuristicAtFirstProgress = Integer.MAX_VALUE;
        timelineFrozen = false;
        searchStarted = false;
        lastQueueSampleExpansion = -1L;

        expandedStates = 0L;
        openPeak = 0L;
        heuristicInfHits = 0L;
        heuristicEvalCalls = 0L;
        assignmentTimeNs = 0L;
        deadlockRegionTimeNs = 0L;
        deadlockRegionCalls = 0L;
        deadlockLooseTimeNs = 0L;
        deadlockLooseCalls = 0L;
        deadlockFreezeTimeNs = 0L;
        deadlockFreezeCalls = 0L;
        deadlockCheckTimeNs = 0L;
        deadlockCheckCalls = 0L;
        considerPushTimeNs = 0L;
        considerPushCalls = 0L;
        assignmentCostSum = 0L;
        assignmentCostSamples = 0L;

        prePrunedRegionStrict = 0L;
        prePrunedRegionStrictFast = 0L;
        prePrunedRegionLooseFail = 0L;
        prePrunedNonWorsen = 0L;
        prePrunedRoomQuota = 0L;
        postPrunedRegion = 0L;
        postPrunedWallLine = 0L;
        postPrunedDeadlock = 0L;
        duplicatePrunedLocal = 0L;
        duplicatePrunedGlobal = 0L;
        stickyGoalBlocked = 0L;
        lbWorsePruned = 0L;
        macroStepsTotal = 0L;
        macroStepsSaved = 0L;

        comparatorDepthDecisions = 0L;
        comparatorPushDecisions = 0L;
        comparatorHeuristicDecisions = 0L;
        comparatorProgressDecisions = 0L;
        comparatorInsertionDecisions = 0L;

        assignmentValues.clear();
        histogramValues.clear();
        histogram.clear();
        queueTimeline.clear();
        timingTotals.clear();
        timingCounts.clear();

        firstPushRecorded = false;
        progressPushRecorded = false;
        solved = false;
        limitHit = false;
    }

    public static void markSearchStart() {
        if (!ENABLED) {
            return;
        }
        searchStartNano = System.nanoTime();
        searchStarted = true;
    }

    public static void markSearchFinish(boolean solvedFlag, boolean limitHitFlag) {
        if (!ENABLED) {
            return;
        }
        solveEndNano = System.nanoTime();
        solved = solvedFlag;
        limitHit = limitHitFlag;
        timelineFrozen = true;
    }

    public static void incrementExpanded(State state) {
        if (!ENABLED) {
            return;
        }
        expandedStates++;
    }

    public static void checkFirstPush(State state, PriorityQueue<State> open) {
        if (!ENABLED) {
            return;
        }
        if (state == null || state.getPushes() <= 0) {
            return;
        }
        if (!firstPushRecorded) {
            firstPushRecorded = true;
            if (searchStarted) {
                firstPushNano = System.nanoTime();
                expansionsToFirstPush = expandedStates;
                State head = open.peek();
                if (head != null) {
                    bestOpenHeuristicAtFirstPush = head.getHeuristic();
                } else {
                    bestOpenHeuristicAtFirstPush = state.getHeuristic();
                }
            }
        }
        int progress = Heuristic.lastPushProgress(state);
        if (!progressPushRecorded && progress > 0) {
            progressPushRecorded = true;
            if (searchStarted) {
                firstProgressPushNano = System.nanoTime();
                expansionsToFirstProgressPush = expandedStates;
                firstProgressValue = progress;
                State head = open.peek();
                if (head != null) {
                    bestOpenHeuristicAtFirstProgress = head.getHeuristic();
                } else {
                    bestOpenHeuristicAtFirstProgress = state.getHeuristic();
                }
            }
            timelineFrozen = true;
        }
    }

    public static void recordComparatorDecision(String field) {
        if (!ENABLED) {
            return;
        }
        switch (field) {
            case "heuristic":
                comparatorHeuristicDecisions++;
                break;
            case "pushes":
                comparatorPushDecisions++;
                break;
            case "depth":
                comparatorDepthDecisions++;
                break;
            case "progress":
                comparatorProgressDecisions++;
                break;
            case "insertion":
                comparatorInsertionDecisions++;
                break;
            default:
                break;
        }
    }

    public static void recordOpenSize(int openSize) {
        if (!ENABLED) {
            return;
        }
        if (openSize > openPeak) {
            openPeak = openSize;
        }
    }

    public static void recordPrePrunedStrict() {
        if (!ENABLED) {
            return;
        }
        prePrunedRegionStrict++;
    }

    public static void recordPrePrunedRegionStrictFast() {
        if (!ENABLED) {
            return;
        }
        prePrunedRegionStrictFast++;
    }

    public static void recordPrePrunedLooseFail() {
        if (!ENABLED) {
            return;
        }
        prePrunedRegionLooseFail++;
    }

    public static void recordPrePrunedNonWorsen() {
        if (!ENABLED) {
            return;
        }
        prePrunedNonWorsen++;
    }

    public static void recordPrePrunedRoomQuota() {
        if (!ENABLED) {
            return;
        }
        prePrunedRoomQuota++;
    }

    public static void recordPostPrunedRegion() {
        if (!ENABLED) {
            return;
        }
        postPrunedRegion++;
    }

    public static void recordPostPrunedWallLine() {
        if (!ENABLED) {
            return;
        }
        postPrunedWallLine++;
    }

    public static void recordPostPrunedDeadlock() {
        if (!ENABLED) {
            return;
        }
        postPrunedDeadlock++;
    }

    public static void recordStickyGoalBlocked() {
        if (!ENABLED) {
            return;
        }
        stickyGoalBlocked++;
    }

    public static void recordDuplicateLocal() {
        if (!ENABLED) {
            return;
        }
        duplicatePrunedLocal++;
    }

    public static void recordDuplicateGlobal() {
        if (!ENABLED) {
            return;
        }
        duplicatePrunedGlobal++;
    }

    public static void recordLBWorsePruned() {
        if (!ENABLED) {
            return;
        }
        lbWorsePruned++;
    }

    public static void recordMacroSteps(int total, int saved) {
        if (!ENABLED) {
            return;
        }
        macroStepsTotal += total;
        macroStepsSaved += saved;
    }

    public static void recordConsiderPushStart() {
        if (!ENABLED) {
            return;
        }
        considerPushCalls++;
    }

    public static void recordConsiderPushTime(long nanos) {
        if (!ENABLED) {
            return;
        }
        considerPushTimeNs += nanos;
        addTiming("GBFS.considerPushesFrom", nanos);
    }

    public static void recordAssignmentValue(int value) {
        if (!ENABLED) {
            return;
        }
        assignmentCostSamples++;
        assignmentCostSum += value >= 0 ? value : 0;
        if (assignmentValues.size() < ASSIGNMENT_SAMPLE_LIMIT) {
            assignmentValues.add(value);
        }
        if (histogramValues.size() < HISTOGRAM_LIMIT) {
            histogramValues.add(value);
            histogram.merge(value, 1, Integer::sum);
        }
    }

    public static void recordAssignmentTime(long nanos) {
        if (!ENABLED) {
            return;
        }
        assignmentTimeNs += nanos;
        addTiming("Heuristic.assignmentLowerBound", nanos);
    }

    public static void recordHeuristicEvaluation(boolean inf) {
        if (!ENABLED) {
            return;
        }
        heuristicEvalCalls++;
        if (inf) {
            heuristicInfHits++;
        }
    }

    public static void recordDeadlockRegionTime(long nanos) {
        if (!ENABLED) {
            return;
        }
        deadlockRegionTimeNs += nanos;
        deadlockRegionCalls++;
        addTiming("Deadlock.regionHasGoalForMove", nanos);
    }

    public static void recordDeadlockLooseTime(long nanos) {
        if (!ENABLED) {
            return;
        }
        deadlockLooseTimeNs += nanos;
        deadlockLooseCalls++;
        addTiming("Deadlock.regionHasGoalIgnoringBoxes", nanos);
    }

    public static void recordDeadlockFreezeTime(long nanos) {
        if (!ENABLED) {
            return;
        }
        deadlockFreezeTimeNs += nanos;
        deadlockFreezeCalls++;
        addTiming("Deadlock.isWallLineFreeze", nanos);
    }

    public static void recordDeadlockCheckTime(long nanos) {
        if (!ENABLED) {
            return;
        }
        deadlockCheckTimeNs += nanos;
        deadlockCheckCalls++;
        addTiming("Deadlock.isDeadlock", nanos);
    }

    public static boolean shouldSample(long expanded) {
        if (!ENABLED) {
            return false;
        }
        if (timelineFrozen) {
            return false;
        }
        if ((expanded & SAMPLE_MASK) != 0) {
            return false;
        }
        if (expanded == lastQueueSampleExpansion) {
            return false;
        }
        if (queueTimeline.size() >= TIMELINE_SAMPLE_LIMIT) {
            return false;
        }
        lastQueueSampleExpansion = expanded;
        return true;
    }

    public static void recordQueueSnapshot(PriorityQueue<State> open, Comparator<State> comparator) {
        if (!ENABLED) {
            return;
        }
        if (timelineFrozen) {
            return;
        }
        PriorityQueue<State> copy = new PriorityQueue<>(comparator);
        copy.addAll(open);
        List<String> lines = new ArrayList<>();
        int limit = Math.min(10, copy.size());
        for (int i = 0; i < limit; i++) {
            State s = copy.poll();
            if (s == null) {
                break;
            }
            lines.add(formatStateSummary(s));
        }
        queueTimeline.add("snapshot=" + expandedStates + " " + lines);
    }

    private static String formatStateSummary(State s) {
        int progress = Heuristic.lastPushProgress(s);
        char lastMove = s.getLastMove();
        String last = lastMove == '\0' ? "-" : Character.toString(lastMove);
        return "{f=" + s.getFCost()
                + ",h=" + s.getHeuristic()
                + ",pushes=" + s.getPushes()
                + ",depth=" + s.getDepth()
                + ",last='" + last
                + "',progress=" + progress
                + ",ins=" + s.getInsertionId()
                + "}";
    }

    public static void recordFirstPush(State parentState, State pushedState, PriorityQueue<State> open) {
        if (!ENABLED) {
            return;
        }
        if (firstPushRecorded) {
            return;
        }
        firstPushRecorded = true;
        timelineFrozen = true;
        if (searchStarted) {
            firstPushNano = System.nanoTime();
            expansionsToFirstPush = expandedStates;
            bestOpenHeuristicAtFirstPush = open.peek() != null ? open.peek().getHeuristic() : pushedState.getHeuristic();
        }
    }

    public static void emitDiagnostics(long elapsedMillis, boolean solvedFlag, boolean limitHitFlag, long closedSize) {
        if (!ENABLED) {
            return;
        }
        long timeToFirstPushMs = -1L;
        if (firstPushNano > 0L && searchStartNano > 0L) {
            timeToFirstPushMs = (firstPushNano - searchStartNano) / 1_000_000L;
        }
        long timeToFirstProgressMs = -1L;
        if (firstProgressPushNano > 0L && searchStartNano > 0L) {
            timeToFirstProgressMs = (firstProgressPushNano - searchStartNano) / 1_000_000L;
        }
        long totalTimeMs = solveEndNano > 0L ? (solveEndNano - searchStartNano) / 1_000_000L : elapsedMillis;
        Map<String, Object> json = new TreeMap<>();
        json.put("map", currentMap);
        json.put("solved", solvedFlag);
        json.put("limitHit", limitHitFlag);
        json.put("time_ms", totalTimeMs);
        json.put("time_to_first_push_ms", timeToFirstPushMs);
        json.put("expansions_to_first_push", expansionsToFirstPush);
        json.put("best_open_heuristic_at_first_push", bestOpenHeuristicAtFirstPush == Integer.MAX_VALUE ? -1 : bestOpenHeuristicAtFirstPush);
        json.put("time_to_first_progress_ms", timeToFirstProgressMs);
        json.put("expansions_to_first_progress", expansionsToFirstProgressPush);
        json.put("first_progress_value", firstProgressValue);
        json.put("best_open_heuristic_at_first_progress", bestOpenHeuristicAtFirstProgress == Integer.MAX_VALUE ? -1 : bestOpenHeuristicAtFirstProgress);
        json.put("open_peak", openPeak);
        json.put("heuristic_INF_hits", heuristicInfHits);
        json.put("heuristic_evaluations", heuristicEvalCalls);
        json.put("pre_pruned_region_strict", prePrunedRegionStrict);
        json.put("pre_pruned_region_strict_fast", prePrunedRegionStrictFast);
        json.put("pre_pruned_region_loose_fail", prePrunedRegionLooseFail);
        json.put("pre_pruned_nonworsen", prePrunedNonWorsen);
        json.put("pre_pruned_room_quota", prePrunedRoomQuota);
        json.put("post_pruned_region", postPrunedRegion);
        json.put("post_pruned_wallline", postPrunedWallLine);
        json.put("post_pruned_deadlock", postPrunedDeadlock);
        json.put("duplicate_pruned_local", duplicatePrunedLocal);
        json.put("duplicate_pruned_global", duplicatePrunedGlobal);
        json.put("sticky_goal_blocked", stickyGoalBlocked);
        json.put("lb_worse_pruned", lbWorsePruned);
        json.put("macro_steps_total", macroStepsTotal);
        json.put("macro_steps_saved", macroStepsSaved);
        json.put("assignmentLB_avg", assignmentCostSamples == 0 ? -1 : assignmentCostSum / (double) assignmentCostSamples);
        json.put("assignmentLB_p50", percentile(50));
        json.put("assignmentLB_p95", percentile(95));
        json.put("assignment_samples", assignmentCostSamples);
        json.put("assignment_histogram", new TreeMap<>(histogram));
        json.put("time_in_assignment_pct", percentage(assignmentTimeNs, totalTimeMs));
        json.put("time_in_region_pct", percentage(deadlockRegionTimeNs, totalTimeMs));
        json.put("time_in_region_loose_pct", percentage(deadlockLooseTimeNs, totalTimeMs));
        json.put("time_in_freeze_pct", percentage(deadlockFreezeTimeNs, totalTimeMs));
        json.put("time_in_deadlock_pct", percentage(deadlockCheckTimeNs, totalTimeMs));
        json.put("time_in_consider_push_pct", percentage(considerPushTimeNs, totalTimeMs));
        json.put("expanded_states", expandedStates);
        json.put("closed_size", closedSize);
        json.put("timeline", new ArrayList<>(queueTimeline));
        json.put("comparator_decisions", comparatorStats());
        json.put("timing_totals_ns", new TreeMap<>(timingTotals));
        json.put("timing_counts", new TreeMap<>(timingCounts));
        System.out.println(toJson(json));
    }

    private static Map<String, Long> comparatorStats() {
        Map<String, Long> stats = new TreeMap<>();
        stats.put("heuristic", comparatorHeuristicDecisions);
        stats.put("pushes", comparatorPushDecisions);
        stats.put("depth", comparatorDepthDecisions);
        stats.put("progress", comparatorProgressDecisions);
        stats.put("insertion", comparatorInsertionDecisions);
        return stats;
    }

    private static double percentage(long timeNs, long totalMs) {
        if (timeNs <= 0L) {
            return 0.0;
        }
        double totalNs = totalMs * 1_000_000.0;
        if (totalNs <= 0) {
            return 0.0;
        }
        return (timeNs / totalNs) * 100.0;
    }

    private static double percentile(int pct) {
        if (assignmentValues.isEmpty()) {
            return -1.0;
        }
        List<Integer> copy = new ArrayList<>(assignmentValues);
        Collections.sort(copy);
        int index = (int) Math.floor((pct / 100.0) * (copy.size() - 1));
        index = Math.max(0, Math.min(copy.size() - 1, index));
        return copy.get(index);
    }

    private static void addTiming(String key, long nanos) {
        timingTotals.merge(key, nanos, Long::sum);
        timingCounts.merge(key, 1L, Long::sum);
    }

    private static String toJson(Map<String, ?> map) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(entry.getKey()).append('"').append(':');
            appendValue(sb, entry.getValue());
        }
        sb.append('}');
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void appendValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            sb.append('"').append(((String) value).replace("\"", "\\\"")).append('"');
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value.toString());
        } else if (value instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                appendValue(sb, e.getKey().toString());
                sb.append(':');
                appendValue(sb, e.getValue());
            }
            sb.append('}');
        } else if (value instanceof Iterable) {
            sb.append('[');
            boolean first = true;
            for (Object item : (Iterable<?>) value) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                appendValue(sb, item);
            }
            sb.append(']');
        } else {
            sb.append('"').append(value.toString()).append('"');
        }
    }

    public static boolean zeroProximity() {
        return ENABLED && ZERO_PROXIMITY;
    }

    public static long now() {
        return ENABLED ? System.nanoTime() : 0L;
    }

    public static void recordFirstPushData(State state, State nextState) {
        // backward compatibility placeholder if future data is needed
    }

    public static void markHeuristicStart() {
        // placeholder hook for future use
    }

    public static long getExpandedStates() {
        return expandedStates;
    }

    public static long getOpenPeak() {
        return openPeak;
    }

    public static void noteHeuristicDuration(long duration) {
        addTiming("Heuristic.evaluate", duration);
    }
}
