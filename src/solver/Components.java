package solver;

import java.util.Arrays;

public final class Components {
    public static int rows;
    public static int cols;
    public static int[][] compId;
    public static int[] goalsInComp;

    private Components() {}

    public static void build(char[][] map, Coordinate[] goals) {
        if (map == null || map.length == 0) {
            rows = 0;
            cols = 0;
            compId = new int[0][0];
            goalsInComp = new int[0];
            return;
        }
        rows = map.length;
        cols = map[0].length;
        compId = new int[rows][cols];
        for (int y = 0; y < rows; y++) {
            Arrays.fill(compId[y], -2);
        }
        int componentCount = 0;
        int maxCells = rows * cols;
        int[] queueX = new int[maxCells];
        int[] queueY = new int[maxCells];
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                if (map[y][x] == Constants.WALL) {
                    compId[y][x] = -1;
                    continue;
                }
                if (compId[y][x] != -2) {
                    continue;
                }
                int head = 0;
                int tail = 0;
                compId[y][x] = componentCount;
                queueX[tail] = x;
                queueY[tail] = y;
                tail++;
                while (head < tail) {
                    int cx = queueX[head];
                    int cy = queueY[head];
                    head++;
                    for (int dir = 0; dir < Constants.DIRECTION_X.length; dir++) {
                        int nx = cx + Constants.DIRECTION_X[dir];
                        int ny = cy + Constants.DIRECTION_Y[dir];
                        if (nx < 0 || nx >= cols || ny < 0 || ny >= rows) {
                            continue;
                        }
                        if (map[ny][nx] == Constants.WALL) {
                            compId[ny][nx] = -1;
                            continue;
                        }
                        if (compId[ny][nx] != -2) {
                            continue;
                        }
                        compId[ny][nx] = componentCount;
                        queueX[tail] = nx;
                        queueY[tail] = ny;
                        tail++;
                    }
                }
                componentCount++;
            }
        }
        goalsInComp = new int[componentCount];
        if (goals != null) {
            for (Coordinate goal : goals) {
                if (goal == null) {
                    continue;
                }
                int gx = goal.x;
                int gy = goal.y;
                if (gy < 0 || gy >= rows || gx < 0 || gx >= cols) {
                    continue;
                }
                int component = compId[gy][gx];
                if (component >= 0) {
                    goalsInComp[component]++;
                }
            }
        }
    }
}
