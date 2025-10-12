package solver;

public class Deadlock {
    private final State state;
    private final char[][] mapData;
    private final Coordinate[] goalCoordinates;

    private final boolean[][] isGoal;
    private final boolean[] goalRow;
    private final boolean[] goalCol;

    public Deadlock(State state, char[][] mapData, Coordinate[] goalCoordinates){
        this.state = state;
        this.mapData = mapData;
        this.goalCoordinates = goalCoordinates;

        assert this.goalCoordinates != null : "Goal coordinates must be supplied to deadlock detection";

        int rows = mapData.length;
        int cols = mapData[0].length;

        isGoal = new boolean[rows][cols];
        goalRow = new boolean[rows];
        goalCol = new boolean[cols];

        for (Coordinate g : goalCoordinates) {
            if (g.y >= 0 && g.y < rows && g.x >= 0 && g.x < cols) {
                isGoal[g.y][g.x] = true;
                goalRow[g.y] = true;
                goalCol[g.x] = true;
            }
        }
    }

    public boolean isDeadlock(){
        for (Coordinate box: state.boxCoordinates){
            if (isBoxInGoal(box)){
                continue;
            }

            if (isCornerDeadlock(box)){
                return true;
            }

            if (isTwoByTwoDeadlock(box)){
                return true;
            }

            if (isWallLineDeadlock(box)){
                return true;
            }
        }

        return false;
    }

    private boolean isBoxInGoal(Coordinate box){
        if (box.y < 0 || box.y >= isGoal.length) {
            return false;
        }
        if (box.x < 0 || box.x >= isGoal[0].length){
            return false;
        } 
        return isGoal[box.y][box.x];
    }

    private boolean isCornerDeadlock(Coordinate box){
        boolean up = isBlocking(box.x, box.y - 1);
        boolean down = isBlocking(box.x, box.y + 1);
        boolean left = isBlocking(box.x - 1, box.y);
        boolean right = isBlocking(box.x + 1, box.y);

        return (up && left) || (up && right) || (down && left) || (down && right);
    }

    private boolean isTwoByTwoDeadlock(Coordinate box) {
        int[][] offsets = {{0,0},{-1,0},{0,-1},{-1,-1}};
        for (int[] offset : offsets) {
            if (isSolidSquare(box.x + offset[0], box.y + offset[1])) {
                return true;
            }
        }
        return false;
    }

    private boolean isWallLineDeadlock(Coordinate box) {
        return isClosedCorridor(box, true) || isClosedCorridor(box, false);
    }

    private boolean goalInRow(int row) {
        if (row < 0 || row >= goalRow.length){
            return false;
        }

        return goalRow[row];
    }

    private boolean goalInColumn(int col) {
        if (col < 0 || col >= goalCol.length){
            return false;
        }

        return goalCol[col];
    }

    private boolean isBlocking(int x, int y) {
        if (y < 0 || y >= mapData.length || x < 0 || x >= mapData[0].length) {
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

    private boolean isSolidSquare(int startX, int startY) {
        int boxes = 0;
        for (int dy = 0; dy < 2; dy++) {
            for (int dx = 0; dx < 2; dx++) {
                int x = startX + dx;
                int y = startY + dy;
                if (!isBlocking(x, y)) {
                    return false;
                }
                if (state.hasBoxAt(x, y) && !isGoal[y][x]) {
                    boxes++;
                }
            }
        }
        // Ensure there is at least one non-goal box in the block (our current box qualifies)
        return boxes > 0;
    }

    private boolean isWallOrBoundary(int x, int y) {
        if (y < 0 || y >= mapData.length || x < 0 || x >= mapData[0].length) {
            return true;
        }
        return mapData[y][x] == Constants.WALL;
    }

    private boolean isClosedCorridor(Coordinate box, boolean horizontal) {
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

        LineCheck negative = walkCorridor(box.x, box.y,
                horizontal ? -1 : 0,
                horizontal ? 0 : -1,
                side1x, side1y, side2x, side2y);
        LineCheck positive = walkCorridor(box.x, box.y,
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

    private LineCheck walkCorridor(int startX, int startY, int stepX, int stepY, int side1x, int side1y, int side2x, int side2y) {
        int cx = startX + stepX;
        int cy = startY + stepY;

        while (true) {
            if (cx < 0 || cy < 0 || cy >= mapData.length || cx >= mapData[0].length) {
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
