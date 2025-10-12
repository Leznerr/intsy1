package solver;

import java.util.ArrayDeque;
import java.util.Arrays;
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
    private final boolean[][] visited;
    private final int[][] parentX;
    private final int[][] parentY;
    private final char[][] moveToHere;
    private final int[][] boxIndex;
    private final ArrayDeque<int[]> bfsQueue = new ArrayDeque<>();
    private final HashSet<Long> localSignatureBuffer = new HashSet<>();

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
        this.visited = new boolean[rows][cols];
        this.parentX = new int[rows][cols];
        this.parentY = new int[rows][cols];
        this.moveToHere = new char[rows][cols];
        this.boxIndex = new int[rows][cols];
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
        State bestFrontier = initial;

        while (!open.isEmpty()) {
            long now = System.nanoTime();
            if (now > deadline) {
                break;
            }
            State current = open.poll();
            stats.incrementExpanded();
            bestFrontier = betterFrontier(current, bestFrontier);

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

        State planState = bestGoal != null ? bestGoal : bestFrontier;
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

    private State betterFrontier(State candidate, State incumbent) {
        if (incumbent == null) {
            return candidate;
        }
        if (stateComparator.compare(candidate, incumbent) < 0) {
            return candidate;
        }
        return incumbent;
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
        visited[startY][startX] = true;
        parentX[startY][startX] = startX;
        parentY[startY][startX] = startY;
        moveToHere[startY][startX] = '\0';
        bfsQueue.clear();
        bfsQueue.add(new int[] {startY, startX});
        localSignatureBuffer.clear();

        while (!bfsQueue.isEmpty()) {
            if (System.nanoTime() > deadline) {
                return;
            }
            int[] cell = bfsQueue.removeFirst();
            int py = cell[0];
            int px = cell[1];
            considerPushesFrom(state, px, py, startX, startY, open, bestCosts);
            for (int dir = 0; dir < Constants.DIRECTION_X.length; dir++) {
                int nx = px + Constants.DIRECTION_X[dir];
                int ny = py + Constants.DIRECTION_Y[dir];
                if (!inBounds(nx, ny)) {
                    continue;
                }
                if (visited[ny][nx]) {
                    continue;
                }
                if (mapData[ny][nx] == Constants.WALL) {
                    continue;
                }
                if (boxIndex[ny][nx] != -1) {
                    continue;
                }
                visited[ny][nx] = true;
                parentX[ny][nx] = px;
                parentY[ny][nx] = py;
                moveToHere[ny][nx] = Constants.MOVES[dir];
                bfsQueue.add(new int[] {ny, nx});
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
            int boxIdx = boxIndex[boxY][boxX];
            if (boxIdx == -1) {
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
            if (boxIndex[destY][destX] != -1) {
                continue;
            }
            char[] path = reconstructPath(startX, startY, px, py);
            State walkTail = appendWalkChain(state, path);
            Coordinate[] updatedBoxes = new Coordinate[state.getBoxes().length];
            Coordinate[] currentBoxes = state.getBoxes();
            for (int i = 0; i < currentBoxes.length; i++) {
                Coordinate c = currentBoxes[i];
                if (i == boxIdx) {
                    updatedBoxes[i] = new Coordinate(destX, destY);
                } else {
                    updatedBoxes[i] = c;
                }
            }
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
        char[] buffer = new char[rows * cols];
        int length = 0;
        int cx = targetX;
        int cy = targetY;
        while (!(cx == startX && cy == startY)) {
            char move = moveToHere[cy][cx];
            buffer[length++] = move;
            int px = parentX[cy][cx];
            int py = parentY[cy][cx];
            cx = px;
            cy = py;
        }
        char[] path = new char[length];
        for (int i = 0; i < length; i++) {
            path[i] = buffer[length - 1 - i];
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
        for (int y = 0; y < rows; y++) {
            Arrays.fill(visited[y], false);
            Arrays.fill(parentX[y], -1);
            Arrays.fill(parentY[y], -1);
            Arrays.fill(moveToHere[y], '\0');
            Arrays.fill(boxIndex[y], -1);
        }
        Coordinate[] boxes = state.getBoxes();
        for (int i = 0; i < boxes.length; i++) {
            Coordinate b = boxes[i];
            if (inBounds(b.x, b.y)) {
                boxIndex[b.y][b.x] = i;
            }
        }
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
