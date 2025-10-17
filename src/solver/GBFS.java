package solver;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;

public final class GBFS {
    private static final long TIME_LIMIT_NANOS = Constants.TIME_BUDGET_MS * 1_000_000L;

    private final char[][] mapData;
    private final Coordinate[] goalCoordinates;
    private final Deadlock deadlockDetector;
    private final SearchStats stats = new SearchStats();
    private final int rows;
    private final int cols;
    private final int[][] visitStamp;
    private int visitToken = 1;
    private final int[][] parentX;
    private final int[][] parentY;
    private final char[][] moveToHere;
    private final int[][] boxStamp;
    private final int[][] boxIds;
    private int boxToken = 1;
    private final int[] queueX;
    private final int[] queueY;
    private final HashSet<Long> localSignatureBuffer = new HashSet<>();
    private State bestFrontierCandidate;
    private State deepestFrontierCandidate;
    private long regionPrunedCount;
    private static final char[] EMPTY_PATH = new char[0];

    private final Comparator<State> stateComparator = (a, b) -> {
        int cmp = Integer.compare(a.getFCost(), b.getFCost());
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(a.getHeuristic(), b.getHeuristic());
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(a.getPushes(), b.getPushes());
        if (cmp != 0) {
            return cmp;
        }
        cmp = Character.compare(a.getLastMove(), b.getLastMove());
        if (cmp != 0) {
            return cmp;
        }
        return Long.compare(a.getInsertionId(), b.getInsertionId());
    };

    public GBFS(char[][] mapData, Coordinate[] goalCoordinates) {
        this.mapData = mapData;
        this.goalCoordinates = goalCoordinates;
        this.deadlockDetector = new Deadlock(mapData, goalCoordinates);
        this.rows = mapData.length;
        this.cols = rows == 0 ? 0 : mapData[0].length;
        this.visitStamp = new int[rows][cols];
        this.parentX = new int[rows][cols];
        this.parentY = new int[rows][cols];
        this.moveToHere = new char[rows][cols];
        this.boxStamp = new int[rows][cols];
        this.boxIds = new int[rows][cols];
        this.queueX = new int[rows * cols];
        this.queueY = new int[rows * cols];
    }

    public SearchOutcome search(State initial) {
        PriorityQueue<State> open = new PriorityQueue<>(stateComparator);
        Map<Long, Long> bestCosts = new HashMap<>();

        long startTime = System.nanoTime();
        long deadline = startTime + TIME_LIMIT_NANOS;
        stats.reset(TIME_LIMIT_NANOS);
        stats.markStart(startTime);
        regionPrunedCount = 0L;

        long initialSignature = initial.getHash();
        bestCosts.put(initialSignature, encodeCost(initial));
        open.add(initial);

        State bestGoal = initial.isGoal(goalCoordinates) ? initial : null;
        bestFrontierCandidate = initial;
        deepestFrontierCandidate = initial;

        while (!open.isEmpty()) {
            long now = System.nanoTime();
            if (now > deadline) {
                break;
            }
            State current = open.poll();
            stats.incrementExpanded();
            updateFrontierCandidates(current);

            long signature = current.getHash();
            long encoded = encodeCost(current);
            Long recorded = bestCosts.get(signature);
            if (recorded != null && recorded.longValue() != encoded) {
                continue;
            }

            if (current.isGoal(goalCoordinates)) {
                if (bestGoal == null || isBetterGoal(current, bestGoal)) {
                    bestGoal = current;
                    stats.updateBestPlanLength(current.getDepth(), current.getPushes());
                }
                long finishTime = System.nanoTime();
                stats.markFinish(finishTime,
                        false,
                        current.getDepth(),
                        current.getPushes(),
                        bestCosts.size());
                String plan = current.reconstructPlan();
                return new SearchOutcome(plan, true, plan);
            }

            expand(current, open, bestCosts, deadline);
        }

        long finishTime = System.nanoTime();
        boolean limitHit = finishTime > deadline && !open.isEmpty();

        State planState = selectFallbackState(bestGoal);
        String plan = planState.reconstructPlan();
        boolean solved = bestGoal != null;
        String completePlan = solved ? plan : null;

        stats.markFinish(finishTime,
                limitHit,
                planState.getDepth(),
                planState.getPushes(),
                bestCosts.size());

        System.out.println("region pruned: " + regionPrunedCount);
        return new SearchOutcome(plan, solved, completePlan);
    }

    public SearchStats getStatistics() {
        return stats.snapshot();
    }

    private void updateFrontierCandidates(State candidate) {
        if (candidate == null) {
            return;
        }
        if (bestFrontierCandidate == null || stateComparator.compare(candidate, bestFrontierCandidate) < 0) {
            bestFrontierCandidate = candidate;
        }
        if (deepestFrontierCandidate == null) {
            deepestFrontierCandidate = candidate;
            return;
        }
        if (candidate.getPushes() > deepestFrontierCandidate.getPushes()) {
            deepestFrontierCandidate = candidate;
            return;
        }
        if (candidate.getPushes() == deepestFrontierCandidate.getPushes()) {
            if (candidate.getDepth() > deepestFrontierCandidate.getDepth()) {
                deepestFrontierCandidate = candidate;
                return;
            }
            if (candidate.getDepth() == deepestFrontierCandidate.getDepth()
                    && stateComparator.compare(candidate, deepestFrontierCandidate) < 0) {
                deepestFrontierCandidate = candidate;
            }
        }
    }

    private State selectFallbackState(State bestGoal) {
        if (bestGoal != null) {
            return bestGoal;
        }
        if (deepestFrontierCandidate != null && deepestFrontierCandidate.getDepth() > 0) {
            return deepestFrontierCandidate;
        }
        if (bestFrontierCandidate != null) {
            return bestFrontierCandidate;
        }
        throw new IllegalStateException("Search did not record any frontier state");
    }

    private boolean isBetterGoal(State candidate, State incumbent) {
        if (candidate.getPushes() != incumbent.getPushes()) {
            return candidate.getPushes() < incumbent.getPushes();
        }
        if (candidate.getDepth() != incumbent.getDepth()) {
            return candidate.getDepth() < incumbent.getDepth();
        }
        return stateComparator.compare(candidate, incumbent) < 0;
    }

    private void expand(State state,
                        PriorityQueue<State> open,
                        Map<Long, Long> bestCosts,
                        long deadline) {
        resetWorkingArrays(state);
        int startX = state.getPlayer().x;
        int startY = state.getPlayer().y;
        markVisited(startX, startY);
        parentX[startY][startX] = startX;
        parentY[startY][startX] = startY;
        moveToHere[startY][startX] = '\0';
        int head = 0;
        int tail = 0;
        queueX[tail] = startX;
        queueY[tail] = startY;
        tail++;
        localSignatureBuffer.clear();

        while (head < tail) {
            if (System.nanoTime() > deadline) {
                return;
            }
            int px = queueX[head];
            int py = queueY[head];
            head++;
            considerPushesFrom(state, px, py, startX, startY, open, bestCosts);
            for (int dir = 0; dir < Constants.DIRECTION_X.length; dir++) {
                int nx = px + Constants.DIRECTION_X[dir];
                int ny = py + Constants.DIRECTION_Y[dir];
                if (!inBounds(nx, ny)) {
                    continue;
                }
                if (isVisited(nx, ny)) {
                    continue;
                }
                if (mapData[ny][nx] == Constants.WALL) {
                    continue;
                }
                if (hasBoxAt(nx, ny)) {
                    continue;
                }
                markVisited(nx, ny);
                parentX[ny][nx] = px;
                parentY[ny][nx] = py;
                moveToHere[ny][nx] = Constants.MOVES[dir];
                queueX[tail] = nx;
                queueY[tail] = ny;
                tail++;
            }
        }
    }

    private void considerPushesFrom(State state,
                                    int px,
                                    int py,
                                    int startX,
                                    int startY,
                                    PriorityQueue<State> open,
                                    Map<Long, Long> bestCosts) {
        for (int dir = 0; dir < Constants.DIRECTION_X.length; dir++) {
            int boxX = px + Constants.DIRECTION_X[dir];
            int boxY = py + Constants.DIRECTION_Y[dir];
            if (!inBounds(boxX, boxY)) {
                continue;
            }
            if (!hasBoxAt(boxX, boxY)) {
                continue;
            }
            int boxIdx = boxIds[boxY][boxX];
            int destX = boxX + Constants.DIRECTION_X[dir];
            int destY = boxY + Constants.DIRECTION_Y[dir];
            if (!inBounds(destX, destY)) {
                continue;
            }
            if (mapData[destY][destX] == Constants.WALL) {
                continue;
            }
            if (hasBoxAt(destX, destY)) {
                continue;
            }
            boolean strictRegionOk = deadlockDetector.regionHasGoalForMove(state.getBoxes(), boxIdx, destX, destY);
            if (!strictRegionOk) {
                // fall back to the loose goal-region test that ignores other boxes
                if (!deadlockDetector.regionHasGoalIgnoringBoxes(destX, destY)) {
                    regionPrunedCount++;
                    continue;
                }
            }
            char[] prePushWalk = reconstructPath(startX, startY, px, py);
            Coordinate[] updatedBoxes = state.getBoxes().clone();
            updatedBoxes[boxIdx] = new Coordinate(destX, destY);
            Coordinate nextPlayer = new Coordinate(boxX, boxY);
            stats.recordPushCandidate();
            State pushState = State.push(state, nextPlayer, updatedBoxes, Constants.MOVES[dir], state.getHeuristic(), prePushWalk);
            State finalState = slideAlongCorridor(pushState, dir);
            int movedIdx = finalState.getMovedBoxIndex();
            if (movedIdx >= 0) {
                Coordinate movedBox = finalState.getBoxes()[movedIdx];
                if (!deadlockDetector.regionHasGoalForMove(finalState.getBoxes(), movedIdx, movedBox.x, movedBox.y)) {
                    regionPrunedCount++;
                    continue;
                }
                if (deadlockDetector.isWallLineFreeze(movedBox.x, movedBox.y, finalState.getBoxes())) {
                    stats.recordDeadlockPruned();
                    continue;
                }
            }
            long signature = finalState.getHash();
            if (!localSignatureBuffer.add(signature)) {
                stats.recordDuplicatePruned();
                continue;
            }
            if (deadlockDetector.isDeadlock(finalState)) {
                stats.recordDeadlockPruned();
                continue;
            }
            int heuristic = Heuristic.evaluate(finalState);
            if (heuristic == Integer.MAX_VALUE) {
                continue;
            }
            finalState = finalState.withHeuristic(heuristic);
            long encodedCost = encodeCost(finalState);
            Long previous = bestCosts.get(signature);
            if (previous != null && !isBetterCost(encodedCost, previous)) {
                stats.recordDuplicatePruned();
                continue;
            }
            bestCosts.put(signature, encodedCost);
            open.add(finalState);
            updateFrontierCandidates(finalState);
            stats.recordEnqueued(open.size());
        }
    }

    private State slideAlongCorridor(State baseState, int dir) {
        State current = baseState;
        while (true) {
            int movedIdx = current.getMovedBoxIndex();
            if (movedIdx < 0) {
                break;
            }
            Coordinate box = current.getBoxes()[movedIdx];
            if (mapData[box.y][box.x] == Constants.GOAL) {
                break;
            }
            if (!isCorridorCell(box.x, box.y, dir)) {
                break;
            }
            int nextX = box.x + Constants.DIRECTION_X[dir];
            int nextY = box.y + Constants.DIRECTION_Y[dir];
            if (!inBounds(nextX, nextY)) {
                break;
            }
            if (mapData[nextY][nextX] == Constants.WALL) {
                break;
            }
            if (current.hasBoxAt(nextX, nextY)) {
                break;
            }
            if (!deadlockDetector.regionHasGoalForMove(current.getBoxes(), movedIdx, nextX, nextY)) {
                break;
            }
            Coordinate nextPlayer = new Coordinate(box.x, box.y);
            Coordinate[] nextBoxes = current.getBoxes().clone();
            nextBoxes[movedIdx] = new Coordinate(nextX, nextY);
            State nextState = State.push(current, nextPlayer, nextBoxes, Constants.MOVES[dir], current.getHeuristic(), EMPTY_PATH);
            if (deadlockDetector.isDeadlock(nextState)) {
                break;
            }
            current = nextState;
        }
        return current;
    }

    private boolean isCorridorCell(int x, int y, int dir) {
        if (dir == Constants.UP || dir == Constants.DOWN) {
            return isWallOrOutOfBounds(x - 1, y) && isWallOrOutOfBounds(x + 1, y);
        }
        return isWallOrOutOfBounds(x, y - 1) && isWallOrOutOfBounds(x, y + 1);
    }

    private char[] reconstructPath(int startX, int startY, int targetX, int targetY) {
        if (startX == targetX && startY == targetY) {
            return new char[0];
        }
        int length = 0;
        int cx = targetX;
        int cy = targetY;
        while (!(cx == startX && cy == startY)) {
            length++;
            int px = parentX[cy][cx];
            int py = parentY[cy][cx];
            cx = px;
            cy = py;
        }
        char[] path = new char[length];
        cx = targetX;
        cy = targetY;
        for (int idx = length - 1; idx >= 0; idx--) {
            char move = moveToHere[cy][cx];
            path[idx] = move;
            int px = parentX[cy][cx];
            int py = parentY[cy][cx];
            cx = px;
            cy = py;
        }
        return path;
    }

    private void resetWorkingArrays(State state) {
        visitToken++;
        if (visitToken == Integer.MAX_VALUE) {
            for (int y = 0; y < rows; y++) {
                java.util.Arrays.fill(visitStamp[y], 0);
            }
            visitToken = 1;
        }
        boxToken++;
        if (boxToken == Integer.MAX_VALUE) {
            for (int y = 0; y < rows; y++) {
                java.util.Arrays.fill(boxStamp[y], 0);
            }
            boxToken = 1;
        }
        Coordinate[] boxes = state.getBoxes();
        for (int i = 0; i < boxes.length; i++) {
            Coordinate b = boxes[i];
            if (inBounds(b.x, b.y)) {
                boxStamp[b.y][b.x] = boxToken;
                boxIds[b.y][b.x] = i;
            }
        }
    }

    private void markVisited(int x, int y) {
        visitStamp[y][x] = visitToken;
    }

    private boolean isVisited(int x, int y) {
        return visitStamp[y][x] == visitToken;
    }

    private boolean hasBoxAt(int x, int y) {
        return inBounds(x, y) && boxStamp[y][x] == boxToken;
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && x < cols && y >= 0 && y < rows;
    }

    private long encodeCost(State state) {
        return (((long) state.getPushes()) << 32) | (state.getDepth() & 0xffffffffL);
    }

    private boolean isBetterCost(long candidate, long existing) {
        long candPushes = candidate >>> 32;
        long candDepth = candidate & 0xffffffffL;
        long existPushes = existing >>> 32;
        long existDepth = existing & 0xffffffffL;
        if (candPushes < existPushes) {
            return true;
        }
        if (candPushes > existPushes) {
            return false;
        }
        return candDepth < existDepth;
    }

    private boolean isWallOrOutOfBounds(int x, int y) {
        if (!inBounds(x, y)) {
            return true;
        }
        return mapData[y][x] == Constants.WALL;
    }

}
