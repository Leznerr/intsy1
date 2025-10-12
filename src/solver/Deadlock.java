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

            if (isWallDeadlock(box)){
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
        boolean wallUp = mapData[box.y - 1][box.x] == Constants.WALL;
        boolean wallDown = mapData[box.y + 1][box.x] == Constants.WALL;
        boolean wallLeft = mapData[box.y][box.x-1] == Constants.WALL;
        boolean wallRight = mapData[box.y][box.x+1] == Constants.WALL;


        return (wallUp && wallLeft) || (wallUp && wallRight) || (wallDown && wallLeft) || (wallDown && wallRight);
    }

    private boolean isWallDeadlock(Coordinate box) {
    if (mapData[box.y - 1][box.x] == Constants.WALL || mapData[box.y + 1][box.x] == Constants.WALL) {
        if (!goalInRow(box.y)) return true;
    }

    if (mapData[box.y][box.x - 1] == Constants.WALL || mapData[box.y][box.x + 1] == Constants.WALL) {
        if (!goalInColumn(box.x)) return true;
    }

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
}
