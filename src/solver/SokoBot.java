package solver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class SokoBot {
    private Coordinate player = null;
    private final List<Coordinate> boxList = new ArrayList<>();
    private final List<Coordinate> goalList = new ArrayList<>();
    private SearchStats lastStats = SearchStats.empty();
    private SearchOutcome lastOutcome = null;
    private static final int SMALL_PUZZLE_BOX_LIMIT = 4;

    public String solveSokobanPuzzle(int width, int height, char[][] mapData, char[][] itemsData) {
        player = null;
        boxList.clear();
        goalList.clear();
        lastOutcome = null;

        extractMap(mapData, itemsData, height, width);

        Coordinate[] boxes = boxList.toArray(new Coordinate[0]);
        Coordinate[] goals = goalList.toArray(new Coordinate[0]);

        Components.build(mapData, goals);
        Rooms.build(mapData, goals);

        String bfsPlan = trySolveSmallPuzzle(mapData, boxes, player, goals);
        if (bfsPlan != null) {
            lastStats = SearchStats.empty();
            lastOutcome = new SearchOutcome(bfsPlan, true, bfsPlan);
            return bfsPlan;
        }

        Heuristic.initialize(mapData, goals);
        State initial = State.initial(player, boxes, Heuristic.evaluate(player, boxes));

        GBFS solver = new GBFS(mapData, goals);
        SearchOutcome outcome = solver.search(initial);
        lastStats = solver.getStatistics();
        lastOutcome = outcome;

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

    public SearchOutcome getLastOutcome() {
        return lastOutcome;
    }

    private String trySolveSmallPuzzle(char[][] mapData, Coordinate[] boxes, Coordinate player, Coordinate[] goals) {
        if (boxes.length == 0) {
            return "";
        }
        if (boxes.length > SMALL_PUZZLE_BOX_LIMIT) {
            return null;
        }
        return breadthFirstSolve(mapData, player, boxes, goals);
    }

    private String breadthFirstSolve(char[][] mapData, Coordinate startPlayer, Coordinate[] startBoxes, Coordinate[] goals) {
        int rows = mapData.length;
        int cols = rows == 0 ? 0 : mapData[0].length;
        Queue<BfsNode> queue = new ArrayDeque<>();
        Coordinate[] initialBoxes = copyAndSort(startBoxes);
        long initialSignature = encodeState(startPlayer, initialBoxes);
        Set<Long> visited = new HashSet<>();
        visited.add(initialSignature);
        queue.add(new BfsNode(startPlayer, initialBoxes, ""));

        while (!queue.isEmpty()) {
            BfsNode node = queue.remove();
            if (isGoalState(node.boxes, goals)) {
                return node.path;
            }
            for (int dir = 0; dir < Constants.DIRECTION_X.length; dir++) {
                int nx = node.player.x + Constants.DIRECTION_X[dir];
                int ny = node.player.y + Constants.DIRECTION_Y[dir];
                if (!inBounds(nx, ny, cols, rows)) {
                    continue;
                }
                if (mapData[ny][nx] == Constants.WALL) {
                    continue;
                }
                int boxIdx = indexOfBox(node.boxes, nx, ny);
                if (boxIdx >= 0) {
                    int destX = nx + Constants.DIRECTION_X[dir];
                    int destY = ny + Constants.DIRECTION_Y[dir];
                    if (!inBounds(destX, destY, cols, rows)) {
                        continue;
                    }
                    if (mapData[destY][destX] == Constants.WALL) {
                        continue;
                    }
                    if (indexOfBox(node.boxes, destX, destY) >= 0) {
                        continue;
                    }
                    Coordinate[] nextBoxes = node.boxes.clone();
                    nextBoxes[boxIdx] = new Coordinate(destX, destY);
                    sortBoxes(nextBoxes);
                    Coordinate nextPlayer = new Coordinate(nx, ny);
                    long sig = encodeState(nextPlayer, nextBoxes);
                    if (visited.add(sig)) {
                        queue.add(new BfsNode(nextPlayer, nextBoxes, node.path + Constants.MOVES[dir]));
                    }
                } else {
                    Coordinate nextPlayer = new Coordinate(nx, ny);
                    long sig = encodeState(nextPlayer, node.boxes);
                    if (visited.add(sig)) {
                        queue.add(new BfsNode(nextPlayer, node.boxes, node.path + Constants.MOVES[dir]));
                    }
                }
            }
        }
        return null;
    }

    private static boolean inBounds(int x, int y, int cols, int rows) {
        return x >= 0 && x < cols && y >= 0 && y < rows;
    }

    private static int indexOfBox(Coordinate[] boxes, int x, int y) {
        for (int i = 0; i < boxes.length; i++) {
            Coordinate box = boxes[i];
            if (box.x == x && box.y == y) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isGoalState(Coordinate[] boxes, Coordinate[] goals) {
        for (Coordinate box : boxes) {
            boolean onGoal = false;
            for (Coordinate goal : goals) {
                if (box.x == goal.x && box.y == goal.y) {
                    onGoal = true;
                    break;
                }
            }
            if (!onGoal) {
                return false;
            }
        }
        return true;
    }

    private static long encodeState(Coordinate player, Coordinate[] boxes) {
        long hash = 1469598103934665603L;
        hash = (hash ^ player.x) * 1099511628211L;
        hash = (hash ^ player.y) * 1099511628211L;
        for (Coordinate box : boxes) {
            hash = (hash ^ box.x) * 1099511628211L;
            hash = (hash ^ box.y) * 1099511628211L;
        }
        return hash;
    }

    private static Coordinate[] copyAndSort(Coordinate[] boxes) {
        Coordinate[] copy = new Coordinate[boxes.length];
        for (int i = 0; i < boxes.length; i++) {
            Coordinate b = boxes[i];
            copy[i] = new Coordinate(b.x, b.y);
        }
        sortBoxes(copy);
        return copy;
    }

    private static void sortBoxes(Coordinate[] boxes) {
        java.util.Arrays.sort(boxes);
    }

    private static final class BfsNode {
        final Coordinate player;
        final Coordinate[] boxes;
        final String path;

        BfsNode(Coordinate player, Coordinate[] boxes, String path) {
            this.player = player;
            this.boxes = boxes;
            this.path = path;
        }
    }
}
