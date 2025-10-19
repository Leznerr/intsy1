package solver;

public final class SearchStats {
    private long expandedStates;
    private int openPeak;
    private long regionPruned;
    private long cornerPruned;
    private long freezePruned;
    private long wallLinePruned;
    private long duplicatePruned;
    private long corridorSlides;
    private int maxBoxesOnGoals;
    private int finalBoxesOnGoals;
    private long progressTiebreakHits;
    private long pass2Accepted;
    private long pass2NeutralAccepted;
    private long pass2Rejected;
    private long microRuns;
    private long startTimeNanos;
    private long finishTimeNanos;
    private long firstIncumbentNanos = -1L;
    private boolean timeLimitHit;
    private long closedStates;
    private int bestPlanLength = -1;
    private int bestPlanPushes = -1;
    private long timeLimitNanos;

    public SearchStats() {
        reset(0L);
    }

    private SearchStats(SearchStats other) {
        this.expandedStates = other.expandedStates;
        this.openPeak = other.openPeak;
        this.regionPruned = other.regionPruned;
        this.cornerPruned = other.cornerPruned;
        this.freezePruned = other.freezePruned;
        this.wallLinePruned = other.wallLinePruned;
        this.duplicatePruned = other.duplicatePruned;
        this.corridorSlides = other.corridorSlides;
        this.maxBoxesOnGoals = other.maxBoxesOnGoals;
        this.finalBoxesOnGoals = other.finalBoxesOnGoals;
        this.progressTiebreakHits = other.progressTiebreakHits;
        this.pass2Accepted = other.pass2Accepted;
        this.pass2NeutralAccepted = other.pass2NeutralAccepted;
        this.pass2Rejected = other.pass2Rejected;
        this.microRuns = other.microRuns;
        this.startTimeNanos = other.startTimeNanos;
        this.finishTimeNanos = other.finishTimeNanos;
        this.firstIncumbentNanos = other.firstIncumbentNanos;
        this.timeLimitHit = other.timeLimitHit;
        this.closedStates = other.closedStates;
        this.bestPlanLength = other.bestPlanLength;
        this.bestPlanPushes = other.bestPlanPushes;
        this.timeLimitNanos = other.timeLimitNanos;
    }

    static SearchStats empty() {
        return new SearchStats();
    }

    SearchStats snapshot() {
        return new SearchStats(this);
    }

    void reset(long limitNanos) {
        this.expandedStates = 0L;
        this.openPeak = 0;
        this.regionPruned = 0L;
        this.cornerPruned = 0L;
        this.freezePruned = 0L;
        this.wallLinePruned = 0L;
        this.duplicatePruned = 0L;
        this.corridorSlides = 0L;
        this.maxBoxesOnGoals = 0;
        this.finalBoxesOnGoals = 0;
        this.progressTiebreakHits = 0L;
        this.pass2Accepted = 0L;
        this.pass2NeutralAccepted = 0L;
        this.pass2Rejected = 0L;
        this.microRuns = 0L;
        this.startTimeNanos = 0L;
        this.finishTimeNanos = 0L;
        this.firstIncumbentNanos = -1L;
        this.timeLimitHit = false;
        this.closedStates = 0L;
        this.bestPlanLength = -1;
        this.bestPlanPushes = -1;
        this.timeLimitNanos = limitNanos;
    }

    void markStart(long now) {
        this.startTimeNanos = now;
    }

    void markFinish(long now, boolean limitHit, int planLength, int planPushes, long closedSize) {
        this.finishTimeNanos = now;
        this.timeLimitHit = limitHit;
        this.closedStates = closedSize;
        this.bestPlanLength = planLength;
        this.bestPlanPushes = planPushes;
    }

    void recordFirstIncumbent(long now) {
        if (firstIncumbentNanos < 0L) {
            firstIncumbentNanos = now;
        }
    }

    void incrementExpanded() {
        expandedStates++;
    }

    void recordOpenSize(int openSize) {
        if (openSize > openPeak) {
            openPeak = openSize;
        }
    }

    void recordRegionPruned() {
        regionPruned++;
    }

    void recordRegionPrePruned() {
        recordRegionPruned();
    }

    void recordRegionPostPruned() {
        recordRegionPruned();
    }

    void recordCornerPruned() {
        cornerPruned++;
    }

    void recordFreezePruned() {
        freezePruned++;
    }

    void recordWallLinePruned() {
        wallLinePruned++;
    }

    void recordDuplicatePruned() {
        duplicatePruned++;
    }

    void recordCorridorSlide() {
        corridorSlides++;
    }

    void accumulate(SearchStats other) {
        if (other == null) {
            return;
        }
        this.expandedStates += other.expandedStates;
        if (other.openPeak > this.openPeak) {
            this.openPeak = other.openPeak;
        }
        this.regionPruned += other.regionPruned;
        this.cornerPruned += other.cornerPruned;
        this.freezePruned += other.freezePruned;
        this.wallLinePruned += other.wallLinePruned;
        this.duplicatePruned += other.duplicatePruned;
        this.corridorSlides += other.corridorSlides;
        if (other.maxBoxesOnGoals > this.maxBoxesOnGoals) {
            this.maxBoxesOnGoals = other.maxBoxesOnGoals;
        }
        if (other.finalBoxesOnGoals > this.finalBoxesOnGoals) {
            this.finalBoxesOnGoals = other.finalBoxesOnGoals;
        }
        this.progressTiebreakHits += other.progressTiebreakHits;
        this.pass2Accepted += other.pass2Accepted;
        this.pass2NeutralAccepted += other.pass2NeutralAccepted;
        this.pass2Rejected += other.pass2Rejected;
        this.microRuns += other.microRuns;
        if (this.firstIncumbentNanos < 0L
                || (other.firstIncumbentNanos >= 0L && other.firstIncumbentNanos < this.firstIncumbentNanos)) {
            this.firstIncumbentNanos = other.firstIncumbentNanos;
        }
        this.timeLimitHit = this.timeLimitHit || other.timeLimitHit;
        this.closedStates += other.closedStates;
    }

    public long getExpandedStates() {
        return expandedStates;
    }

    public int getOpenPeak() {
        return openPeak;
    }

    public long getRegionPruned() {
        return regionPruned;
    }

    public long getCornerPruned() {
        return cornerPruned;
    }

    public long getFreezePruned() {
        return freezePruned;
    }

    public long getWallLinePruned() {
        return wallLinePruned;
    }

    public long getDuplicatePruned() {
        return duplicatePruned;
    }

    public long getCorridorSlides() {
        return corridorSlides;
    }

    void recordBoxesOnGoalsCandidate(int value) {
        if (value > maxBoxesOnGoals) {
            maxBoxesOnGoals = value;
        }
    }

    void maybeUpdateMaxBoxes(int value) {
        recordBoxesOnGoalsCandidate(value);
    }

    void recordProgressTiebreakHit() {
        progressTiebreakHits++;
    }

    void recordPass2Accepted() {
        pass2Accepted++;
    }

    void recordPass2NeutralAccepted() {
        pass2NeutralAccepted++;
    }

    void recordPass2Rejected() {
        pass2Rejected++;
    }

    void recordMicroRun() {
        microRuns++;
    }

    void setFinalBoxes(int value) {
        finalBoxesOnGoals = value;
    }

    public long getElapsedMillis() {
        if (startTimeNanos == 0L || finishTimeNanos == 0L || finishTimeNanos < startTimeNanos) {
            return 0L;
        }
        return (finishTimeNanos - startTimeNanos) / 1_000_000L;
    }

    public long getFirstIncumbentMillis() {
        if (firstIncumbentNanos < 0L || startTimeNanos == 0L) {
            return -1L;
        }
        return (firstIncumbentNanos - startTimeNanos) / 1_000_000L;
    }

    public boolean isTimeLimitHit() {
        return timeLimitHit;
    }

    public long getClosedStates() {
        return closedStates;
    }

    public int getBestPlanLength() {
        return bestPlanLength;
    }

    public int getBestPlanPushes() {
        return bestPlanPushes;
    }

    public String toSummaryString() {
        StringBuilder sb = new StringBuilder();
        sb.append("elapsed_ms=").append(getElapsedMillis());
        sb.append(" expanded=").append(expandedStates);
        sb.append(" open_peak=").append(openPeak);
        long incumbent = getFirstIncumbentMillis();
        sb.append(" first_incumbent_ms=").append(incumbent);
        sb.append(" region_pruned=").append(regionPruned);
        sb.append(" corner_pruned=").append(cornerPruned);
        sb.append(" freeze_pruned=").append(freezePruned);
        sb.append(" wall_line_pruned=").append(wallLinePruned);
        sb.append(" duplicates=").append(duplicatePruned);
        sb.append(" corridor_slides=").append(corridorSlides);
        sb.append(" limit_hit=").append(timeLimitHit);
        sb.append(" boxes_on_goals_max=").append(maxBoxesOnGoals);
        sb.append(" final_boxes=").append(finalBoxesOnGoals);
        sb.append(" progress_tiebreak_hits=").append(progressTiebreakHits);
        sb.append(" pass2_acc=").append(pass2Accepted);
        sb.append(" pass2_neutral=").append(pass2NeutralAccepted);
        sb.append(" pass2_rej=").append(pass2Rejected);
        sb.append(" micro_runs=").append(microRuns);
        return sb.toString();
    }
}
