package solver;

import java.util.ArrayList;
import java.util.List;

public class SokoBot {
    private Coordinate player = null;
    private final List<Coordinate> boxList = new ArrayList<>();
    private final List<Coordinate> goalList = new ArrayList<>();
    private SearchStats lastStats = SearchStats.empty();

    public String solveSokobanPuzzle(int width, int height, char[][] mapData, char[][] itemsData) {
        player = null;
        boxList.clear();
        goalList.clear();

        extractMap(mapData, itemsData, height, width);

        Coordinate[] boxes = boxList.toArray(new Coordinate[0]);
        Coordinate[] goals = goalList.toArray(new Coordinate[0]);

        Heuristic.initialize(mapData, goals);
        State initial = State.initial(player, boxes, Heuristic.evaluate(player, boxes));

        GBFS solver = new GBFS(mapData, goals);
        SearchOutcome outcome = solver.search(initial);
        lastStats = solver.getStatistics();

        String planToReturn = outcome.getBestPlan();
        ReplayValidator.ValidationResult validation = ReplayValidator.validate(mapData, itemsData, planToReturn);
        if (!validation.fullyValid) {
            int idx = validation.lastValidIndex;
            planToReturn = idx >= 0 ? planToReturn.substring(0, idx + 1) : "";
        }

        if (!validation.solved && outcome.getBestCompletePlan() != null) {
            ReplayValidator.ValidationResult full = ReplayValidator.validate(mapData, itemsData, outcome.getBestCompletePlan());
            if (full.fullyValid && full.solved) {
                planToReturn = outcome.getBestCompletePlan();
            }
        }

        return planToReturn;
    }

    private void extractMap(char[][] mapData, char[][] itemsData, int height, int width) {
        boolean[][] goalSeen = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                char itemCell = itemsData[y][x];
                switch (itemCell) {
                    case Constants.PLAYER:
                    case Constants.PLAYER_ON_GOAL:
                        player = new Coordinate(x, y);
                        break;
                    case Constants.BOX:
                    case Constants.BOX_ON_GOAL:
                        boxList.add(new Coordinate(x, y));
                        break;
                    default:
                        break;
                }

                if (mapData[y][x] == Constants.GOAL || itemCell == Constants.BOX_ON_GOAL || itemCell == Constants.PLAYER_ON_GOAL) {
                    if (!goalSeen[y][x]) {
                        goalList.add(new Coordinate(x, y));
                        goalSeen[y][x] = true;
                    }
                }
            }
        }

        if (player == null) {
            throw new IllegalStateException("Map does not contain a player start position");
        }
    }

    public SearchStats getLastStats() {
        return lastStats;
    }
}
