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
    private final int[][] occupiedStamp;
    private int occupiedToken = 1;

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
        this.occupiedStamp = new int[rows][cols];
        DeadlockCache.clear();
        RegionCache.clear();
    }

    public boolean isDeadlock(State state) {
        long start = Diagnostics.now();
        try {
            markBoxes(state);
            return DeadlockCache.getOrCompute(state.getBoxes(), () -> evaluateDeadlock(state));
        } finally {
            if (Diagnostics.ENABLED) {
                Diagnostics.recordDeadlockCheckTime(System.nanoTime() - start);
            }
        }
        return evaluateDeadlock(state, lockedGoal);
    }

    private boolean evaluateDeadlock(State state) {
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
            if (!regionHasGoalIgnoringBoxes(box.x, box.y)) {
                return true;
            }
            if (isCorridorTrap(box)) {
                return true;
            }
            if (isImmovable(box.x, box.y, state.getBoxes())) {
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

    boolean compHasEnoughGoalsForMove(State state, int movedIdx, int destX, int destY) {
        return compHasEnoughGoalsForMove(state.getBoxes(), movedIdx, destX, destY);
    }

    boolean compHasEnoughGoalsForMove(Coordinate[] boxes, int movedIdx, int destX, int destY) {
        if (!inBounds(destX, destY) || mapData[destY][destX] == Constants.WALL) {
            return false;
        }
        if (isCornerNoGoal(destX, destY)) {
            return false;
        }
        if (quickFrozenSquare(destX, destY, boxes)) {
            return false;
        }
        final int comp = Components.compId[destY][destX];
        if (comp < 0) {
            return false;
        }
        int count = 1;
        for (int i = 0; i < boxes.length; i++) {
            if (i == movedIdx) {
                continue;
            }
            Coordinate b = boxes[i];
            if (b != null && Components.compId[b.y][b.x] == comp) {
                count++;
            }
        }
        return count <= Components.goalsInComp[comp];
    }

    boolean roomHasEnoughGoalsForMove(State state, int movedIdx, int destX, int destY) {
        return roomHasEnoughGoalsForMove(state.getBoxes(), movedIdx, destX, destY);
    }

    boolean roomHasEnoughGoalsForMove(Coordinate[] boxes, int movedIdx, int destX, int destY) {
        if (!inBounds(destX, destY) || mapData[destY][destX] == Constants.WALL) {
            return false;
        }
        int r = Rooms.roomId[destY][destX];
        if (r < 0) {
            return false;
        }
        Coordinate current = boxes[movedIdx];
        if (current != null) {
            int currentRoom = Rooms.roomId[current.y][current.x];
            if (currentRoom == r) {
                return true;
            }
        }
        int quota = Rooms.goalsInRoom[r];
        if (quota == 0) {
            quota = 1;
        }
        int count = 1;
        for (int i = 0; i < boxes.length; i++) {
            if (i == movedIdx) {
                continue;
            }
            Coordinate b = boxes[i];
            if (b != null && Rooms.roomId[b.y][b.x] == r) {
                count++;
            }
        }
        return count <= quota;
    }

    boolean quickWallLineFreeze(int x, int y, Coordinate[] boxes) {
        return isHorizontalFreeze(x, y, boxes, -1)
                || isHorizontalFreeze(x, y, boxes, 1)
                || isVerticalFreeze(x, y, boxes, -1)
                || isVerticalFreeze(x, y, boxes, 1);
    }

    boolean isOneSidedFreezeLine(int x, int y, Coordinate[] boxes) {
        if (!inBounds(x, y)) {
            return false;
        }
        if (isGoal(x, y)) {
            return false;
        }
        if (isWallOrOutOfBounds(x, y - 1)
                && checkOneSidedHorizontalFreezeLine(x, y, boxes, -1)) {
            return true;
        }
        if (isWallOrOutOfBounds(x, y + 1)
                && checkOneSidedHorizontalFreezeLine(x, y, boxes, 1)) {
            return true;
        }
        if (isWallOrOutOfBounds(x - 1, y)
                && checkOneSidedVerticalFreezeLine(x, y, boxes, -1)) {
            return true;
        }
        if (isWallOrOutOfBounds(x + 1, y)
                && checkOneSidedVerticalFreezeLine(x, y, boxes, 1)) {
            return true;
        }
        return false;
    }

    private boolean isHorizontalFreeze(int x, int y, Coordinate[] boxes, int wallDy) {
        if (!isWallOrOutOfBounds(x, y + wallDy)) {
            return false;
        }
        int oppY = y - wallDy;
        if (!isBlockedByWallOrBox(x, oppY, boxes, -1)) {
            return false;
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            int cx = x + sx;
            while (inBounds(cx, y) && mapData[y][cx] != Constants.WALL) {
                if (!isWallOrOutOfBounds(cx, y + wallDy)) {
                    return false;
                }
                if (!isBlockedByWallOrBox(cx, oppY, boxes, -1)) {
                    return false;
                }
                cx += sx;
            }
        }
        return true;
    }

    private boolean isVerticalFreeze(int x, int y, Coordinate[] boxes, int wallDx) {
        if (!isWallOrOutOfBounds(x + wallDx, y)) {
            return false;
        }
        int oppX = x - wallDx;
        if (!isBlockedByWallOrBox(oppX, y, boxes, -1)) {
            return false;
        }
        for (int sy = -1; sy <= 1; sy += 2) {
            int cy = y + sy;
            while (inBounds(x, cy) && mapData[cy][x] != Constants.WALL) {
                if (!isWallOrOutOfBounds(x + wallDx, cy)) {
                    return false;
                }
                if (!isBlockedByWallOrBox(oppX, cy, boxes, -1)) {
                    return false;
                }
                cy += sy;
            }
        }
        return true;
    }

    private boolean checkOneSidedHorizontalFreezeLine(int x, int y, Coordinate[] boxes, int wallDy) {
        int oppositeY = y - wallDy;
        if (!isBlockedByWallOrBox(x, oppositeY, boxes, -1)) {
            return false;
        }
        return checkHorizontalOneSidedDirection(x, y, boxes, wallDy, oppositeY, -1)
                && checkHorizontalOneSidedDirection(x, y, boxes, wallDy, oppositeY, 1);
    }

    private boolean checkHorizontalOneSidedDirection(int x,
                                                     int y,
                                                     Coordinate[] boxes,
                                                     int wallDy,
                                                     int oppositeY,
                                                     int stepX) {
        int cx = x + stepX;
        while (inBounds(cx, y) && mapData[y][cx] != Constants.WALL) {
            if (!isWallOrOutOfBounds(cx, y + wallDy)) {
                return true;
            }
            if (isGoal(cx, y)) {
                return false;
            }
            if (!isBlockedByWallOrBox(cx, oppositeY, boxes, -1)) {
                return false;
            }
            cx += stepX;
        }
        return true;
    }

    private boolean checkOneSidedVerticalFreezeLine(int x, int y, Coordinate[] boxes, int wallDx) {
        int oppositeX = x - wallDx;
        if (!isBlockedByWallOrBox(oppositeX, y, boxes, -1)) {
            return false;
        }
        return checkVerticalOneSidedDirection(x, y, boxes, wallDx, oppositeX, -1)
                && checkVerticalOneSidedDirection(x, y, boxes, wallDx, oppositeX, 1);
    }

    private boolean checkVerticalOneSidedDirection(int x,
                                                   int y,
                                                   Coordinate[] boxes,
                                                   int wallDx,
                                                   int oppositeX,
                                                   int stepY) {
        int cy = y + stepY;
        while (inBounds(x, cy) && mapData[cy][x] != Constants.WALL) {
            if (!isWallOrOutOfBounds(x + wallDx, cy)) {
                return true;
            }
            if (isGoal(x, cy)) {
                return false;
            }
            if (!isBlockedByWallOrBox(oppositeX, cy, boxes, -1)) {
                return false;
            }
            cy += stepY;
        }
        return true;
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

    boolean isCornerNoGoal(int x, int y) {
        if (isGoal(x, y)) {
            return false;
        }
        boolean left = isWallOrOutOfBounds(x - 1, y);
        boolean right = isWallOrOutOfBounds(x + 1, y);
        boolean up = isWallOrOutOfBounds(x, y - 1);
        boolean down = isWallOrOutOfBounds(x, y + 1);
        return (up || down) && (left || right);
    }

    boolean quickFrozenSquare(int x, int y, Coordinate[] boxes) {
        int[][] offsets = {{0, 0}, {-1, 0}, {-1, -1}, {0, -1}};
        for (int[] offset : offsets) {
            if (formsTwoByTwoFast(offset[0] + x, offset[1] + y, boxes)) {
                return true;
            }
        }
        return false;
    }

    private boolean formsTwoByTwoFast(int startX, int startY, Coordinate[] boxes) {
        int filled = 0;
        int offGoalBoxes = 0;
        for (int dy = 0; dy < 2; dy++) {
            for (int dx = 0; dx < 2; dx++) {
                int cx = startX + dx;
                int cy = startY + dy;
                if (isWallOrOutOfBounds(cx, cy)) {
                    filled++;
                    continue;
                }
                if (!hasBox(boxes, -1, cx, cy)) {
                    return false;
                }
                if (!isGoal(cx, cy)) {
                    offGoalBoxes++;
                }
                filled++;
            }
        }
        return filled == 4 && offGoalBoxes > 0;
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

    private boolean isImmovable(int x, int y, Coordinate[] boxes) {
        if (isGoal(x, y)) {
            return false;
        }
        for (int dir = 0; dir < Constants.DIRECTION_X.length; dir++) {
            int destX = x + Constants.DIRECTION_X[dir];
            int destY = y + Constants.DIRECTION_Y[dir];
            if (isWallOrOutOfBounds(destX, destY)) {
                continue;
            }
            if (hasBox(boxes, -1, destX, destY)) {
                continue;
            }
            int backX = x - Constants.DIRECTION_X[dir];
            int backY = y - Constants.DIRECTION_Y[dir];
            if (isWallOrOutOfBounds(backX, backY)) {
                continue;
            }
            if (hasBox(boxes, -1, backX, backY)) {
                if (!isCorner(backX, backY)) {
                    return false;
                }
                continue;
            }
            return false;
        }
        return true;
    }

    public boolean regionHasGoalForMove(Coordinate[] boxes, int movedIdx, int destX, int destY) {
        if (!inBounds(destX, destY)) {
            return false;
        }
        if (mapData[destY][destX] == Constants.WALL) {
            return false;
        }
        return RegionCache.getOrCompute(boxes, movedIdx, destX, destY,
                () -> computeRegionHasGoalForMove(boxes, movedIdx, destX, destY));
    }

    private boolean computeRegionHasGoalForMove(Coordinate[] boxes, int movedIdx, int destX, int destY) {
        advanceRegionToken();
        advanceOccupiedToken();
        int ignoringToken = regionToken;
        Queue<int[]> queue = new ArrayDeque<>();
        for (int i = 0; i < boxes.length; i++) {
            if (i == movedIdx) {
                continue;
            }
            Coordinate other = boxes[i];
            if (other != null && inBounds(other.x, other.y)) {
                occupiedStamp[other.y][other.x] = occupiedToken;
            }
        }
        regionStamp[destY][destX] = ignoringToken;
        queue.add(new int[] {destX, destY});
        int goalsInRegion = 0;
        while (!queue.isEmpty()) {
            int[] cell = queue.remove();
            int cx = cell[0];
            int cy = cell[1];
            if (goal[cy][cx]) {
                goalsInRegion++;
            }
            for (int dir = 0; dir < Constants.DIRECTION_X.length; dir++) {
                int nx = cx + Constants.DIRECTION_X[dir];
                int ny = cy + Constants.DIRECTION_Y[dir];
                if (!inBounds(nx, ny)) {
                    continue;
                }
                if (regionStamp[ny][nx] == ignoringToken) {
                    continue;
                }
                if (mapData[ny][nx] == Constants.WALL) {
                    continue;
                }
                if (occupiedStamp[ny][nx] == occupiedToken) {
                    continue;
                }
                regionStamp[ny][nx] = ignoringToken;
                queue.add(new int[] {nx, ny});
            }
        }
        if (goalsInRegion == 0) {
            return false;
        }

        int boxesInRegion = 0;
        if (regionStamp[destY][destX] == ignoringToken) {
            boxesInRegion++;
        }
        for (int i = 0; i < boxes.length; i++) {
            if (i == movedIdx) {
                continue;
            }
            Coordinate box = boxes[i];
            if (box != null && inBounds(box.x, box.y)
                    && regionStamp[box.y][box.x] == ignoringToken) {
                boxesInRegion++;
            }
        }
        return boxesInRegion <= goalsInRegion;
    }

    public boolean isWallLineFreeze(int x, int y, Coordinate[] boxes) {
        if (!inBounds(x, y)) {
            return false;
        }
        if (isGoal(x, y)) {
            return false;
        }
        int movedIdx = findBoxIndex(boxes, x, y);
        if (movedIdx < 0) {
            return false;
        }

        boolean verticalAlignment = false;
        if (isWallOrOutOfBounds(x - 1, y)) {
            verticalAlignment |= isVerticalLineBlocked(x, y, boxes, movedIdx, -1);
        }
        if (!verticalAlignment && isWallOrOutOfBounds(x + 1, y)) {
            verticalAlignment |= isVerticalLineBlocked(x, y, boxes, movedIdx, 1);
        }
        if (verticalAlignment && !regionHasGoalForMove(boxes, movedIdx, x, y)) {
            return true;
        }

        boolean horizontalAlignment = false;
        if (isWallOrOutOfBounds(x, y - 1)) {
            horizontalAlignment |= isHorizontalLineBlocked(x, y, boxes, movedIdx, -1);
        }
        if (!horizontalAlignment && isWallOrOutOfBounds(x, y + 1)) {
            horizontalAlignment |= isHorizontalLineBlocked(x, y, boxes, movedIdx, 1);
        }
        if (horizontalAlignment && !regionHasGoalForMove(boxes, movedIdx, x, y)) {
            return true;
        }
        return false;
    }

    private boolean isVerticalLineBlocked(int x, int y, Coordinate[] boxes, int movedIdx, int wallDx) {
        int wallX = x + wallDx;
        if (!isWallOrOutOfBounds(wallX, y)) {
            return false;
        }
        int oppositeX = x - wallDx;
        if (!isBlockedByWallOrBox(oppositeX, y, boxes, movedIdx)) {
            return false;
        }
        if (!checkVerticalDirection(x, y, boxes, movedIdx, wallDx, oppositeX, -1)) {
            return false;
        }
        if (!checkVerticalDirection(x, y, boxes, movedIdx, wallDx, oppositeX, 1)) {
            return false;
        }
        return true;
    }

    private boolean checkVerticalDirection(int x,
                                           int y,
                                           Coordinate[] boxes,
                                           int movedIdx,
                                           int wallDx,
                                           int oppositeX,
                                           int stepY) {
        int cy = y + stepY;
        while (inBounds(x, cy) && mapData[cy][x] != Constants.WALL) {
            if (!isWallOrOutOfBounds(x + wallDx, cy)) {
                break;
            }
            if (!isBlockedByWallOrBox(oppositeX, cy, boxes, movedIdx)) {
                return false;
            }
            cy += stepY;
        }
        return true;
    }

    private boolean isHorizontalLineBlocked(int x, int y, Coordinate[] boxes, int movedIdx, int wallDy) {
        int wallY = y + wallDy;
        if (!isWallOrOutOfBounds(x, wallY)) {
            return false;
        }
        int oppositeY = y - wallDy;
        if (!isBlockedByWallOrBox(x, oppositeY, boxes, movedIdx)) {
            return false;
        }
        if (!checkHorizontalDirection(x, y, boxes, movedIdx, wallDy, oppositeY, -1)) {
            return false;
        }
        if (!checkHorizontalDirection(x, y, boxes, movedIdx, wallDy, oppositeY, 1)) {
            return false;
        }
        return true;
    }

    private boolean checkHorizontalDirection(int x,
                                             int y,
                                             Coordinate[] boxes,
                                             int movedIdx,
                                             int wallDy,
                                             int oppositeY,
                                             int stepX) {
        int cx = x + stepX;
        while (inBounds(cx, y) && mapData[y][cx] != Constants.WALL) {
            if (!isWallOrOutOfBounds(cx, y + wallDy)) {
                break;
            }
            if (!isBlockedByWallOrBox(cx, oppositeY, boxes, movedIdx)) {
                return false;
            }
            cx += stepX;
        }
        return true;
    }

    private boolean isBlockedByWallOrBox(int x, int y, Coordinate[] boxes, int movedIdx) {
        if (!inBounds(x, y)) {
            return true;
        }
        if (mapData[y][x] == Constants.WALL) {
            return true;
        }
        return hasBox(boxes, movedIdx, x, y);
    }

    private boolean hasBox(Coordinate[] boxes, int excludeIdx, int x, int y) {
        for (int i = 0; i < boxes.length; i++) {
            if (i == excludeIdx) {
                continue;
            }
            Coordinate box = boxes[i];
            if (box != null && box.x == x && box.y == y) {
                return true;
            }
        }
        return false;
    }

    private int findBoxIndex(Coordinate[] boxes, int x, int y) {
        for (int i = 0; i < boxes.length; i++) {
            Coordinate box = boxes[i];
            if (box != null && box.x == x && box.y == y) {
                return i;
            }
        }
        return -1;
    }

    public boolean regionHasGoalIgnoringBoxes(int startX, int startY) {
        if (!inBounds(startX, startY)) {
            return false;
        }
        if (mapData[startY][startX] == Constants.WALL) {
            return false;
        }
        int component = Components.compId[startY][startX];
        return component >= 0 && Components.goalsInComp[component] > 0;
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

    private void advanceOccupiedToken() {
        occupiedToken++;
        if (occupiedToken == Integer.MAX_VALUE) {
            for (int y = 0; y < rows; y++) {
                java.util.Arrays.fill(occupiedStamp[y], 0);
            }
            occupiedToken = 1;
        }
    }

    private boolean inBounds(int x, int y) {
        return y >= 0 && y < rows && x >= 0 && x < cols;
    }
}
