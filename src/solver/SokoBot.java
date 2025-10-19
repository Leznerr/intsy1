package solver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
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
    private static final long TOTAL_SOLVE_TIME_LIMIT_MS = 14_800L;

    public String solveSokobanPuzzle(int width, int height, char[][] mapData, char[][] itemsData) {
        long totalBudgetMs = Math.min(Constants.TIME_BUDGET_MS, TOTAL_SOLVE_TIME_LIMIT_MS);
        long totalBudgetNanos = totalBudgetMs * 1_000_000L;
        long solveStart = System.nanoTime();
        long deadline = solveStart + totalBudgetNanos;

        SearchStats aggregateStats = new SearchStats();
        aggregateStats.reset(totalBudgetNanos);
        aggregateStats.markStart(solveStart);

        char[][] workingItems = cloneItems(itemsData);
        StringBuilder combinedPlan = new StringBuilder();
        ReplayValidator.ValidationResult segmentValidation = null;
        Coordinate[] goalCoordinates = extractGoals(mapData, itemsData);
        Heuristic.initialize(mapData, goalCoordinates);
        int previousBoxesOnGoals = countBoxesOnGoals(workingItems, mapData);
        int previousPenalty = corridorPenalty(workingItems);
        HashSet<Integer> visitedBoards = new HashSet<>();
        visitedBoards.add(Arrays.deepHashCode(workingItems));

        while (true) {
            long now = System.nanoTime();
            if (now >= deadline) {
                break;
            }
            long remainingMs = Math.max(1L, (deadline - now) / 1_000_000L);
            SolutionSegment segment = runSingleSearch(width, height, mapData, workingItems, remainingMs);
            aggregateStats.accumulate(segment.stats);
            segmentValidation = segment.validation;

            char[][] finalBoard = workingItems;
            if (segmentValidation != null && segmentValidation.finalItems != null) {
                finalBoard = cloneItems(segmentValidation.finalItems);
            }
            int boxesOnGoals = countBoxesOnGoals(finalBoard, mapData);
            int penaltyAfter = corridorPenalty(finalBoard);
            boolean segmentSolved = segmentValidation != null && segmentValidation.solved;
            boolean improvedGoals = boxesOnGoals > previousBoxesOnGoals;
            boolean improvedPenalty = !improvedGoals && penaltyAfter < previousPenalty;
            boolean adopt = segmentSolved || (!segment.plan.isEmpty() && (improvedGoals || improvedPenalty));
            System.out.println("segment plan len=" + segment.plan.length()
                    + " solved=" + segmentSolved
                    + " boxes=" + boxesOnGoals
                    + " prevBoxes=" + previousBoxesOnGoals
                    + " penalty=" + penaltyAfter
                    + " prevPenalty=" + previousPenalty
                    + " improvedGoals=" + improvedGoals
                    + " improvedPenalty=" + improvedPenalty
                    + " adopt=" + adopt);
            int boardHash = Arrays.deepHashCode(finalBoard);
            if (adopt && !segmentSolved && !improvedGoals && visitedBoards.contains(boardHash)) {
                adopt = false;
            }
            if (!adopt) {
                break;
            }
            if (!segment.plan.isEmpty()) {
                combinedPlan.append(segment.plan);
            }
            workingItems = finalBoard;
            visitedBoards.add(boardHash);
            previousBoxesOnGoals = boxesOnGoals;
            previousPenalty = penaltyAfter;
            aggregateStats.maybeUpdateMaxBoxes(boxesOnGoals);
            if (improvedGoals) {
                aggregateStats.recordPass2Accepted();
            } else if (improvedPenalty) {
                aggregateStats.recordPass2NeutralAccepted();
                aggregateStats.recordMicroRun();
            }
            if (segmentSolved) {
                break;
            }
        }

        String finalPlan = combinedPlan.toString();
        ReplayValidator.ValidationResult finalValidation = ReplayValidator.validate(mapData, itemsData, finalPlan);
        long solveEnd = System.nanoTime();
        boolean limitHit = solveEnd > deadline && !finalValidation.solved;
        int finalBoxes = finalValidation.finalItems != null
                ? countBoxesOnGoals(finalValidation.finalItems, mapData)
                : previousBoxesOnGoals;
        aggregateStats.setFinalBoxes(finalBoxes);
        aggregateStats.markFinish(solveEnd, limitHit, finalPlan.length(), finalValidation.pushes, 0);

        lastStats = aggregateStats.snapshot();
        lastOutcome = new SearchOutcome(finalPlan, finalValidation.solved, finalValidation.solved ? finalPlan : null);

        return finalPlan;
    }

    private SolutionSegment runSingleSearch(int width,
                                            int height,
                                            char[][] mapData,
                                            char[][] itemsData,
                                            long timeBudgetMs) {
        player = null;
        boxList.clear();
        goalList.clear();
        lastOutcome = null;

        extractMap(mapData, itemsData, height, width);

        Coordinate[] boxes = boxList.toArray(new Coordinate[0]);
        Coordinate[] goals = goalList.toArray(new Coordinate[0]);

        Components.build(mapData, goals);
        Rooms.build(mapData, goals);

        long segmentStart = System.nanoTime();

        String bfsPlan = trySolveSmallPuzzle(mapData, boxes, player, goals);
        if (bfsPlan != null) {
            ReplayValidator.ValidationResult validation = ReplayValidator.validate(mapData, itemsData, bfsPlan);
            SearchStats stats = new SearchStats();
            long budgetNanos = Math.max(1L, timeBudgetMs) * 1_000_000L;
            stats.reset(budgetNanos);
            stats.markStart(segmentStart);
            long segmentEnd = System.nanoTime();
            stats.markFinish(segmentEnd, false, bfsPlan.length(), validation.pushes, 0);
            SearchOutcome outcome = new SearchOutcome(bfsPlan, validation.solved, validation.solved ? bfsPlan : null);
            return new SolutionSegment(bfsPlan, stats.snapshot(), outcome, validation);
        }

        Heuristic.initialize(mapData, goals);
        State initial = State.initial(player, boxes, Heuristic.evaluate(player, boxes));

        GBFS solver = new GBFS(mapData, goals, timeBudgetMs);
        SearchOutcome rawOutcome = solver.search(initial);
        SearchStats stats = solver.getStatistics();

        String planToReturn = rawOutcome.getBestPlan();
        if (planToReturn == null) {
            planToReturn = "";
        }

        ReplayValidator.ValidationResult validation = ReplayValidator.validate(mapData, itemsData, planToReturn);
        if (!validation.fullyValid) {
            int idx = validation.lastValidIndex;
            planToReturn = idx >= 0 ? planToReturn.substring(0, idx + 1) : "";
            validation = ReplayValidator.validate(mapData, itemsData, planToReturn);
        }

        if (!validation.solved && rawOutcome.getBestCompletePlan() != null) {
            String completePlan = rawOutcome.getBestCompletePlan();
            ReplayValidator.ValidationResult full = ReplayValidator.validate(mapData, itemsData, completePlan);
            if (full.fullyValid && full.solved) {
                planToReturn = completePlan;
                validation = full;
            }
        }

        SearchOutcome adjustedOutcome = new SearchOutcome(planToReturn, validation.solved,
                validation.solved ? planToReturn : rawOutcome.getBestCompletePlan());
        return new SolutionSegment(planToReturn, stats, adjustedOutcome, validation);
    }

    private static char[][] cloneItems(char[][] source) {
        if (source == null) {
            return new char[0][0];
        }
        char[][] copy = new char[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? new char[0] : source[i].clone();
        }
        return copy;
    }

    private static int countBoxesOnGoals(char[][] items, char[][] map) {
        if (items == null || map == null) {
            return 0;
        }
        int count = 0;
        int rows = Math.min(items.length, map.length);
        for (int y = 0; y < rows; y++) {
            int cols = Math.min(items[y].length, map[y].length);
            for (int x = 0; x < cols; x++) {
                char item = items[y][x];
                char mapCell = map[y][x];
                if (item == Constants.BOX_ON_GOAL) {
                    count++;
                } else if (item == Constants.BOX && mapCell == Constants.GOAL) {
                    count++;
                }
            }
        }
        return count;
    }

    private static final class SolutionSegment {
        final String plan;
        final SearchStats stats;
        final SearchOutcome outcome;
        final ReplayValidator.ValidationResult validation;

        SolutionSegment(String plan, SearchStats stats, SearchOutcome outcome, ReplayValidator.ValidationResult validation) {
            this.plan = plan;
            this.stats = stats;
            this.outcome = outcome;
            this.validation = validation;
        }
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

    private static Coordinate[] extractBoxes(char[][] items) {
        List<Coordinate> boxes = new ArrayList<>();
        for (int y = 0; y < items.length; y++) {
            for (int x = 0; x < items[y].length; x++) {
                char cell = items[y][x];
                if (cell == Constants.BOX || cell == Constants.BOX_ON_GOAL) {
                    boxes.add(new Coordinate(x, y));
                }
            }
        }
        return boxes.toArray(new Coordinate[0]);
    }

    private static Coordinate[] extractGoals(char[][] map, char[][] items) {
        List<Coordinate> goals = new ArrayList<>();
        int rows = map.length;
        int cols = rows == 0 ? 0 : map[0].length;
        boolean[][] seen = new boolean[rows][cols];
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                if (map[y][x] == Constants.GOAL
                        || items[y][x] == Constants.BOX_ON_GOAL
                        || items[y][x] == Constants.PLAYER_ON_GOAL) {
                    if (!seen[y][x]) {
                        goals.add(new Coordinate(x, y));
                        seen[y][x] = true;
                    }
                }
            }
        }
        return goals.toArray(new Coordinate[0]);
    }

    private static int corridorPenalty(char[][] items) {
        Coordinate[] boxes = extractBoxes(items);
        if (boxes.length == 0) {
            return 0;
        }
        return Heuristic.corridorEntrancePenalty(boxes);
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



