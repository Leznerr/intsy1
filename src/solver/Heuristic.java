package solver;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

public class Heuristic {

    private static final int INF = 1_000_000;
    private static int[][][] goalDistanceGrids = new int[0][][];
    private static Coordinate[] cachedGoals = new Coordinate[0];
    private static int rows = 0;
    private static int cols = 0;
    private static char[][] cachedMap = new char[0][0];

    private Heuristic() {}

    public static synchronized void initialize(char[][] mapData, Coordinate[] goals) {
        if (mapData == null) {
            goalDistanceGrids = new int[0][][];
            cachedGoals = new Coordinate[0];
            cachedMap = new char[0][0];
            rows = cols = 0;
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
    }

    private static void fillWithInf(int[][] grid) {
        for (int y = 0; y < grid.length; y++) {
            Arrays.fill(grid[y], INF);
        }
    }

    private static void bfsFromGoal(int goalIndex) {
        Coordinate goal = cachedGoals[goalIndex];
        if (goal.y < 0 || goal.y >= rows || goal.x < 0 || goal.x >= cols) {
            return;
        }

        int[][] dist = goalDistanceGrids[goalIndex];
        Queue<int[]> queue = new ArrayDeque<>();
        dist[goal.y][goal.x] = 0;
        queue.add(new int[]{goal.y, goal.x});

        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            int cy = cur[0];
            int cx = cur[1];
            int base = dist[cy][cx];

            for (int dir = 0; dir < Constants.DIRECTION_X.length; dir++) {
                int nx = cx + Constants.DIRECTION_X[dir];
                int ny = cy + Constants.DIRECTION_Y[dir];

                if (nx < 0 || nx >= cols || ny < 0 || ny >= rows) {
                    continue;
                }

                if (cachedMap[ny][nx] == Constants.WALL) {
                    continue;
                }

                if (dist[ny][nx] > base + 1) {
                    dist[ny][nx] = base + 1;
                    queue.add(new int[]{ny, nx});
                }
            }
        }
    }

    public static int evaluate(State state) {
        if (goalDistanceGrids.length == 0 || state.boxCoordinates.length == 0) {
            return 0;
        }

        int boxCount = state.boxCoordinates.length;
        int goalCount = goalDistanceGrids.length;
        int size = Math.max(boxCount, goalCount);
        int[][] cost = new int[size][size];

        for (int i = 0; i < size; i++) {
            Arrays.fill(cost[i], INF);
        }

        for (int b = 0; b < boxCount; b++) {
            Coordinate box = state.boxCoordinates[b];
            if (box.y < 0 || box.y >= rows || box.x < 0 || box.x >= cols) {
                continue;
            }
            for (int g = 0; g < goalCount; g++) {
                cost[b][g] = goalDistanceGrids[g][box.y][box.x];
            }
        }

        int assignment = hungarian(cost, size);
        if (assignment >= INF) {
            return INF;
        }

        int minPlayerWalk = estimatePlayerProximity(state);
        return assignment + minPlayerWalk;
    }

    private static int estimatePlayerProximity(State state) {
        int best = INF;
        for (Coordinate box : state.boxCoordinates) {
            int dist = Math.abs(state.playerCoordinate.x - box.x) + Math.abs(state.playerCoordinate.y - box.y);
            if (dist < best) {
                best = dist;
            }
        }
        return best == INF ? 0 : best;
    }

    private static int hungarian(int[][] cost, int n) {
        int[] u = new int[n + 1];
        int[] v = new int[n + 1];
        int[] p = new int[n + 1];
        int[] way = new int[n + 1];

        for (int i = 1; i <= n; i++) {
            p[0] = i;
            int j0 = 0;
            int[] minv = new int[n + 1];
            boolean[] used = new boolean[n + 1];
            Arrays.fill(minv, INF);
            Arrays.fill(used, false);
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
