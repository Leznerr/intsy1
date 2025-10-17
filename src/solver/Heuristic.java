package solver;

import java.util.Arrays;

public final class Heuristic {
    private static final int INF = 1_000_000;
    private static int[][][] goalDistanceGrids = new int[0][][];
    private static int[][] minToAnyGoal = new int[0][0];
    private static Coordinate[] cachedGoals = new Coordinate[0];
    private static int rows;
    private static int cols;
    private static char[][] cachedMap = new char[0][0];
    private static int[] dpCurrent = new int[0];
    private static int[] dpNext = new int[0];
    private static int dpLimit = 0;
    private static int[][] reusableCost = new int[0][0];
    private static int[] u = new int[0];
    private static int[] v = new int[0];
    private static int[] p = new int[0];
    private static int[] way = new int[0];
    private static int[] minv = new int[0];
    private static boolean[] used = new boolean[0];
    private static int[][] regionStamp = new int[0][0];
    private static int regionToken = 1;
    private static int[][] occupancyStamp = new int[0][0];
    private static int occupancyToken = 1;
    private static int[] regionQueueX = new int[0];
    private static int[] regionQueueY = new int[0];
    private static int[] bfsQueueX = new int[0];
    private static int[] bfsQueueY = new int[0];

    private Heuristic() {}

    public static synchronized void initialize(char[][] mapData, Coordinate[] goals) {
        if (mapData == null || goals == null) {
            cachedGoals = new Coordinate[0];
            goalDistanceGrids = new int[0][][];
            cachedMap = new char[0][0];
            minToAnyGoal = new int[0][0];
            rows = cols = 0;
            dpCurrent = new int[0];
            dpNext = new int[0];
            dpLimit = 0;
            reusableCost = new int[0][0];
            u = new int[0];
            v = new int[0];
            p = new int[0];
            way = new int[0];
            minv = new int[0];
            used = new boolean[0];
            regionStamp = new int[0][0];
            occupancyStamp = new int[0][0];
            regionQueueX = new int[0];
            regionQueueY = new int[0];
            bfsQueueX = new int[0];
            bfsQueueY = new int[0];
            regionToken = 1;
            occupancyToken = 1;
            AssignCache.clear();
            return;
        }

        AssignCache.clear();
        rows = mapData.length;
        cols = rows > 0 ? mapData[0].length : 0;
        cachedMap = new char[rows][cols];
        for (int y = 0; y < rows; y++) {
            System.arraycopy(mapData[y], 0, cachedMap[y], 0, cols);
        }
        ensureRegionStructures(rows, cols);
        ensureBfsCapacity(rows * cols);

        cachedGoals = goals.clone();
        goalDistanceGrids = new int[cachedGoals.length][rows][cols];
        for (int g = 0; g < cachedGoals.length; g++) {
            fillWithInf(goalDistanceGrids[g]);
            bfsFromGoal(g);
        }
        buildMinToAnyGoal();
        ensureCostCapacity(cachedGoals.length);
        ensureDpCapacity(cachedGoals.length);
    }

    public static int evaluate(State state) {
        if (state == null) {
            return Integer.MAX_VALUE;
        }
        return evaluate(state.getPlayer(), state.getBoxes());
    }

    public static int evaluate(Coordinate player, Coordinate[] boxes) {
        if (boxes.length == 0 || goalDistanceGrids.length == 0) {
            if (Diagnostics.ENABLED) {
                Diagnostics.recordAssignmentValue(0);
                Diagnostics.recordHeuristicEvaluation(false);
            }
            return 0;
        }
        int boxCount = boxes.length;
        int goalCount = goalDistanceGrids.length;
        if (goalCount < boxCount) {
            if (Diagnostics.ENABLED) {
                Diagnostics.recordAssignmentValue(Integer.MAX_VALUE);
                Diagnostics.recordHeuristicEvaluation(true);
            }
            return Integer.MAX_VALUE;
        }
        Coordinate[] sortedBoxes = boxes.clone();
        Arrays.sort(sortedBoxes);
        final Coordinate[] key = sortedBoxes;
        final int bc = boxCount;
        final int gc = goalCount;
        long evalStart = Diagnostics.now();
        int assignment = AssignCache.getOrCompute(key, () -> assignmentLowerBound(key, bc, gc));
        boolean inf = assignment >= INF;
        if (Diagnostics.ENABLED) {
            int recordedValue = inf ? Integer.MAX_VALUE : assignment;
            Diagnostics.recordAssignmentValue(recordedValue);
            Diagnostics.recordHeuristicEvaluation(inf);
            Diagnostics.noteHeuristicDuration(System.nanoTime() - evalStart);
        }
        if (inf) {
            return Integer.MAX_VALUE;
        }
        int proximity = Diagnostics.zeroProximity() ? 0 : estimatePlayerProximity(player, boxes);
        return assignment + proximity;
    }

    private static void fillWithInf(int[][] grid) {
        for (int y = 0; y < grid.length; y++) {
            Arrays.fill(grid[y], INF);
        }
    }

    private static void bfsFromGoal(int goalIndex) {
        Coordinate goal = cachedGoals[goalIndex];
        if (!inBounds(goal.x, goal.y)) {
            return;
        }
        int[][] dist = goalDistanceGrids[goalIndex];
        dist[goal.y][goal.x] = 0;
        int head = 0;
        int tail = 0;
        bfsQueueX[tail] = goal.x;
        bfsQueueY[tail] = goal.y;
        tail++;
        while (head < tail) {
            int cx = bfsQueueX[head];
            int cy = bfsQueueY[head];
            head++;
            int base = dist[cy][cx];
            for (int dir = 0; dir < Constants.DIRECTION_X.length; dir++) {
                int nx = cx + Constants.DIRECTION_X[dir];
                int ny = cy + Constants.DIRECTION_Y[dir];
                if (!inBounds(nx, ny)) {
                    continue;
                }
                if (cachedMap[ny][nx] == Constants.WALL) {
                    continue;
                }
                if (dist[ny][nx] > base + 1) {
                    dist[ny][nx] = base + 1;
                    bfsQueueX[tail] = nx;
                    bfsQueueY[tail] = ny;
                    tail++;
                }
            }
        }
    }

    private static void buildMinToAnyGoal() {
        if (rows <= 0 || cols <= 0) {
            minToAnyGoal = new int[0][0];
            return;
        }
        minToAnyGoal = new int[rows][cols];
        for (int y = 0; y < rows; y++) {
            Arrays.fill(minToAnyGoal[y], INF);
        }
        for (int g = 0; g < goalDistanceGrids.length; g++) {
            int[][] dist = goalDistanceGrids[g];
            for (int y = 0; y < rows; y++) {
                int[] distRow = dist[y];
                int[] minRow = minToAnyGoal[y];
                for (int x = 0; x < cols; x++) {
                    int value = distRow[x];
                    if (value < minRow[x]) {
                        minRow[x] = value;
                    }
                }
            }
        }
    }

    private static boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && y < rows && x < cols;
    }

    private static int estimatePlayerProximity(Coordinate player, Coordinate[] boxes) {
        if (player == null) {
            return 0;
        }
        int best = INF;
        for (Coordinate box : boxes) {
            int dist = Math.abs(player.x - box.x) + Math.abs(player.y - box.y);
            if (dist < best) {
                best = dist;
            }
        }
        return best == INF ? 0 : best;
    }

    private static int assignmentLowerBound(Coordinate[] boxes, int boxCount, int goalCount) {
        long start = Diagnostics.now();
        if (boxCount == 0) {
            if (Diagnostics.ENABLED) {
                Diagnostics.recordAssignmentTime(System.nanoTime() - start);
            }
            return 0;
        }
        if (goalCount == 0) {
            if (Diagnostics.ENABLED) {
                Diagnostics.recordAssignmentTime(System.nanoTime() - start);
            }
            return Integer.MAX_VALUE;
        }
        if (boxCount > goalCount) {
            if (Diagnostics.ENABLED) {
                Diagnostics.recordAssignmentTime(System.nanoTime() - start);
            }
            return Integer.MAX_VALUE;
        }
        if (goalCount <= 15) {
            int value = assignWithBitmask(boxes, boxCount, goalCount);
            if (Diagnostics.ENABLED) {
                Diagnostics.recordAssignmentTime(System.nanoTime() - start);
            }
            return value;
        }
        int size = goalCount;
        ensureCostCapacity(size);
        for (int i = 0; i < size; i++) {
            Arrays.fill(reusableCost[i], 0, size, 0);
        }
        for (int b = 0; b < boxCount; b++) {
            Coordinate box = boxes[b];
            if (!inBounds(box.x, box.y)) {
                return Integer.MAX_VALUE;
            }
            Arrays.fill(reusableCost[b], 0, size, INF);
            boolean reachable = false;
            for (int g = 0; g < goalCount; g++) {
                int dist = goalDistanceGrids[g][box.y][box.x];
                if (dist < INF) {
                    int cost = dist * dist;
                    reusableCost[b][g] = cost;
                    reachable = true;
                }
            }
            if (!reachable) {
                if (Diagnostics.ENABLED) {
                    Diagnostics.recordAssignmentTime(System.nanoTime() - start);
                }
                return Integer.MAX_VALUE;
            }
        }
        int result = hungarian(reusableCost, size);
        if (Diagnostics.ENABLED) {
            Diagnostics.recordAssignmentTime(System.nanoTime() - start);
        }
        return result;
    }

    public static int lastPushProgress(State state) {
        if (state == null) {
            return 0;
        }
        return computeDistanceDelta(state);
    }

    private static int assignWithBitmask(Coordinate[] boxes, int boxCount, int goalCount) {
        ensureDpCapacity(goalCount);
        int limit = dpLimit;
        Arrays.fill(dpCurrent, 0, limit, INF);
        dpCurrent[0] = 0;
        int[] current = dpCurrent;
        int[] next = dpNext;
        for (int b = 0; b < boxCount; b++) {
            Arrays.fill(next, 0, limit, INF);
            Coordinate box = boxes[b];
            if (!inBounds(box.x, box.y)) {
                return Integer.MAX_VALUE;
            }
            boolean reachable = false;
            for (int mask = 0; mask < limit; mask++) {
                int base = current[mask];
                if (base >= INF) {
                    continue;
                }
                for (int g = 0; g < goalCount; g++) {
                    if ((mask & (1 << g)) != 0) {
                        continue;
                    }
                    int dist = goalDistanceGrids[g][box.y][box.x];
                    if (dist >= INF) {
                        continue;
                    }
                    reachable = true;
                    int nextMask = mask | (1 << g);
                    int cost = dist * dist;
                    int candidate = base + cost;
                    if (candidate < next[nextMask]) {
                        next[nextMask] = candidate;
                    }
                }
            }
            if (!reachable) {
                return Integer.MAX_VALUE;
            }
            int[] temp = current;
            current = next;
            next = temp;
        }
        int best = INF;
        int required = boxCount;
        for (int mask = 0; mask < limit; mask++) {
            if (Integer.bitCount(mask) == required) {
                int value = current[mask];
                if (value < best) {
                    best = value;
                }
            }
        }
        return best;
    }

    private static void ensureCostCapacity(int size) {
        if (size <= 0) {
            reusableCost = new int[0][0];
            ensureHungarianCapacity(0);
            return;
        }
        if (reusableCost.length < size || reusableCost[0].length < size) {
            reusableCost = new int[size][size];
        }
        ensureHungarianCapacity(size);
    }

    private static void ensureHungarianCapacity(int size) {
        int length = size + 1;
        if (u.length < length) {
            u = new int[length];
            v = new int[length];
            p = new int[length];
            way = new int[length];
            minv = new int[length];
            used = new boolean[length];
        }
    }

    private static void ensureDpCapacity(int goalCount) {
        if (goalCount < 0 || goalCount > 20) {
            goalCount = Math.min(Math.max(goalCount, 0), 20);
        }
        int limit = goalCount == 0 ? 1 : 1 << goalCount;
        if (dpCurrent.length < limit) {
            dpCurrent = new int[limit];
            dpNext = new int[limit];
        }
        dpLimit = limit;
    }

    private static void ensureRegionStructures(int r, int c) {
        if (r <= 0 || c <= 0) {
            regionStamp = new int[0][0];
            occupancyStamp = new int[0][0];
            regionQueueX = new int[0];
            regionQueueY = new int[0];
            ensureBfsCapacity(0);
            regionToken = 1;
            occupancyToken = 1;
            return;
        }
        if (regionStamp.length != r || regionStamp[0].length != c) {
            regionStamp = new int[r][c];
            regionToken = 1;
        }
        if (occupancyStamp.length != r || occupancyStamp[0].length != c) {
            occupancyStamp = new int[r][c];
            occupancyToken = 1;
        }
        int capacity = r * c;
        if (regionQueueX.length < capacity) {
            regionQueueX = new int[capacity];
            regionQueueY = new int[capacity];
        }
    }

    private static void ensureBfsCapacity(int capacity) {
        if (capacity <= 0) {
            bfsQueueX = new int[0];
            bfsQueueY = new int[0];
            return;
        }
        if (bfsQueueX.length < capacity) {
            bfsQueueX = new int[capacity];
            bfsQueueY = new int[capacity];
        }
    }

    private static int computeDistanceDelta(State state) {
        if (state == null || !state.wasPush()) {
            return 0;
        }
        int movedIdx = state.getMovedBoxIndex();
        if (movedIdx < 0) {
            return 0;
        }
        Coordinate current = state.getBoxes()[movedIdx];
        Coordinate previous = findPreviousLocation(state);
        if (current == null || previous == null) {
            return 0;
        }
        int currentDist = nearestGoalDistance(current.x, current.y);
        int previousDist = nearestGoalDistance(previous.x, previous.y);
        if (previousDist >= INF) {
            return 0;
        }
        if (currentDist >= INF) {
            return -previousDist;
        }
        return previousDist - currentDist;
    }

    public static int nearestGoalDistance(int x, int y) {
        if (!inBounds(x, y) || minToAnyGoal.length == 0) {
            return INF;
        }
        return minToAnyGoal[y][x];
    }

    private static Coordinate findPreviousLocation(State state) {
        if (state == null) {
            return null;
        }
        State parent = state.getParent();
        if (parent == null) {
            return null;
        }
        Coordinate[] before = parent.getBoxes();
        Coordinate[] after = state.getBoxes();
        outer:
        for (Coordinate candidate : before) {
            for (Coordinate current : after) {
                if (candidate.x == current.x && candidate.y == current.y) {
                    continue outer;
                }
            }
            return new Coordinate(candidate.x, candidate.y);
        }
        return null;
    }

    private static void markOccupancy(Coordinate[] boxes, int skipIdx) {
        if (rows <= 0 || cols <= 0) {
            return;
        }
        advanceOccupancyToken();
        for (int i = 0; i < boxes.length; i++) {
            if (i == skipIdx) {
                continue;
            }
            Coordinate other = boxes[i];
            if (other == null) {
                continue;
            }
            if (!inBounds(other.x, other.y)) {
                continue;
            }
            occupancyStamp[other.y][other.x] = occupancyToken;
        }
    }

    private static int floodRegionFrom(Coordinate start) {
        if (!inBounds(start.x, start.y)) {
            return 0;
        }
        advanceRegionToken();
        int token = regionToken;
        int head = 0;
        int tail = 0;
        regionStamp[start.y][start.x] = token;
        regionQueueX[tail] = start.x;
        regionQueueY[tail] = start.y;
        tail++;
        while (head < tail) {
            int cx = regionQueueX[head];
            int cy = regionQueueY[head];
            head++;
            for (int dir = 0; dir < Constants.DIRECTION_X.length; dir++) {
                int nx = cx + Constants.DIRECTION_X[dir];
                int ny = cy + Constants.DIRECTION_Y[dir];
                if (!inBounds(nx, ny)) {
                    continue;
                }
                if (regionStamp[ny][nx] == token) {
                    continue;
                }
                if (cachedMap[ny][nx] == Constants.WALL) {
                    continue;
                }
                if (occupancyStamp[ny][nx] == occupancyToken) {
                    continue;
                }
                regionStamp[ny][nx] = token;
                regionQueueX[tail] = nx;
                regionQueueY[tail] = ny;
                tail++;
            }
        }
        return token;
    }

    private static void advanceRegionToken() {
        regionToken++;
        if (regionToken == Integer.MAX_VALUE) {
            for (int y = 0; y < regionStamp.length; y++) {
                Arrays.fill(regionStamp[y], 0);
            }
            regionToken = 1;
        }
    }

    private static void advanceOccupancyToken() {
        occupancyToken++;
        if (occupancyToken == Integer.MAX_VALUE) {
            for (int y = 0; y < occupancyStamp.length; y++) {
                Arrays.fill(occupancyStamp[y], 0);
            }
            occupancyToken = 1;
        }
    }

    private static int hungarian(int[][] cost, int n) {
        ensureHungarianCapacity(n);
        for (int i = 0; i <= n; i++) {
            u[i] = 0;
            v[i] = 0;
            p[i] = 0;
            way[i] = 0;
        }
        for (int i = 1; i <= n; i++) {
            p[0] = i;
            int j0 = 0;
            Arrays.fill(minv, 0, n + 1, INF);
            Arrays.fill(used, 0, n + 1, false);
            do {
                used[j0] = true;
                int i0 = p[j0];
                int delta = INF;
                int j1 = 0;
                for (int j = 1; j <= n; j++) {
                    if (used[j]) {
                        continue;
                    }
                    int cur = cost[i0 - 1][j - 1] - u[i0] - v[j];
                    if (cur < minv[j]) {
                        minv[j] = cur;
                        way[j] = j0;
                    }
                    if (minv[j] < delta) {
                        delta = minv[j];
                        j1 = j;
                    }
                }
                for (int j = 0; j <= n; j++) {
                    if (used[j]) {
                        u[p[j]] += delta;
                        v[j] -= delta;
                    } else {
                        minv[j] -= delta;
                    }
                }
                j0 = j1;
            } while (p[j0] != 0);
            do {
                int j1 = way[j0];
                p[j0] = p[j1];
                j0 = j1;
            } while (j0 != 0);
        }
        int result = 0;
        for (int j = 1; j <= n; j++) {
            if (p[j] == 0) {
                continue;
            }
            int value = cost[p[j] - 1][j - 1];
            if (value >= INF) {
                return INF;
            }
            result += value;
        }
        return result;
    }
}
