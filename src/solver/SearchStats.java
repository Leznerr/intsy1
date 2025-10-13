package solver;

public final class SearchStats {
    private long expandedStates;
    private long pushCandidates;
    private long enqueuedPushes;
    private long deadlockPruned;
    private long duplicatePruned;
    private int openPeak;
    private long closedStates;
    private long timeLimitNanos;
    private long startTimeNanos;
    private long endTimeNanos;
    private boolean timeLimitHit;
    private int bestPlanLength;
    private int bestPlanPushes;
    private long regionPruned;
    private long lineFreezePruned;
    private long cornerDeadlockCount;
    private long twoByTwoDeadlockCount;
    private long wallLineDeadlockCount;
    private long firstGoalNanos;

    SearchStats() {
        reset(0L);
    }

    private SearchStats(SearchStats other) {
        this.expandedStates = other.expandedStates;
        this.pushCandidates = other.pushCandidates;
        this.enqueuedPushes = other.enqueuedPushes;
        this.deadlockPruned = other.deadlockPruned;
        this.duplicatePruned = other.duplicatePruned;
        this.openPeak = other.openPeak;
        this.closedStates = other.closedStates;
        this.timeLimitNanos = other.timeLimitNanos;
        this.startTimeNanos = other.startTimeNanos;
        this.endTimeNanos = other.endTimeNanos;
        this.timeLimitHit = other.timeLimitHit;
        this.bestPlanLength = other.bestPlanLength;
        this.bestPlanPushes = other.bestPlanPushes;
        this.regionPruned = other.regionPruned;
        this.lineFreezePruned = other.lineFreezePruned;
        this.cornerDeadlockCount = other.cornerDeadlockCount;
        this.twoByTwoDeadlockCount = other.twoByTwoDeadlockCount;
        this.wallLineDeadlockCount = other.wallLineDeadlockCount;
        this.firstGoalNanos = other.firstGoalNanos;
    }

    static SearchStats empty() {
        return new SearchStats();
    }

    SearchStats snapshot() {
        return new SearchStats(this);
    }

    void reset(long limitNanos) {
        this.expandedStates = 0L;
        this.pushCandidates = 0L;
        this.enqueuedPushes = 0L;
        this.deadlockPruned = 0L;
        this.duplicatePruned = 0L;
        this.openPeak = 0;
        this.closedStates = 0L;
        this.timeLimitNanos = limitNanos;
        this.startTimeNanos = 0L;
        this.endTimeNanos = 0L;
        this.timeLimitHit = false;
        this.bestPlanLength = -1;
        this.bestPlanPushes = -1;
        this.regionPruned = 0L;
        this.lineFreezePruned = 0L;
        this.cornerDeadlockCount = 0L;
        this.twoByTwoDeadlockCount = 0L;
        this.wallLineDeadlockCount = 0L;
        this.firstGoalNanos = -1L;
    }

    void markStart(long now) {
        this.startTimeNanos = now;
    }

    void markFinish(long now, boolean limitHit, int planLength, int planPushes, long closedSize) {
        this.endTimeNanos = now;
        this.timeLimitHit = limitHit;
        if (planLength >= 0) {
            this.bestPlanLength = planLength;
            this.bestPlanPushes = planPushes;
            if (this.firstGoalNanos == -1L && this.startTimeNanos != 0L) {
                this.firstGoalNanos = now - this.startTimeNanos;
            }
        }
        this.closedStates = closedSize;
    }

    void incrementExpanded() {
        if (Constants.DEBUG_METRICS) {
            this.expandedStates++;
        }
    }

    void recordPushCandidate() {
        if (Constants.DEBUG_METRICS) {
            this.pushCandidates++;
        }
    }

    void recordDeadlockPruned() {
        if (Constants.DEBUG_METRICS) {
            this.deadlockPruned++;
        }
    }

    void recordDuplicatePruned() {
        if (Constants.DEBUG_METRICS) {
            this.duplicatePruned++;
        }
    }

    void recordEnqueued(int openSize) {
        if (Constants.DEBUG_METRICS) {
            this.enqueuedPushes++;
            if (openSize > this.openPeak) {
                this.openPeak = openSize;
            }
        }
    }

    void recordRegionPruned() {
        if (Constants.DEBUG_METRICS) {
            this.regionPruned++;
        }
    }

    void recordLineFreezePruned() {
        if (Constants.DEBUG_METRICS) {
            this.lineFreezePruned++;
        }
    }

    void recordFirstGoal(long now) {
        if (this.firstGoalNanos != -1L) {
            return;
        }
        if (this.startTimeNanos == 0L) {
            return;
        }
        this.firstGoalNanos = now - this.startTimeNanos;
    }

    void captureDeadlockCounts(long corner, long twoByTwo, long wallLine) {
        if (!Constants.DEBUG_METRICS) {
            return;
        }
        this.cornerDeadlockCount = corner;
        this.twoByTwoDeadlockCount = twoByTwo;
        this.wallLineDeadlockCount = wallLine;
    }

    void updateBestPlanLength(int planLength, int pushes) {
        if (!Constants.DEBUG_METRICS) {
            return;
        }
        if (planLength < 0) {
            return;
        }
        if (this.bestPlanLength == -1 || planLength < this.bestPlanLength) {
            this.bestPlanLength = planLength;
            this.bestPlanPushes = pushes;
        }
    }

    public long getExpandedStates() {
        return expandedStates;
    }

    public long getPushCandidates() {
        return pushCandidates;
    }

    public long getEnqueuedPushes() {
        return enqueuedPushes;
    }

    public long getDeadlockPruned() {
        return deadlockPruned;
    }

    public long getDuplicatePruned() {
        return duplicatePruned;
    }

    public int getOpenPeak() {
        return openPeak;
    }

    public long getClosedStates() {
        return closedStates;
    }

    public boolean isTimeLimitHit() {
        return timeLimitHit;
    }

    public int getBestPlanLength() {
        return bestPlanLength;
    }

    public int getBestPlanPushes() {
        return bestPlanPushes;
    }

    public long getElapsedNanos() {
        if (endTimeNanos == 0L || startTimeNanos == 0L) {
            return 0L;
        }
        return endTimeNanos - startTimeNanos;
    }

    public long getElapsedMillis() {
        return getElapsedNanos() / 1_000_000L;
    }

    public double getElapsedSeconds() {
        return getElapsedNanos() / 1_000_000_000.0;
    }

    public double getTimeLimitSeconds() {
        return timeLimitNanos / 1_000_000_000.0;
    }

    public long getRegionPruned() {
        return regionPruned;
    }

    public long getLineFreezePruned() {
        return lineFreezePruned;
    }

    public long getCornerDeadlockCount() {
        return cornerDeadlockCount;
    }

    public long getTwoByTwoDeadlockCount() {
        return twoByTwoDeadlockCount;
    }

    public long getWallLineDeadlockCount() {
        return wallLineDeadlockCount;
    }

    public long getFirstGoalMillis() {
        if (firstGoalNanos < 0L) {
            return -1L;
        }
        return firstGoalNanos / 1_000_000L;
    }
}
