package solver;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;

public class GBFS {
    private static final long TIME_LIMIT_NANOS = 14_000_000_000L;
    private static final long FNV_OFFSET = 1469598103934665603L;
    private static final long FNV_PRIME = 1099511628211L;

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
    private final ArrayDeque<int[]> coordinatePool = new ArrayDeque<>();
    private final HashSet<Long> localSignatureBuffer = new HashSet<>();
    private final Comparator<State> stateComparator = (a, b) -> {
        int cmp = Integer.compare(a.getCachedHeuristic(), b.getCachedHeuristic());
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

        cmp = compareLexicographically(a, b);
        if (cmp != 0) {
            return cmp;
        }

        return Long.compare(a.getStateId(), b.getStateId());
    };

    public GBFS(char[][] mapData, Coordinate[] goalCoordinates){
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

    public String search(State initial){
        if (initial.getCachedHeuristic() == Integer.MAX_VALUE) {
            initial.setCachedHeuristic(Heuristic.evaluate(initial));
        }

        PriorityQueue<State> open = new PriorityQueue<>(stateComparator);
        Map<Long, Long> bestCosts = new HashMap<>();

        long startTime = System.nanoTime();
        stats.reset(TIME_LIMIT_NANOS);
        stats.markStart(startTime);
        String bestPlan = "";
        int bestPlanLength = Integer.MAX_VALUE;

        long initialSignature = computeSignature(initial);
        bestCosts.put(initialSignature, encodeCost(initial));
        open.add(initial);
        stats.recordEnqueued(open.size());

        boolean timeLimitHit = false;
        while (!open.isEmpty()){
            if (System.nanoTime() - startTime >= TIME_LIMIT_NANOS) {
                timeLimitHit = true;
                break;
            }

            State current = open.poll();
            stats.incrementExpanded();
            long signature = computeSignature(current);
            long encodedCost = encodeCost(current);
            Long recorded = bestCosts.get(signature);
            if (recorded != null && recorded.longValue() != encodedCost) {
                stats.recordDuplicatePruned();
                continue;
            }

            if (current.isGoal(goalCoordinates)){
                String plan = current.reconstructMoves();
                if (plan.length() < bestPlanLength) {
                    bestPlan = plan;
                    bestPlanLength = plan.length();
                }
                stats.updateBestPlanLength(plan.length());
                continue;
            }

            expand(current, open, bestCosts);
        }

        int recordedPlanLength = (bestPlanLength == Integer.MAX_VALUE) ? -1 : bestPlanLength;
        stats.markFinish(System.nanoTime(), timeLimitHit, recordedPlanLength);
        return bestPlanLength == Integer.MAX_VALUE ? "" : bestPlan;
    }

    private void expand(State state, PriorityQueue<State> open, Map<Long, Long> bestCosts) {
        resetWorkingArrays();
        markBoxes(state);

        int startX = state.playerCoordinate.x;
        int startY = state.playerCoordinate.y;

        visited[startY][startX] = true;
        parentX[startY][startX] = startX;
        parentY[startY][startX] = startY;
        moveToHere[startY][startX] = '\0';

        enqueueCell(startY, startX);
        localSignatureBuffer.clear();

        while (!bfsQueue.isEmpty()) {
            int[] cell = bfsQueue.removeFirst();
            int py = cell[0];
            int px = cell[1];

            considerPushesFrom(state, px, py, startX, startY, open, bestCosts);

            for (int dir = 0; dir < Constants.DIRECTION_X.length; dir++) {
                int nx = px + Constants.DIRECTION_X[dir];
                int ny = py + Constants.DIRECTION_Y[dir];

                if (nx < 0 || nx >= cols || ny < 0 || ny >= rows) {
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
                enqueueCell(ny, nx);
            }

            releaseCell(cell);
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

            if (boxX < 0 || boxX >= cols || boxY < 0 || boxY >= rows) {
                continue;
            }

            int idx = boxIndex[boxY][boxX];
            if (idx == -1) {
                continue;
            }
        }

        Queue<int[]> queue = new ArrayDeque<>();
        List<int[]> reachable = new ArrayList<>();

        int startX = state.playerCoordinate.x;
        int startY = state.playerCoordinate.y;
        queue.add(new int[]{startY, startX});
        visited[startY][startX] = true;
        parentX[startY][startX] = startX;
        parentY[startY][startX] = startY;
        reachable.add(new int[]{startY, startX});

        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            int cy = cur[0];
            int cx = cur[1];

            for (int dir = 0; dir < Constants.DIRECTION_X.length; dir++) {
                int nx = cx + Constants.DIRECTION_X[dir];
                int ny = cy + Constants.DIRECTION_Y[dir];

                if (nx < 0 || nx >= cols || ny < 0 || ny >= rows) {
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

            int destX = boxX + Constants.DIRECTION_X[dir];
            int destY = boxY + Constants.DIRECTION_Y[dir];

            if (destX < 0 || destX >= cols || destY < 0 || destY >= rows) {
                continue;
            }
            if (mapData[destY][destX] == Constants.WALL) {
                continue;
            }
            if (boxIndex[destY][destX] != -1) {
                continue;
            }

            char[] path = reconstructPath(parentX, parentY, moveToHere, startX, startY, px, py);
            State tail = buildWalkChain(state, path);

            Coordinate[] newBoxes = new Coordinate[state.boxCoordinates.length];
            for (int b = 0; b < state.boxCoordinates.length; b++) {
                Coordinate original = state.boxCoordinates[b];
                if (b == idx) {
                    newBoxes[b] = new Coordinate(destX, destY);
                } else {
                    newBoxes[b] = new Coordinate(original.x, original.y);
                }
            }

            Coordinate newPlayer = new Coordinate(boxX, boxY);
            char pushMove = Constants.MOVES[dir];
            stats.recordPushCandidate();
            State pushState = tail.spawnPushState(newPlayer, newBoxes, pushMove);

            long stateSignature = computeSignature(pushState);
            if (!localSignatureBuffer.add(stateSignature)) {
                stats.recordDuplicatePruned();
                continue;
            }

            if (deadlockDetector.isDeadlock(pushState)) {
                stats.recordDeadlockPruned();
                continue;
            }

            pushState.setCachedHeuristic(Heuristic.evaluate(pushState));

            long encodedCost = encodeCost(pushState);
            Long previous = bestCosts.get(stateSignature);
            if (previous != null && !isBetterCost(encodedCost, previous)) {
                stats.recordDuplicatePruned();
                continue;
            }

            bestCosts.put(stateSignature, encodedCost);
            open.add(pushState);
            stats.recordEnqueued(open.size());
        }
    }

    private void resetWorkingArrays() {
        for (int y = 0; y < rows; y++) {
            java.util.Arrays.fill(visited[y], false);
            java.util.Arrays.fill(parentX[y], -1);
            java.util.Arrays.fill(parentY[y], -1);
            java.util.Arrays.fill(moveToHere[y], '\0');
            java.util.Arrays.fill(boxIndex[y], -1);
        }

        while (!bfsQueue.isEmpty()) {
            coordinatePool.addLast(bfsQueue.removeFirst());
        }
    }

    private void markBoxes(State state) {
        for (int i = 0; i < state.boxCoordinates.length; i++) {
            Coordinate b = state.boxCoordinates[i];
            if (b.y >= 0 && b.y < rows && b.x >= 0 && b.x < cols) {
                boxIndex[b.y][b.x] = i;
            }
        }
    }

    private void enqueueCell(int y, int x) {
        int[] cell = coordinatePool.pollFirst();
        if (cell == null) {
            cell = new int[2];
        }
        cell[0] = y;
        cell[1] = x;
        bfsQueue.addLast(cell);
    }

    private void releaseCell(int[] cell) {
        coordinatePool.addLast(cell);
    }

    private State buildWalkChain(State origin, char[] moves) {
        State tail = origin;
        int px = origin.playerCoordinate.x;
        int py = origin.playerCoordinate.y;
        for (char move : moves) {
            int dir = moveToIndex(move);
            px += Constants.DIRECTION_X[dir];
            py += Constants.DIRECTION_Y[dir];
            tail = tail.spawnWalkState(new Coordinate(px, py), move);
        }
        return tail;
    }

    private int moveToIndex(char move) {
        for (int i = 0; i < Constants.MOVES.length; i++) {
            if (Constants.MOVES[i] == move) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unknown move: " + move);
    }

    private char[] reconstructPath(int[][] parentX, int[][] parentY, char[][] moveToHere, int startX, int startY, int targetX, int targetY) {
        if (targetX == startX && targetY == startY) {
            return new char[0];
        }

        java.util.ArrayList<Character> reversed = new java.util.ArrayList<>();
        int cx = targetX;
        int cy = targetY;
        while (!(cx == startX && cy == startY)) {
            char move = moveToHere[cy][cx];
            reversed.add(move);
            int px = parentX[cy][cx];
            int py = parentY[cy][cx];
            cx = px;
            cy = py;
        }

        char[] path = new char[reversed.size()];
        for (int i = 0; i < reversed.size(); i++) {
            path[i] = reversed.get(reversed.size() - 1 - i);
        }
        return path;
    }

    private long computeSignature(State state) {
        long hash = FNV_OFFSET;
        hash = (hash ^ state.playerCoordinate.x) * FNV_PRIME;
        hash = (hash ^ state.playerCoordinate.y) * FNV_PRIME;
        for (Coordinate box : state.boxCoordinates) {
            long mixed = ((long) box.x << 32) ^ (box.y & 0xffffffffL);
            hash = (hash ^ mixed) * FNV_PRIME;
        }
        return hash;
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

    private int compareLexicographically(State a, State b) {
        String movesA = a.reconstructMoves();
        String movesB = b.reconstructMoves();
        int min = Math.min(movesA.length(), movesB.length());
        for (int i = 0; i < min; i++) {
            int cmp = Character.compare(movesA.charAt(i), movesB.charAt(i));
            if (cmp != 0) {
                return cmp;
            }
        }
        return Integer.compare(movesA.length(), movesB.length());
    }

    public SearchStats getStatistics() {
        return stats.snapshot();
    }
}