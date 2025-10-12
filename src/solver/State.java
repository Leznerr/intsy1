package solver;

public class State {
    private static final java.util.concurrent.atomic.AtomicLong NEXT_ID = new java.util.concurrent.atomic.AtomicLong(1L);

    public final Coordinate playerCoordinate;
    public final Coordinate[] boxCoordinates;
    public final boolean pushedLastMove;
    public final char lastMove;
    private final State parent;
    private final int depth;
    private final int pushes;
    private final long stateId;

    private int cachedHeuristic = Integer.MIN_VALUE;
    private int cachedHash = 0;

    public State(Coordinate playerCoordinate, Coordinate[] boxCoordinates){
        this(playerCoordinate, boxCoordinates, true, null, false, '\0', 0, 0);
    }

    public State(Coordinate playerCoordinate,
                 Coordinate[] boxCoordinates,
                 boolean cloneBoxes,
                 State parent,
                 boolean pushedLastMove,
                 char lastMove,
                 int depth,
                 int pushes){
        this.playerCoordinate = playerCoordinate;
        if (cloneBoxes) {
            this.boxCoordinates = new Coordinate[boxCoordinates.length];
            for (int i = 0; i < boxCoordinates.length; i++) {
                Coordinate c = boxCoordinates[i];
                this.boxCoordinates[i] = new Coordinate(c.x, c.y);
            }
            sortBoxes(this.boxCoordinates);
        } else {
            this.boxCoordinates = boxCoordinates;
        }
        this.parent = parent;
        this.pushedLastMove = pushedLastMove;
        this.lastMove = lastMove;
        this.depth = depth;
        this.pushes = pushes;
        this.stateId = NEXT_ID.getAndIncrement();
    }

    private static void sortBoxes(Coordinate[] boxes) {
        java.util.Arrays.sort(boxes, (a, b) -> {
            if (a.y != b.y) {
                return Integer.compare(a.y, b.y);
            }
            return Integer.compare(a.x, b.x);
        });
    }

    public State spawnWalkState(Coordinate newPlayerCoordinate, char move) {
        return new State(newPlayerCoordinate, this.boxCoordinates, false, this, false, move, this.depth + 1, this.pushes);
    }

    public State spawnPushState(Coordinate newPlayerCoordinate, Coordinate[] newBoxCoordinates, char move) {
        return new State(newPlayerCoordinate, newBoxCoordinates, true, this, true, move, this.depth + 1, this.pushes + 1);
    }

    public String reconstructMoves() {
        StringBuilder sb = new StringBuilder();
        State cur = this;
        while (cur != null) {
            if (cur.lastMove != '\0') {
                sb.append(cur.lastMove);
            }
            cur = cur.parent;
        }
        return sb.reverse().toString();
    }
    public boolean isGoal(Coordinate[] goals){
        for (Coordinate box : boxCoordinates){
            boolean onGoal = false;
            for (Coordinate goal : goals){
                if (box.x == goal.x && box.y == goal.y){
                    onGoal = true;
                    break;
                }
            }

            if (!onGoal){
                return false;
            }
        }

        return true;
    }

    public void setCachedHeuristic(int h) {
        this.cachedHeuristic = h;
    }

    public int getCachedHeuristic() {
        return (cachedHeuristic != Integer.MIN_VALUE) ? cachedHeuristic : Integer.MAX_VALUE;
    }

    public int getDepth() {
        return depth;
    }

    public int getPushes() {
        return pushes;
    }

    public State getParent() {
        return parent;
    }

    public long getStateId() {
        return stateId;
    }

    public boolean hasBoxAt(int x, int y) {
        for (Coordinate c : boxCoordinates) {
            if (c.x == x && c.y == y) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o){
        if (this == o){
            return true;
        }
        
        if (!(o instanceof  State)){
            return false;
        }

        State other = (State) o;
        
        if (this.playerCoordinate.x != other.playerCoordinate.x ||
            this.playerCoordinate.y != other.playerCoordinate.y){
            return false;
        }

        if (this.boxCoordinates.length != other.boxCoordinates.length){
            return false;
        }

        for (int i = 0; i < boxCoordinates.length; i++){
            Coordinate a = this.boxCoordinates[i];
            Coordinate b = other.boxCoordinates[i];

            if (a.x != b.x || a.y != b.y){
                return false;
            }
        }

        return true;
    }

    @Override
    public int hashCode(){
        if (cachedHash != 0){
            return cachedHash;
        }

        int h = 17;
        h = h * 31 + playerCoordinate.x;
        h = h * 31 + playerCoordinate.y;

        for (Coordinate c : boxCoordinates){
            h = h * 31 + c.x;
            h = h * 31 + c.y;
        }

        cachedHash = (h == 0) ? 1 : h;
        return cachedHash;
    }

}
