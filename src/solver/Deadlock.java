package solver;

public final class Deadlock {
    private final char[][] mapData;
    private final boolean[][] goal;
    private final int rows;
    private final int cols;
    private final int[][] boxStamp;
    private int boxStampToken = 1;
    private final int[][] regionStamp;
    private int regionToken = 1;
    private long cornerDeadlocks;
    private long twoByTwoDeadlocks;
    private long wallLineDeadlocks;
    private final int[] queueX;
    private final int[] queueY;
    private final RegionSummary regionSummary = new RegionSummary();

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
        int capacity = Math.max(1, rows * cols);
        this.queueX = new int[capacity];
        this.queueY = new int[capacity];
    }

    public void resetRuleCounters() {
        cornerDeadlocks = 0L;
        twoByTwoDeadlocks = 0L;
        wallLineDeadlocks = 0L;
    }

    public long getCornerDeadlocks() {
        return cornerDeadlocks;
    }

    public long getTwoByTwoDeadlocks() {
        return twoByTwoDeadlocks;
    }

    public long getWallLineDeadlocks() {
        return wallLineDeadlocks;
    }

    public boolean isDeadlock(State state) {
        return detectDeadlock(state, true);
    }

    public boolean wouldBeDeadlock(State state) {
        return detectDeadlock(state, false);
    }

    private boolean detectDeadlock(State state, boolean countRules) {
        markBoxes(state);
        Coordinate[] boxes = state.getBoxes();
        for (int idx = 0; idx < boxes.length; idx++) {
            Coordinate box = boxes[idx];
            if (isGoal(box.x, box.y)) {
                continue;
            }
            if (isCorner(box.x, box.y)) {
                if (countRules) {
                    cornerDeadlocks++;
                }
                return true;
            }
            if (isFrozenSquare(box.x, box.y)) {
                if (countRules) {
                    twoByTwoDeadlocks++;
                }
                return true;
            }
            if (isLineFreeze(box)) {
                if (countRules) {
                    wallLineDeadlocks++;
                }
                return true;
            }
            if (isWallLineTrap(box)) {
                if (countRules) {
                    wallLineDeadlocks++;
                }
                return true;
            }
            if (!regionHasGoalIgnoringBoxes(box.x, box.y)) {
                return true;
            }
            if (!regionHasGoalIgnoringBoxes(box.x, box.y)) {
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

    private boolean isLineFreeze(Coordinate box) {
        if (isGoal(box.x, box.y)) {
            return false;
        }
        if (hasBox(box.x + 1, box.y) && !isGoal(box.x + 1, box.y)) {
            boolean wallsAbove = isWallOrOutOfBounds(box.x, box.y - 1)
                    && isWallOrOutOfBounds(box.x + 1, box.y - 1);
            boolean wallsBelow = isWallOrOutOfBounds(box.x, box.y + 1)
                    && isWallOrOutOfBounds(box.x + 1, box.y + 1);
            if (wallsAbove || wallsBelow) {
                return true;
            }
        }
        if (hasBox(box.x, box.y + 1) && !isGoal(box.x, box.y + 1)) {
            boolean wallsLeft = isWallOrOutOfBounds(box.x - 1, box.y)
                    && isWallOrOutOfBounds(box.x - 1, box.y + 1);
            boolean wallsRight = isWallOrOutOfBounds(box.x + 1, box.y)
                    && isWallOrOutOfBounds(box.x + 1, box.y + 1);
            if (wallsLeft || wallsRight) {
                return true;
            }
        }
        return false;
    }

    private boolean isWallLineTrap(Coordinate box) {
        if (isGoal(box.x, box.y)) {
            return false;
        }
        boolean adjacentWall = isWallOrOutOfBounds(box.x - 1, box.y)
                || isWallOrOutOfBounds(box.x + 1, box.y)
                || isWallOrOutOfBounds(box.x, box.y - 1)
                || isWallOrOutOfBounds(box.x, box.y + 1);
        if (!adjacentWall) {
            return false;
        }
        return !regionHasGoalIgnoringBoxes(box.x, box.y);
    }

    public boolean regionHasGoalForMove(Coordinate[] boxes, int movedIdx, int destX, int destY) {
        if (!analyzeBoxAwareRegion(boxes, movedIdx, destX, destY, regionSummary)) {
            return false;
        }
        if (regionSummary.goals == 0) {
            return false;
        }
        return regionSummary.boxes <= regionSummary.goals;
    }

    public boolean isWallLineFreeze(int x, int y, Coordinate[] boxes) {
        if (!inBounds(x, y)) {
            return false;
        }
        if (isGoal(x, y)) {
            return false;
        }
        int targetIdx = -1;
        for (int i = 0; i < boxes.length; i++) {
            Coordinate candidate = boxes[i];
            if (candidate != null && candidate.x == x && candidate.y == y) {
                targetIdx = i;
                break;
            }
        }
        if (targetIdx == -1) {
            return false;
        }
        if (!analyzeBoxAwareRegion(boxes, targetIdx, x, y, regionSummary)) {
            return false;
        }
        if (regionSummary.goals > 0) {
            return false;
        }
        if (isWallOrOutOfBounds(x - 1, y)
                && isLineFrozenAlongRegion(x, y, regionSummary, 0, 1, 1, 0)) {
            return true;
        }
        if (isWallOrOutOfBounds(x + 1, y)
                && isLineFrozenAlongRegion(x, y, regionSummary, 0, 1, -1, 0)) {
            return true;
        }
        if (isWallOrOutOfBounds(x, y - 1)
                && isLineFrozenAlongRegion(x, y, regionSummary, 1, 0, 0, 1)) {
            return true;
        }
        if (isWallOrOutOfBounds(x, y + 1)
                && isLineFrozenAlongRegion(x, y, regionSummary, 1, 0, 0, -1)) {
            return true;
        }
        return false;
    }

    private boolean analyzeBoxAwareRegion(Coordinate[] boxes,
                                          int movedIdx,
                                          int startX,
                                          int startY,
                                          RegionSummary summary) {
        summary.reset();
        if (!inBounds(startX, startY)) {
            return false;
        }
        if (mapData[startY][startX] == Constants.WALL) {
            return false;
        }

        advanceBoxStamp();
        for (int i = 0; i < boxes.length; i++) {
            if (i == movedIdx) {
                continue;
            }
            Coordinate other = boxes[i];
            if (other != null && inBounds(other.x, other.y)) {
                boxStamp[other.y][other.x] = boxStampToken;
            }
        }

        advanceRegionToken();
        int token = regionToken;
        summary.token = token;

        int head = 0;
        int tail = 0;
        regionStamp[startY][startX] = token;
        queueX[tail] = startX;
        queueY[tail] = startY;
        tail++;

        summary.boxes = 1;
        if (goal[startY][startX]) {
            summary.goals = 1;
        }

        while (head < tail) {
            int cx = queueX[head];
            int cy = queueY[head];
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
                if (mapData[ny][nx] == Constants.WALL) {
                    continue;
                }
                if (boxStamp[ny][nx] == boxStampToken) {
                    regionStamp[ny][nx] = token;
                    summary.boxes++;
                    if (goal[ny][nx]) {
                        summary.goals++;
                    }
                    continue;
                }
                regionStamp[ny][nx] = token;
                queueX[tail] = nx;
                queueY[tail] = ny;
                tail++;
                if (goal[ny][nx]) {
                    summary.goals++;
                }
            }
        }
        return true;
    }

    private boolean isLineFrozenAlongRegion(int x,
                                            int y,
                                            RegionSummary summary,
                                            int axisStepX,
                                            int axisStepY,
                                            int oppositeDx,
                                            int oppositeDy) {
        if (!isCellOppositeBlocked(x, y, oppositeDx, oppositeDy)) {
            return false;
        }
        if (!isDirectionOppositeBlocked(x, y, axisStepX, axisStepY, oppositeDx, oppositeDy, summary.token)) {
            return false;
        }
        if (!isDirectionOppositeBlocked(x, y, -axisStepX, -axisStepY, oppositeDx, oppositeDy, summary.token)) {
            return false;
        }
        return true;
    }

    private boolean isDirectionOppositeBlocked(int originX,
                                               int originY,
                                               int stepX,
                                               int stepY,
                                               int oppositeDx,
                                               int oppositeDy,
                                               int token) {
        if (stepX == 0 && stepY == 0) {
            return true;
        }
        int cx = originX + stepX;
        int cy = originY + stepY;
        while (true) {
            if (!inBounds(cx, cy)) {
                return true;
            }
            if (regionStamp[cy][cx] != token) {
                return true;
            }
            if (!isCellOppositeBlocked(cx, cy, oppositeDx, oppositeDy)) {
                return false;
            }
            cx += stepX;
            cy += stepY;
        }
    }

    private boolean isCellOppositeBlocked(int cellX, int cellY, int offsetX, int offsetY) {
        int targetX = cellX + offsetX;
        int targetY = cellY + offsetY;
        if (!inBounds(targetX, targetY)) {
            return true;
        }
        if (mapData[targetY][targetX] == Constants.WALL) {
            return true;
        }
        if (hasBox(targetX, targetY) && !isGoal(targetX, targetY)) {
            return true;
        }
        return false;
    }

    private boolean regionHasGoalIgnoringBoxes(int startX, int startY) {
        advanceRegionToken();
        int head = 0;
        int tail = 0;
        regionStamp[startY][startX] = regionToken;
        queueX[tail] = startX;
        queueY[tail] = startY;
        tail++;
        while (head < tail) {
            int cx = queueX[head];
            int cy = queueY[head];
            head++;
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
                regionStamp[ny][nx] = regionToken;
                queueX[tail] = nx;
                queueY[tail] = ny;
                tail++;
            }
        }
        return false;
    }

    private boolean regionHasGoal(Coordinate startBox) {
        advanceRegionToken();
        if (!inBounds(startBox.x, startBox.y)) {
            return false;
        }
        int head = 0;
        int tail = 0;
        regionStamp[startBox.y][startBox.x] = regionToken;
        queueX[tail] = startBox.x;
        queueY[tail] = startBox.y;
        tail++;
        while (head < tail) {
            int cx = queueX[head];
            int cy = queueY[head];
            head++;
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
                queueX[tail] = nx;
                queueY[tail] = ny;
                tail++;
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

    private static final class RegionSummary {
        int goals;
        int boxes;
        int token;

        void reset() {
            goals = 0;
            boxes = 0;
            token = 0;
        }
    }
}
