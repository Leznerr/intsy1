package solver;

/**
 * Collects search metrics for a single GBFS invocation so callers can assess progress and
 * performance without digging through solver internals.
 */
public final class SearchStats {
    private long expandedStates;
    private long pushCandidates;
    private long enqueuedPushes;
    private long deadlockPruned;
    private long duplicatePruned;
    private int priorityQueuePeak;
    private long timeLimitNanos;
    private long startTimeNanos;
    private long endTimeNanos;
    private boolean timeLimitHit;
    private int bestPlanLength;

    SearchStats() {
        reset(0L);
    }

    private SearchStats(SearchStats other) {
        this.expandedStates = other.expandedStates;
        this.pushCandidates = other.pushCandidates;
        this.enqueuedPushes = other.enqueuedPushes;
        this.deadlockPruned = other.deadlockPruned;
        this.duplicatePruned = other.duplicatePruned;
        this.priorityQueuePeak = other.priorityQueuePeak;
        this.timeLimitNanos = other.timeLimitNanos;
        this.startTimeNanos = other.startTimeNanos;
        this.endTimeNanos = other.endTimeNanos;
        this.timeLimitHit = other.timeLimitHit;
        this.bestPlanLength = other.bestPlanLength;
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
        this.priorityQueuePeak = 0;
        this.timeLimitNanos = limitNanos;
        this.startTimeNanos = 0L;
        this.endTimeNanos = 0L;
        this.timeLimitHit = false;
        this.bestPlanLength = -1;
    }

    void markStart(long now) {
        this.startTimeNanos = now;
    }

    void markFinish(long now, boolean limitHit, int planLength) {
        this.endTimeNanos = now;
        this.timeLimitHit = limitHit;
        if (planLength >= 0) {
            this.bestPlanLength = planLength;
        }
    }

    void incrementExpanded() {
        this.expandedStates++;
    }

    void recordPushCandidate() {
        this.pushCandidates++;
    }

    void recordDeadlockPruned() {
        this.deadlockPruned++;
    }

    void recordDuplicatePruned() {
        this.duplicatePruned++;
    }

    void recordEnqueued(int queueSize) {
        this.enqueuedPushes++;
        if (queueSize > this.priorityQueuePeak) {
            this.priorityQueuePeak = queueSize;
        }
    }

    void updateBestPlanLength(int planLength) {
        if (planLength < 0) {
            return;
        }
        if (this.bestPlanLength == -1 || planLength < this.bestPlanLength) {
            this.bestPlanLength = planLength;
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

    public int getPriorityQueuePeak() {
        return priorityQueuePeak;
    }

    public boolean isTimeLimitHit() {
        return timeLimitHit;
    }

    public int getBestPlanLength() {
        return bestPlanLength;
    }

    public long getElapsedNanos() {
        if (endTimeNanos == 0L || startTimeNanos == 0L) {
            return 0L;
        }
        return endTimeNanos - startTimeNanos;
    }

    public double getElapsedSeconds() {
        return getElapsedNanos() / 1_000_000_000.0;
    }

    public double getTimeLimitSeconds() {
        return timeLimitNanos / 1_000_000_000.0;
    }
}
