package solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

public final class State {
    private static final AtomicLong INSERTION_SEQUENCE = new AtomicLong(1L);
    private static final int HEURISTIC_WEIGHT = 1000;

    private final Coordinate player;
    private final Coordinate[] boxes;
    private final State parent;
    private final char lastMove;
    private final boolean lastMovePush;
    private final int depth;
    private final int pushes;
    private final int heuristic;
    private final int fCost;
    private final long insertionId;
    private final long hash;
    private final char[] prePushWalk;
    private final int movedBoxIndex;

    private State(Coordinate player,
                  Coordinate[] boxes,
                  State parent,
                  char lastMove,
                  boolean lastMovePush,
                  int depth,
                  int pushes,
                  int heuristic,
                  long insertionId,
                  long hash,
                  char[] prePushWalk,
                  int movedBoxIndex) {
        this.player = player;
        this.boxes = boxes;
        this.parent = parent;
        this.lastMove = lastMove;
        this.lastMovePush = lastMovePush;
        this.depth = depth;
        this.pushes = pushes;
        this.heuristic = heuristic;
        if (heuristic == Integer.MAX_VALUE) {
            this.fCost = Integer.MAX_VALUE;
        } else {
            long weighted = (long) heuristic * HEURISTIC_WEIGHT;
            long total = pushes + weighted;
            this.fCost = total >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
        }
        this.insertionId = insertionId;
        this.hash = hash;
        this.prePushWalk = prePushWalk == null ? new char[0] : prePushWalk.clone();
        this.movedBoxIndex = movedBoxIndex;
    }

    public static State initial(Coordinate player, Coordinate[] boxes, int heuristic) {
        Coordinate[] orderedBoxes = copyAndSort(boxes);
        long hash = computeHash(player, orderedBoxes);
        return new State(player, orderedBoxes, null, '\0', false, 0, 0, heuristic, 0L, hash, new char[0], -1);
    }

    public static State walk(State parent, Coordinate nextPlayer, char move) {
        return new State(nextPlayer,
                parent.boxes,
                parent,
                move,
                false,
                parent.depth + 1,
                parent.pushes,
                parent.heuristic,
                parent.insertionId,
                parent.hash,
                new char[0],
                -1);
    }

    public static State push(State parent,
                              Coordinate nextPlayer,
                              Coordinate[] updatedBoxes,
                              char move,
                              int heuristic,
                              char[] prePushWalk) {
        Coordinate[] ordered = copyAndSort(updatedBoxes);
        long insertion = INSERTION_SEQUENCE.getAndIncrement();
        long hash = computeHash(nextPlayer, ordered);
        Coordinate newLocation = findMovedCoordinate(parent.getBoxes(), ordered);
        int movedIndex = findIndex(ordered, newLocation);
        int additionalDepth = prePushWalk == null ? 0 : prePushWalk.length;
        return new State(nextPlayer,
                ordered,
                parent,
                move,
                true,
                parent.depth + additionalDepth + 1,
                parent.pushes + 1,
                heuristic,
                insertion,
                hash,
                prePushWalk,
                movedIndex);
    }

    private static int findIndex(Coordinate[] boxes, Coordinate target) {
        for (int i = 0; i < boxes.length; i++) {
            Coordinate box = boxes[i];
            if (box.x == target.x && box.y == target.y) {
                return i;
            }
        }
        return -1;
    }

    private static Coordinate findMovedCoordinate(Coordinate[] previous, Coordinate[] current) {
        for (Coordinate candidate : current) {
            boolean exists = false;
            for (Coordinate prior : previous) {
                if (candidate.x == prior.x && candidate.y == prior.y) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                return candidate;
            }
        }
        if (current.length > 0) {
            return current[0];
        }
        throw new IllegalStateException("Unable to determine moved box location");
    }

    private static Coordinate[] copyAndSort(Coordinate[] boxes) {
        Coordinate[] copy = new Coordinate[boxes.length];
        for (int i = 0; i < boxes.length; i++) {
            Coordinate c = boxes[i];
            copy[i] = new Coordinate(c.x, c.y);
        }
        Arrays.sort(copy);
        return copy;
    }

    private static long computeHash(Coordinate player, Coordinate[] boxes) {
        long hash = 1469598103934665603L;
        hash = (hash ^ player.x) * 1099511628211L;
        hash = (hash ^ player.y) * 1099511628211L;
        for (Coordinate box : boxes) {
            hash = (hash ^ box.x) * 1099511628211L;
            hash = (hash ^ box.y) * 1099511628211L;
        }
        return hash;
    }

    public Coordinate getPlayer() {
        return player;
    }

    public Coordinate[] getBoxes() {
        return boxes;
    }

    public State getParent() {
        return parent;
    }

    public char getLastMove() {
        return lastMove;
    }

    public boolean wasPush() {
        return lastMovePush;
    }

    public int getDepth() {
        return depth;
    }

    public int getPushes() {
        return pushes;
    }

    public int getHeuristic() {
        return heuristic;
    }

    public int getFCost() {
        return fCost;
    }

    public long getInsertionId() {
        return insertionId;
    }

    public long getHash() {
        return hash;
    }

    public boolean isGoal(Coordinate[] goals) {
        for (Coordinate box : boxes) {
            boolean onGoal = false;
            for (Coordinate goal : goals) {
                if (box.x == goal.x && box.y == goal.y) {
                    onGoal = true;
                    break;
                }
            }
            if (!onGoal) {
                return false;
            }
        }
        return true;
    }

    public String reconstructPlan() {
        if (depth == 0) {
            return "";
        }
        char[] sequence = new char[depth];
        int index = 0;
        ArrayList<State> chain = new ArrayList<>();
        State cursor = this;
        while (cursor != null) {
            chain.add(cursor);
            cursor = cursor.parent;
        }
        for (int i = chain.size() - 1; i >= 0; i--) {
            State node = chain.get(i);
            if (!node.lastMovePush) {
                continue;
            }
            for (char move : node.prePushWalk) {
                if (index < sequence.length) {
                    sequence[index++] = move;
                }
            }
            if (index < sequence.length) {
                sequence[index++] = node.lastMove;
            }
        }
        return new String(sequence, 0, index);
    }

    public int getMovedBoxIndex() {
        return movedBoxIndex;
    }

    public boolean hasBoxAt(int x, int y) {
        for (Coordinate c : boxes) {
            if (c.x == x && c.y == y) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof State)) {
            return false;
        }
        State that = (State) other;
        if (player.x != that.player.x || player.y != that.player.y) {
            return false;
        }
        return Arrays.equals(boxes, that.boxes);
    }

    @Override
    public int hashCode() {
        return (int) (hash ^ (hash >>> 32));
    }
}
