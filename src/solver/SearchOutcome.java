package solver;

public final class SearchOutcome {
    private final String bestPlan;
    private final boolean bestPlanSolves;
    private final String bestCompletePlan;

    SearchOutcome(String bestPlan, boolean bestPlanSolves, String bestCompletePlan) {
        this.bestPlan = bestPlan;
        this.bestPlanSolves = bestPlanSolves;
        this.bestCompletePlan = bestCompletePlan;
    }

    public String getBestPlan() {
        return bestPlan;
    }

    public boolean bestPlanSolves() {
        return bestPlanSolves;
    }

    public String getBestCompletePlan() {
        return bestCompletePlan;
    }
}
