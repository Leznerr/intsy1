package solver;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ReplayValidator {
    private ReplayValidator() {}

    public static final class ValidationResult {
        public final boolean fullyValid;
        public final boolean solved;
        public final int lastValidIndex;

        ValidationResult(boolean fullyValid, boolean solved, int lastValidIndex) {
            this.fullyValid = fullyValid;
            this.solved = solved;
            this.lastValidIndex = lastValidIndex;
        }
    }

    public static ValidationResult validate(char[][] map, char[][] items, String plan) {
        if (map == null || items == null) {
            throw new IllegalArgumentException("Map and item layers must be provided");
        }
        int height = map.length;
        int width = height == 0 ? 0 : map[0].length;
        boolean[][] walls = new boolean[height][width];
        boolean[][] goals = new boolean[height][width];

        Coordinate player = null;
        Set<Integer> boxSet = new HashSet<>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                char mapCell = map[y][x];
                char itemCell = items[y][x];
                if (mapCell == Constants.WALL) {
                    walls[y][x] = true;
                }
                if (mapCell == Constants.GOAL || itemCell == Constants.BOX_ON_GOAL || itemCell == Constants.PLAYER_ON_GOAL) {
                    goals[y][x] = true;
                }
                switch (itemCell) {
                    case Constants.PLAYER:
                    case Constants.PLAYER_ON_GOAL:
                        player = new Coordinate(x, y);
                        break;
                    case Constants.BOX:
                    case Constants.BOX_ON_GOAL:
                        boxSet.add(index(x, y, width));
                        break;
                    default:
                        break;
                }
            }
        }

        if (player == null) {
            throw new IllegalStateException("No player found on map for replay validation");
        }

        List<Character> moves = new ArrayList<>(plan.length());
        for (int i = 0; i < plan.length(); i++) {
            moves.add(plan.charAt(i));
        }

        int lastValid = -1;
        boolean valid = true;
        for (int i = 0; i < moves.size(); i++) {
            char move = moves.get(i);
            int dirIndex = directionIndex(move);
            if (dirIndex == -1) {
                valid = false;
                break;
            }
            int nx = player.x + Constants.DIRECTION_X[dirIndex];
            int ny = player.y + Constants.DIRECTION_Y[dirIndex];
            if (!inBounds(nx, ny, width, height) || walls[ny][nx]) {
                valid = false;
                break;
            }
            int neighborIndex = index(nx, ny, width);
            if (boxSet.contains(neighborIndex)) {
                int destX = nx + Constants.DIRECTION_X[dirIndex];
                int destY = ny + Constants.DIRECTION_Y[dirIndex];
                if (!inBounds(destX, destY, width, height) || walls[destY][destX]) {
                    valid = false;
                    break;
                }
                int destIndex = index(destX, destY, width);
                if (boxSet.contains(destIndex)) {
                    valid = false;
                    break;
                }
                boxSet.remove(neighborIndex);
                boxSet.add(destIndex);
                player = new Coordinate(nx, ny);
            } else {
                player = new Coordinate(nx, ny);
            }
            lastValid = i;
        }

        boolean solved = true;
        for (int cell : boxSet) {
            int y = cell / width;
            int x = cell % width;
            if (!goals[y][x]) {
                solved = false;
                break;
            }
        }

        return new ValidationResult(valid, solved, lastValid);
    }

    private static int index(int x, int y, int width) {
        return y * width + x;
    }

    private static boolean inBounds(int x, int y, int width, int height) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    private static int directionIndex(char move) {
        switch (move) {
            case 'u':
            case 'U':
                return Constants.UP;
            case 'd':
            case 'D':
                return Constants.DOWN;
            case 'l':
            case 'L':
                return Constants.LEFT;
            case 'r':
            case 'R':
                return Constants.RIGHT;
            default:
                return -1;
        }
    }
}
