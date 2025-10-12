package solver;

public class Deadlock {
    private final char[][] mapData;
    private final boolean[][] isGoal;
    private final boolean[] goalRow;
    private final boolean[] goalCol;
    private final int rows;
    private final int cols;

    public Deadlock(char[][] mapData, Coordinate[] goalCoordinates) {
        this.mapData = mapData;

        this.rows = mapData.length;
        this.cols = this.rows == 0 ? 0 : mapData[0].length;

        this.isGoal = new boolean[this.rows][this.cols];
        this.goalRow = new boolean[this.rows];
        this.goalCol = new boolean[this.cols];

        if (goalCoordinates == null) {
            throw new IllegalArgumentException("Goal coordinates must be supplied to deadlock detection");
        }

        for (Coordinate g : goalCoordinates) {
            if (g.y >= 0 && g.y < this.rows && g.x >= 0 && g.x < this.cols) {
                isGoal[g.y][g.x] = true;
                goalRow[g.y] = true;
                goalCol[g.x] = true;
            }
        }
    }

    public boolean isDeadlock(State state) {
        for (Coordinate box : state.boxCoordinates) {
            if (isBoxInGoal(box)) {
                continue;
            }

            if (isCornerDeadlock(state, box)) {
                return true;
            }

            if (isTwoByTwoDeadlock(state, box)) {
                return true;
            }

            if (isWallLineDeadlock(state, box)) {
                return true;
            }
        }

        return false;
    }

    private boolean isBoxInGoal(Coordinate box) {
        if (box.y < 0 || box.y >= rows) {
            return false;
        }
        if (box.x < 0 || box.x >= cols) {
            return false;
        }
        return isGoal[box.y][box.x];
    }

    private boolean isCornerDeadlock(State state, Coordinate box) {
        boolean up = actsAsRigidObstacle(state, box.x, box.y - 1, 0, -1);
        boolean down = actsAsRigidObstacle(state, box.x, box.y + 1, 0, 1);
        boolean left = actsAsRigidObstacle(state, box.x - 1, box.y, -1, 0);
        boolean right = actsAsRigidObstacle(state, box.x + 1, box.y, 1, 0);

        return (up && left) || (up && right) || (down && left) || (down && right);
    }

    private boolean isTwoByTwoDeadlock(State state, Coordinate box) {
        int[][] offsets = {{0, 0}, {-1, 0}, {0, -1}, {-1, -1}};
        for (int[] offset : offsets) {
            if (isSolidSquare(state, box.x + offset[0], box.y + offset[1])) {
                return true;
            }
        }
        return false;
    }

    private boolean isWallLineDeadlock(State state, Coordinate box) {
        return isClosedCorridor(state, box, true) || isClosedCorridor(state, box, false);
    }

    private boolean goalInRow(int row) {
        if (row < 0 || row >= rows) {
            return false;
        }

        return goalRow[row];
    }

    private boolean goalInColumn(int col) {
        if (col < 0 || col >= cols) {
            return false;
        }

        return goalCol[col];
    }

    private boolean isBlocking(State state, int x, int y) {
        if (y < 0 || y >= rows || x < 0 || x >= cols) {
            return true;
        }
        if (mapData[y][x] == Constants.WALL) {
            return true;
        }
        if (state.hasBoxAt(x, y) && !isGoal[y][x]) {
            return true;
        }
        return false;
    }

    private boolean actsAsRigidObstacle(State state, int x, int y, int towardX, int towardY) {
        if (isWallOrBoundary(x, y)) {
            return true;
        }

        if (!state.hasBoxAt(x, y) || isGoal[y][x]) {
            return false;
        }

        if (towardX != 0) {
            return isWallOrBoundary(x, y - 1) && isWallOrBoundary(x, y + 1);
        }

        return isWallOrBoundary(x - 1, y) && isWallOrBoundary(x + 1, y);
    }

    private boolean isSolidSquare(State state, int startX, int startY) {
        int boxes = 0;
        for (int dy = 0; dy < 2; dy++) {
            for (int dx = 0; dx < 2; dx++) {
                int x = startX + dx;
                int y = startY + dy;
                if (!isBlocking(state, x, y)) {
                    return false;
                }
                if (y >= 0 && y < rows && x >= 0 && x < cols && state.hasBoxAt(x, y) && !isGoal[y][x]) {
                    boxes++;
                }
            }
        }
        // Ensure there is at least one non-goal box in the block (our current box qualifies)
        return boxes > 0;
    }

    private boolean isWallOrBoundary(int x, int y) {
        if (y < 0 || y >= rows || x < 0 || x >= cols) {
            return true;
        }
        return mapData[y][x] == Constants.WALL;
    }

    private boolean isClosedCorridor(State state, Coordinate box, boolean horizontal) {
        int side1x;
        int side1y;
        int side2x;
        int side2y;

        if (horizontal) {
            side1x = 0;
            side1y = -1;
            side2x = 0;
            side2y = 1;
        } else {
            side1x = -1;
            side1y = 0;
            side2x = 1;
            side2y = 0;
        }

        if (!isWallOrBoundary(box.x + side1x, box.y + side1y) || !isWallOrBoundary(box.x + side2x, box.y + side2y)) {
            return false;
        }

        LineCheck negative = walkCorridor(state, box.x, box.y,
                horizontal ? -1 : 0,
                horizontal ? 0 : -1,
                side1x, side1y, side2x, side2y);
        LineCheck positive = walkCorridor(state, box.x, box.y,
                horizontal ? 1 : 0,
                horizontal ? 0 : 1,
                side1x, side1y, side2x, side2y);

        if (negative.goalFound || positive.goalFound) {
            return false;
        }

        if (!negative.closed || !positive.closed) {
            return false;
        }

        if (horizontal) {
            return !goalInRow(box.y);
        }

        return !goalInColumn(box.x);
    }

    private LineCheck walkCorridor(State state, int startX, int startY, int stepX, int stepY, int side1x, int side1y, int side2x, int side2y) {
        int cx = startX + stepX;
        int cy = startY + stepY;

        while (true) {
            if (cx < 0 || cy < 0 || cy >= rows || cx >= cols) {
                return new LineCheck(true, false);
            }

            if (!isWallOrBoundary(cx + side1x, cy + side1y) || !isWallOrBoundary(cx + side2x, cy + side2y)) {
                return new LineCheck(false, false);
            }

            if (mapData[cy][cx] == Constants.WALL) {
                return new LineCheck(true, false);
            }

            if (!state.hasBoxAt(cx, cy)) {
                if (isGoal[cy][cx]) {
                    return new LineCheck(false, true);
                }
                return new LineCheck(false, false);
            }

            if (isGoal[cy][cx]) {
                return new LineCheck(false, true);
            }

            cx += stepX;
            cy += stepY;
        }
    }

    private static final class LineCheck {
        final boolean closed;
        final boolean goalFound;

        LineCheck(boolean closed, boolean goalFound) {
            this.closed = closed;
            this.goalFound = goalFound;
        }
    }
}
