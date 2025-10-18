package solver;

public final class SearchStats {
    private long expandedStates;
    private int openPeak;
    private long regionPrePruned;
    private long regionPostPruned;
    private long cornerPruned;
    private long freezePruned;
    private long wallLinePruned;
    private long duplicatePruned;
    private long corridorSlides;
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
        this.regionPrePruned = other.regionPrePruned;
        this.regionPostPruned = other.regionPostPruned;
        this.cornerPruned = other.cornerPruned;
        this.freezePruned = other.freezePruned;
        this.wallLinePruned = other.wallLinePruned;
        this.duplicatePruned = other.duplicatePruned;
        this.corridorSlides = other.corridorSlides;
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
        this.regionPrePruned = 0L;
        this.regionPostPruned = 0L;
        this.cornerPruned = 0L;
        this.freezePruned = 0L;
        this.wallLinePruned = 0L;
        this.duplicatePruned = 0L;
        this.corridorSlides = 0L;
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

    void recordRegionPrePruned() {
        regionPrePruned++;
    }

    void recordRegionPostPruned() {
        regionPostPruned++;
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

    public long getExpandedStates() {
        return expandedStates;
    }

    public int getOpenPeak() {
        return openPeak;
    }

    public long getRegionPrePruned() {
        return regionPrePruned;
    }

    public long getRegionPostPruned() {
        return regionPostPruned;
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
        if (incumbent >= 0) {
            sb.append(" first_incumbent_ms=").append(incumbent);
        }
        sb.append(" region_pruned=").append(regionPrePruned);
        sb.append(" post_region_pruned=").append(regionPostPruned);
        sb.append(" corner_pruned=").append(cornerPruned);
        sb.append(" freeze_pruned=").append(freezePruned);
        sb.append(" wall_line_pruned=").append(wallLinePruned);
        sb.append(" duplicates=").append(duplicatePruned);
        sb.append(" corridor_slides=").append(corridorSlides);
        sb.append(" closed=").append(closedStates);
        sb.append(" limit_hit=").append(timeLimitHit);
        return sb.toString();
    }
}
