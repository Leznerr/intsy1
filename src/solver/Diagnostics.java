package solver;

public final class Diagnostics {
    private Diagnostics() {}

    public static final boolean ENABLED = false;
    public static final int MAX_LOG_LINES = 5_000;
    public static int SAMPLE_MASK = 0;
    public static boolean ZERO_PROXIMITY = false;

    private static int emittedLines = 0;
    private static boolean truncated = false;
    private static String pendingSummary = null;
    private static boolean finished = false;

    public static void resetForMap(String mapName) {
        emittedLines = 0;
        truncated = false;
        pendingSummary = null;
        finished = false;
        if (ENABLED) {
            log("== " + mapName + " ==");
        }
    }

    public static void markSearchStart() {
        // start banner handled by resetForMap
    }

    public static void markSearchFinish(boolean solvedFlag, boolean limitHitFlag) {
        if (!ENABLED) {
            return;
        }
        if (pendingSummary == null) {
            pendingSummary = "solved=" + solvedFlag + " limit_hit=" + limitHitFlag;
        }
        log(pendingSummary);
        pendingSummary = null;
        finished = true;
    }

    public static void setSummary(String summary) {
        if (!ENABLED) {
            return;
        }
        pendingSummary = summary;
    }

    public static void emitDiagnostics(long elapsedMillis, boolean solvedFlag, boolean limitHitFlag, long closedSize) {
        if (!ENABLED || finished) {
            return;
        }
        if (pendingSummary == null) {
            pendingSummary = "elapsed_ms=" + elapsedMillis
                    + " solved=" + solvedFlag
                    + " limit_hit=" + limitHitFlag
                    + " closed=" + closedSize;
        }
        log(pendingSummary);
        pendingSummary = null;
        finished = true;
    }

    private static void log(String message) {
        if (!ENABLED) {
            return;
        }
        if (emittedLines >= MAX_LOG_LINES) {
            if (!truncated) {
                System.out.println("[diagnostics] truncated after " + MAX_LOG_LINES + " lines");
                truncated = true;
            }
            emittedLines++;
            return;
        }
        System.out.println(message);
        emittedLines++;
        if (emittedLines >= MAX_LOG_LINES && !truncated) {
            System.out.println("[diagnostics] truncated after " + MAX_LOG_LINES + " lines");
            truncated = true;
        }
    }

    public static long now() {
        return System.nanoTime();
    }

    public static boolean zeroProximity() {
        return ZERO_PROXIMITY;
    }

    public static void recordAssignmentValue(int value) {
    }

    public static void recordHeuristicEvaluation(boolean infinite) {
    }

    public static void noteHeuristicDuration(long nanos) {
    }

    public static void recordAssignmentTime(long nanos) {
    }
}
