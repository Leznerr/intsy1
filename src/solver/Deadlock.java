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

        return false;
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


}
