package solver;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

public final class State {
    private static final AtomicLong INSERTION_SEQUENCE = new AtomicLong(1L);
    private static final int CORRIDOR_PENALTY_WEIGHT = 5;

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
    private final long goalDistanceSquaredSum;
    private final int boxesOnGoals;
    private final int lastPushProgress;
    private final int corridorEntrancePenalty;

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
                  int movedBoxIndex,
                  long goalDistanceSquaredSum,
                  int boxesOnGoals,
                  int lastPushProgress,
                  int corridorEntrancePenalty) {
        this.player = player;
        this.boxes = boxes;
        this.parent = parent;
        this.lastMove = lastMove;
        this.lastMovePush = lastMovePush;
        this.depth = depth;
        this.pushes = pushes;
        this.heuristic = heuristic;
        this.boxesOnGoals = boxesOnGoals;
        this.lastPushProgress = lastPushProgress;
        this.corridorEntrancePenalty = corridorEntrancePenalty;
        this.fCost = computeFCost(heuristic, pushes, boxes, corridorEntrancePenalty);
        this.insertionId = insertionId;
        this.hash = hash;
        this.prePushWalk = prePushWalk == null ? new char[0] : prePushWalk.clone();
        this.movedBoxIndex = movedBoxIndex;
        this.goalDistanceSquaredSum = goalDistanceSquaredSum;
    }

    public static State initial(Coordinate player, Coordinate[] boxes, int heuristic) {
        Coordinate[] orderedBoxes = copyAndSort(boxes);
        long hash = computeHash(player, orderedBoxes);
        long goalDistanceSquaredSum = computeGoalDistanceSquaredSum(orderedBoxes);
        return new State(player,
                orderedBoxes,
                null,
                '\0',
                false,
                0,
                0,
                heuristic,
                0L,
                hash,
                new char[0],
                -1,
                goalDistanceSquaredSum,
                countBoxesOnGoals(orderedBoxes),
                0,
                Heuristic.corridorEntrancePenalty(orderedBoxes));
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
        Coordinate movedCoordinate = findMovedCoordinate(parent.getBoxes(), ordered);
        int movedIndex = findIndex(ordered, movedCoordinate);
        int additionalDepth = prePushWalk == null ? 0 : prePushWalk.length;
        long goalDistanceSquaredSum = computeGoalDistanceSquaredSum(ordered);
        int boxesOnGoals = countBoxesOnGoals(ordered);
        int lastPushProgress = boxesOnGoals - parent.boxesOnGoals;
        int corridorPenalty = Heuristic.corridorEntrancePenalty(ordered);
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
                movedIndex,
                goalDistanceSquaredSum,
                boxesOnGoals,
                lastPushProgress,
                corridorPenalty);
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

    public long getGoalDistanceSquaredSum() {
        return goalDistanceSquaredSum;
    }

    public int getBoxesOnGoals() {
        return boxesOnGoals;
    }

    public int getLastPushProgress() {
        return lastPushProgress;
    }

    public int getCorridorEntrancePenalty() {
        return corridorEntrancePenalty;
    }

    public State withHeuristic(int newHeuristic) {
        if (this.heuristic == newHeuristic) {
            return this;
        }
        return new State(this.player,
                this.boxes,
                this.parent,
                this.lastMove,
                this.lastMovePush,
                this.depth,
                this.pushes,
                newHeuristic,
                this.insertionId,
                this.hash,
                this.prePushWalk,
                this.movedBoxIndex,
                this.goalDistanceSquaredSum,
                this.boxesOnGoals,
                this.lastPushProgress,
                this.corridorEntrancePenalty);
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
        StringBuilder builder = new StringBuilder(depth);
        appendPlan(builder);
        return builder.toString();
    }

    private void appendPlan(StringBuilder builder) {
        if (parent != null) {
            parent.appendPlan(builder);
        }
        if (!lastMovePush) {
            return;
        }
        for (char c : prePushWalk) {
            builder.append(c);
        }
        builder.append(lastMove);
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

    private static long computeGoalDistanceSquaredSum(Coordinate[] boxes) {
        long total = 0L;
        for (Coordinate box : boxes) {
            int distance = Heuristic.nearestGoalDistance(box.x, box.y);
            long contribution = (long) distance * (long) distance;
            total += contribution;
        }
        return total;
    }

    private static int countBoxesOnGoals(Coordinate[] boxes) {
        int count = 0;
        for (Coordinate box : boxes) {
            if (Heuristic.nearestGoalDistance(box.x, box.y) == 0) {
                count++;
            }
        }
        return count;
    }

    private static int computeFCost(int heuristic, int pushes, Coordinate[] boxes, int corridorPenalty) {
        if (heuristic == Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        int weight = dynamicWeight(Heuristic.unplacedBoxesCount(boxes));
        long weighted = (long) heuristic * (long) weight + (long) corridorPenalty * CORRIDOR_PENALTY_WEIGHT;
        long total = pushes + weighted;
        return total >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    private static int dynamicWeight(int remaining) {
        if (remaining >= 5) {
            return 1000;
        }
        if (remaining >= 3) {
            return 600;
        }
        return 300;
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
