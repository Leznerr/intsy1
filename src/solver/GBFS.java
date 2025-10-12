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
                break;
            }

            expand(current, open, bestCosts, deadline);
        }

        long finishTime = System.nanoTime();
        boolean limitHit = finishTime > deadline && !open.isEmpty();

        State fallback = selectFallbackState(bestGoal);
        State planState = fallback;
        String plan = planState.reconstructPlan();
        boolean solved = bestGoal != null;
        String completePlan = solved ? plan : null;

        stats.markFinish(finishTime,
                limitHit,
                planState.getDepth(),
                planState.getPushes(),
                bestCosts.size());

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
            char[] path = reconstructPath(startX, startY, px, py);
            State walkTail = appendWalkChain(state, path);
            Coordinate[] updatedBoxes = state.getBoxes().clone();
            updatedBoxes[boxIdx] = new Coordinate(destX, destY);
            Coordinate nextPlayer = new Coordinate(boxX, boxY);
            stats.recordPushCandidate();
            int heuristic = Heuristic.evaluate(nextPlayer, updatedBoxes);
            if (heuristic >= Integer.MAX_VALUE) {
                continue;
            }
            State pushState = State.push(walkTail, nextPlayer, updatedBoxes, Constants.MOVES[dir], heuristic);
            long signature = pushState.getHash();
            if (!localSignatureBuffer.add(signature)) {
                stats.recordDuplicatePruned();
                continue;
            }
            if (deadlockDetector.isDeadlock(pushState)) {
                stats.recordDeadlockPruned();
                continue;
            }
            long encodedCost = encodeCost(pushState);
            Long previous = bestCosts.get(signature);
            if (previous != null && !isBetterCost(encodedCost, previous)) {
                stats.recordDuplicatePruned();
                continue;
            }
            bestCosts.put(signature, encodedCost);
            open.add(pushState);
            updateFrontierCandidates(pushState);
            stats.recordEnqueued(open.size());
        }
    }

    private State appendWalkChain(State origin, char[] path) {
        State tail = origin;
        int px = origin.getPlayer().x;
        int py = origin.getPlayer().y;
        for (char move : path) {
            int dir = directionIndex(move);
            px += Constants.DIRECTION_X[dir];
            py += Constants.DIRECTION_Y[dir];
            tail = State.walk(tail, new Coordinate(px, py), move);
        }
        return tail;
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

    private int directionIndex(char move) {
        switch (move) {
            case 'u':
                return Constants.UP;
            case 'd':
                return Constants.DOWN;
            case 'l':
                return Constants.LEFT;
            case 'r':
                return Constants.RIGHT;
            default:
                throw new IllegalArgumentException("Unknown move: " + move);
        }
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
}
