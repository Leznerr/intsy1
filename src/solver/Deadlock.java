package solver;

import java.util.ArrayDeque;
import java.util.Queue;

public final class Deadlock {
    private final char[][] mapData;
    private final boolean[][] goal;
    private final int rows;
    private final int cols;
    private final int[][] boxStamp;
    private int boxStampToken = 1;
    private final int[][] regionStamp;
    private int regionToken = 1;

    public Deadlock(char[][] mapData, Coordinate[] goalCoordinates) {
        this.mapData = mapData;
        this.rows = mapData.length;
        this.cols = rows == 0 ? 0 : mapData[0].length;
        this.goal = new boolean[rows][cols];
        for (Coordinate g : goalCoordinates) {
            if (inBounds(g.x, g.y)) {
                goal[g.y][g.x] = true;
            }
        }
        this.boxStamp = new int[rows][cols];
        this.regionStamp = new int[rows][cols];
    }

    public boolean isDeadlock(State state) {
        markBoxes(state);
        for (Coordinate box : state.getBoxes()) {
            if (isGoal(box.x, box.y)) {
                continue;
            }
            if (isCorner(box.x, box.y)) {
                return true;
            }
            if (isFrozenSquare(box.x, box.y)) {
                return true;
            }
            if (isCorridorTrap(box)) {
                return true;
            }
        }
        return false;
    }

    private void markBoxes(State state) {
        advanceBoxStamp();
        for (Coordinate box : state.getBoxes()) {
            if (inBounds(box.x, box.y)) {
                boxStamp[box.y][box.x] = boxStampToken;
            }
        }
    }

    private void advanceBoxStamp() {
        boxStampToken++;
        if (boxStampToken == Integer.MAX_VALUE) {
            for (int y = 0; y < rows; y++) {
                java.util.Arrays.fill(boxStamp[y], 0);
            }
            boxStampToken = 1;
        }
    }

    private boolean hasBox(int x, int y) {
        return inBounds(x, y) && boxStamp[y][x] == boxStampToken;
    }

    private boolean isGoal(int x, int y) {
        return inBounds(x, y) && goal[y][x];
    }

    private boolean isCorner(int x, int y) {
        boolean up = isWallOrOutOfBounds(x, y - 1);
        boolean down = isWallOrOutOfBounds(x, y + 1);
        boolean left = isWallOrOutOfBounds(x - 1, y);
        boolean right = isWallOrOutOfBounds(x + 1, y);
        return (up && left) || (up && right) || (down && left) || (down && right);
    }

    private boolean isBlocked(int x, int y) {
        if (!inBounds(x, y)) {
            return true;
        }
        if (mapData[y][x] == Constants.WALL) {
            return true;
        }
        return hasBox(x, y) && !isGoal(x, y);
    }

    private boolean isWallOrOutOfBounds(int x, int y) {
        if (!inBounds(x, y)) {
            return true;
        }
        return mapData[y][x] == Constants.WALL;
    }

    private boolean isFrozenSquare(int x, int y) {
        int[][] offsets = {{0, 0}, {-1, 0}, {-1, -1}, {0, -1}};
        for (int[] offset : offsets) {
            if (formsTwoByTwo(offset[0] + x, offset[1] + y)) {
                return true;
            }
        }
        return false;
    }

    private boolean formsTwoByTwo(int startX, int startY) {
        for (int dy = 0; dy < 2; dy++) {
            for (int dx = 0; dx < 2; dx++) {
                int x = startX + dx;
                int y = startY + dy;
                if (!isBlocked(x, y)) {
                    return false;
                }
            }
        }
        int boxes = 0;
        for (int dy = 0; dy < 2; dy++) {
            for (int dx = 0; dx < 2; dx++) {
                int x = startX + dx;
                int y = startY + dy;
                if (hasBox(x, y) && !isGoal(x, y)) {
                    boxes++;
                }
            }
        }
        return boxes > 0;
    }

    private boolean isCorridorTrap(Coordinate box) {
        boolean verticalWalls = isWallOrOutOfBounds(box.x - 1, box.y)
                && isWallOrOutOfBounds(box.x + 1, box.y);
        boolean horizontalWalls = isWallOrOutOfBounds(box.x, box.y - 1)
                && isWallOrOutOfBounds(box.x, box.y + 1);
        if (!verticalWalls && !horizontalWalls) {
            return false;
        }
        if (regionHasGoal(box)) {
            return false;
        }
        return true;
    }

    private boolean regionHasGoal(Coordinate startBox) {
        advanceRegionToken();
        Queue<int[]> queue = new ArrayDeque<>();
        if (!inBounds(startBox.x, startBox.y)) {
            return false;
        }
        regionStamp[startBox.y][startBox.x] = regionToken;
        queue.add(new int[] {startBox.x, startBox.y});
        while (!queue.isEmpty()) {
            int[] cell = queue.remove();
            int cx = cell[0];
            int cy = cell[1];
            if (goal[cy][cx]) {
                return true;
            }
            for (int dir = 0; dir < Constants.DIRECTION_X.length; dir++) {
                int nx = cx + Constants.DIRECTION_X[dir];
                int ny = cy + Constants.DIRECTION_Y[dir];
                if (!inBounds(nx, ny)) {
                    continue;
                }
                if (regionStamp[ny][nx] == regionToken) {
                    continue;
                }
                if (mapData[ny][nx] == Constants.WALL) {
                    continue;
                }
                if (hasBox(nx, ny) && !(nx == startBox.x && ny == startBox.y)) {
                    continue;
                }
                regionStamp[ny][nx] = regionToken;
                queue.add(new int[] {nx, ny});
            }
        }
        return false;
    }

    private void advanceRegionToken() {
        regionToken++;
        if (regionToken == Integer.MAX_VALUE) {
            for (int y = 0; y < rows; y++) {
                java.util.Arrays.fill(regionStamp[y], 0);
            }
            regionToken = 1;
        }
    }

    private boolean inBounds(int x, int y) {
        return y >= 0 && y < rows && x >= 0 && x < cols;
    }
}
