package solver;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;

public final class GBFS {
    private static final long TIME_LIMIT_NANOS = Constants.TIME_BUDGET_MS * 1_000_000L;
    private static final char[] EMPTY_PATH = new char[0];

    private final char[][] mapData;
    private final Coordinate[] goalCoordinates;
    private final Deadlock deadlockDetector;
    private final SearchStats stats = new SearchStats();

    private final int rows;
    private final int cols;

    private final int[][] visitStamp;
    private final int[][] parentX;
    private final int[][] parentY;
    private final char[][] moveToHere;
    private int visitToken = 1;

    private final int[][] boxStamp;
    private final int[][] boxIds;
    private int boxToken = 1;

    private final int[] queueX;
    private final int[] queueY;

    private final HashSet<Long> localSignatureBuffer = new HashSet<>();

    private State bestFrontierCandidate;
    private State deepestFrontierCandidate;

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
        cmp = Integer.compare(a.getDepth(), b.getDepth());
        if (cmp != 0) {
            return cmp;
        }
        long distSumA = a.getGoalDistanceSquaredSum();
        long distSumB = b.getGoalDistanceSquaredSum();
        if (distSumA != distSumB) {
            return Long.compare(distSumA, distSumB);
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
        Diagnostics.markSearchStart();

        long signature = initial.getHash();
        bestCosts.put(signature, encodeCost(initial));
        open.add(initial);
        stats.recordOpenSize(open.size());

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

            if (current.isGoal(goalCoordinates)) {
                stats.recordFirstIncumbent(now);
                stats.markFinish(now, false, current.getDepth(), current.getPushes(), bestCosts.size());
                if (Diagnostics.ENABLED) {
                    Diagnostics.setSummary(stats.toSummaryString());
                }
                Diagnostics.markSearchFinish(true, false);
                String plan = current.reconstructPlan();
                return new SearchOutcome(plan, true, plan);
            }

            expand(current, open, bestCosts, deadline);
        }

        long finishTime = System.nanoTime();
        boolean limitHit = finishTime > deadline && !open.isEmpty();

        State fallback = selectFallbackState();
        String plan = fallback.reconstructPlan();

        stats.markFinish(finishTime, limitHit, fallback.getDepth(), fallback.getPushes(), bestCosts.size());
        if (Diagnostics.ENABLED) {
            Diagnostics.setSummary(stats.toSummaryString());
        }
        Diagnostics.markSearchFinish(false, limitHit);

        return new SearchOutcome(plan, false, null);
    }

    public SearchStats getStatistics() {
        return stats.snapshot();
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

            int boxIdx = boxIds[boxY][boxX];
            Coordinate[] parentBoxes = state.getBoxes();

            if (!deadlockDetector.regionHasGoalForMove(parentBoxes, boxIdx, destX, destY)) {
                if (!deadlockDetector.regionHasGoalIgnoringBoxes(destX, destY)) {
                    stats.recordRegionPrePruned();
                    continue;
                }
            }

            char[] prePushWalk = reconstructPath(startX, startY, px, py);
            Coordinate[] updatedBoxes = copyBoxes(parentBoxes);
            updatedBoxes[boxIdx] = new Coordinate(destX, destY);
            State pushState = State.push(state,
                    new Coordinate(boxX, boxY),
                    updatedBoxes,
                    Constants.MOVES[dir],
                    state.getHeuristic(),
                    prePushWalk);

            State slidState = slideAlongCorridor(pushState, dir);
            if (slidState == null) {
                continue;
            }

            State finalState = slidState;
            int movedIdx = finalState.getMovedBoxIndex();
            if (movedIdx < 0) {
                continue;
            }
            Coordinate moved = finalState.getBoxes()[movedIdx];

            if (!deadlockDetector.regionHasGoalForMove(finalState.getBoxes(), movedIdx, moved.x, moved.y)) {
                if (!deadlockDetector.regionHasGoalIgnoringBoxes(moved.x, moved.y)) {
                    stats.recordRegionPostPruned();
                    continue;
                }
            }
            if (!deadlockDetector.roomHasEnoughGoalsForMove(finalState.getBoxes(), movedIdx, moved.x, moved.y)) {
                stats.recordRegionPostPruned();
                continue;
            }
            if (!deadlockDetector.compHasEnoughGoalsForMove(finalState.getBoxes(), movedIdx, moved.x, moved.y)) {
                stats.recordRegionPostPruned();
                continue;
            }
            if (deadlockDetector.isCornerNoGoal(moved.x, moved.y)) {
                stats.recordCornerPruned();
                continue;
            }
            if (deadlockDetector.quickFrozenSquare(moved.x, moved.y, finalState.getBoxes())) {
                stats.recordFreezePruned();
                continue;
            }
            if (deadlockDetector.isWallLineFreeze(moved.x, moved.y, finalState.getBoxes())) {
                stats.recordWallLinePruned();
                continue;
            }
            if (deadlockDetector.isDeadlock(finalState)) {
                stats.recordFreezePruned();
                continue;
            }

            long childSignature = finalState.getHash();
            if (!localSignatureBuffer.add(childSignature)) {
                stats.recordDuplicatePruned();
                continue;
            }

            int heuristic = Heuristic.evaluate(finalState);
            if (heuristic == Integer.MAX_VALUE) {
                continue;
            }
            finalState = finalState.withHeuristic(heuristic);

            long encodedCost = encodeCost(finalState);
            Long previous = bestCosts.get(childSignature);
            if (previous != null && previous <= encodedCost) {
                stats.recordDuplicatePruned();
                continue;
            }
            bestCosts.put(childSignature, encodedCost);

            open.add(finalState);
            updateFrontierCandidates(finalState);
            stats.recordOpenSize(open.size());
        }
    }

    private State slideAlongCorridor(State baseState, int dir) {
        State current = baseState;
        int movedIdx = current.getMovedBoxIndex();
        if (movedIdx < 0) {
            return current;
        }
        while (true) {
            Coordinate moved = current.getBoxes()[movedIdx];
            if (mapData[moved.y][moved.x] == Constants.GOAL) {
                break;
            }
            if (!isCorridorCell(current, moved.x, moved.y, dir)) {
                break;
            }
            int nextX = moved.x + Constants.DIRECTION_X[dir];
            int nextY = moved.y + Constants.DIRECTION_Y[dir];
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
                if (!deadlockDetector.regionHasGoalIgnoringBoxes(nextX, nextY)) {
                    stats.recordRegionPostPruned();
                    break;
                }
            }
            Coordinate[] nextBoxes = copyBoxes(current.getBoxes());
            nextBoxes[movedIdx] = new Coordinate(nextX, nextY);
            current = State.push(current,
                    new Coordinate(moved.x, moved.y),
                    nextBoxes,
                    Constants.MOVES[dir],
                    current.getHeuristic(),
                    EMPTY_PATH);
            stats.recordCorridorSlide();
        }
        return current;
    }

    private boolean isCorridorCell(State state, int x, int y, int dir) {
        int leftDir = (dir + 1) % Constants.DIRECTION_X.length;
        int rightDir = (dir + 3) % Constants.DIRECTION_X.length;
        int leftX = x + Constants.DIRECTION_X[leftDir];
        int leftY = y + Constants.DIRECTION_Y[leftDir];
        int rightX = x + Constants.DIRECTION_X[rightDir];
        int rightY = y + Constants.DIRECTION_Y[rightDir];
        boolean leftBlocked = isWallOrOutOfBounds(leftX, leftY) || state.hasBoxAt(leftX, leftY);
        boolean rightBlocked = isWallOrOutOfBounds(rightX, rightY) || state.hasBoxAt(rightX, rightY);
        return leftBlocked && rightBlocked;
    }

    private char[] reconstructPath(int startX, int startY, int targetX, int targetY) {
        if (startX == targetX && startY == targetY) {
            return EMPTY_PATH;
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

    private boolean isWallOrOutOfBounds(int x, int y) {
        if (!inBounds(x, y)) {
            return true;
        }
        return mapData[y][x] == Constants.WALL;
    }

    private Coordinate[] copyBoxes(Coordinate[] boxes) {
        Coordinate[] copy = new Coordinate[boxes.length];
        for (int i = 0; i < boxes.length; i++) {
            Coordinate b = boxes[i];
            copy[i] = new Coordinate(b.x, b.y);
        }
        return copy;
    }

    private long encodeCost(State state) {
        long pushes = state.getPushes() & 0xffffffffL;
        long depth = state.getDepth() & 0xffffffffL;
        return (pushes << 32) | depth;
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
        if (candidate.getPushes() == deepestFrontierCandidate.getPushes()
                && candidate.getDepth() > deepestFrontierCandidate.getDepth()) {
            deepestFrontierCandidate = candidate;
        }
    }

    private State selectFallbackState() {
        if (deepestFrontierCandidate != null && deepestFrontierCandidate.getDepth() > 0) {
            return deepestFrontierCandidate;
        }
        if (bestFrontierCandidate != null) {
            return bestFrontierCandidate;
        }
        throw new IllegalStateException("Search exhausted without recording frontier state");
    }
}
