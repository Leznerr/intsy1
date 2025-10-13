package solver;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

public final class Heuristic {
    private static final int INF = 1_000_000;
    private static int[][][] goalDistanceGrids = new int[0][][];
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

    private Heuristic() {}

    public static synchronized void initialize(char[][] mapData, Coordinate[] goals) {
        if (mapData == null || goals == null) {
            cachedGoals = new Coordinate[0];
            goalDistanceGrids = new int[0][][];
            cachedMap = new char[0][0];
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
            return;
        }

        rows = mapData.length;
        cols = rows > 0 ? mapData[0].length : 0;
        cachedMap = new char[rows][cols];
        for (int y = 0; y < rows; y++) {
            System.arraycopy(mapData[y], 0, cachedMap[y], 0, cols);
        }

        cachedGoals = goals.clone();
        goalDistanceGrids = new int[cachedGoals.length][rows][cols];
        for (int g = 0; g < cachedGoals.length; g++) {
            fillWithInf(goalDistanceGrids[g]);
            bfsFromGoal(g);
        }
        ensureCostCapacity(cachedGoals.length);
        ensureDpCapacity(cachedGoals.length);
    }

    public static int evaluate(State state) {
        return evaluate(state.getPlayer(), state.getBoxes());
    }

    public static int evaluate(Coordinate player, Coordinate[] boxes) {
        if (boxes.length == 0 || goalDistanceGrids.length == 0) {
            return 0;
        }
        int boxCount = boxes.length;
        int goalCount = goalDistanceGrids.length;
        if (goalCount < boxCount) {
            return Integer.MAX_VALUE;
        }
        int assignment = assignmentLowerBound(boxes, boxCount, goalCount);
        if (assignment >= INF) {
            return Integer.MAX_VALUE;
        }
        int proximity = estimatePlayerProximity(player, boxes);
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
        Queue<int[]> queue = new ArrayDeque<>();
        dist[goal.y][goal.x] = 0;
        queue.add(new int[] {goal.y, goal.x});
        while (!queue.isEmpty()) {
            int[] cur = queue.remove();
            int cy = cur[0];
            int cx = cur[1];
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
                    queue.add(new int[] {ny, nx});
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
        if (boxCount == 0) {
            return 0;
        }
        if (goalCount == 0) {
            return Integer.MAX_VALUE;
        }
        if (goalCount <= 15) {
            return assignWithBitmask(boxes, boxCount, goalCount);
        }
        int size = Math.max(boxCount, goalCount);
        ensureCostCapacity(size);
        for (int i = 0; i < size; i++) {
            Arrays.fill(reusableCost[i], 0, size, INF);
        }
        for (int b = 0; b < boxCount; b++) {
            Coordinate box = boxes[b];
            if (!inBounds(box.x, box.y)) {
                return Integer.MAX_VALUE;
            }
            for (int g = 0; g < goalCount; g++) {
                reusableCost[b][g] = goalDistanceGrids[g][box.y][box.x];
            }
        }
        return hungarian(reusableCost, size);
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
                    int nextMask = mask | (1 << g);
                    int candidate = base + dist;
                    if (candidate < next[nextMask]) {
                        next[nextMask] = candidate;
                    }
                }
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
