package solver;

import java.util.Arrays;

public class State {
    public Coordinate playerCoordinate;
    public Coordinate[] boxCoordinates;
    public String movesTaken;
    public boolean pushedLastMove;
    public char lastMove;
    private Integer cachedHeuristic = null;
    private State parent;
    private String cachedEncode = null;
    private int cachedHash = 0;

    public State(Coordinate playerCoordinate, Coordinate[] boxCoordinates, String movesTaken, boolean pushedLastMove, char lastMove){
        this.playerCoordinate = playerCoordinate;
        this.boxCoordinates = boxCoordinates;

        Arrays.sort(this.boxCoordinates, (a,b) -> {
            if (a.y != b.y){
                return a.y - b.y;
            }

            return a.x - b.x;
        });

        this.movesTaken = movesTaken;
        this.pushedLastMove = pushedLastMove;
        this.lastMove = lastMove;
        this.parent = null;
    }

    public State(Coordinate playerCoordinate, Coordinate[] boxCoordinates, State parent, boolean pushedLastMove, char lastMove){
        this.playerCoordinate = playerCoordinate;
        // clone and keep already-sorted invariant; caller should keep array sorted (GBFS can bubble moved box)
        this.boxCoordinates = boxCoordinates.clone();
        this.movesTaken = null;
        this.pushedLastMove = pushedLastMove;
        this.lastMove = lastMove;
        this.parent = parent;
    }

    public String reconstructMoves() {
        if (this.movesTaken != null) return this.movesTaken;

        StringBuilder sb = new StringBuilder();
        State cur = this;
        while (cur != null) {
            if (cur.movesTaken != null) {
                sb.append(cur.movesTaken);
                break;
            }
            if (cur.lastMove != 0) sb.append(cur.lastMove);
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

    public String encode() {
        if (cachedEncode != null) {
            return cachedEncode;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(playerCoordinate.x).append(',').append(playerCoordinate.y).append('|');

        for (Coordinate c : boxCoordinates) {
            sb.append(c.x).append(',').append(c.y).append(';');
        }

        cachedEncode = sb.toString();
        cachedHash = cachedEncode.hashCode();
        return cachedEncode;
    }

    public void setCachedHeuristic(int h) {
        this.cachedHeuristic = h;
    }

    // new getter used by comparator; returns large value if not set
    public int getCachedHeuristic() {
        return (cachedHeuristic != null) ? cachedHeuristic : Integer.MAX_VALUE;
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

        if (h == 0){
            cachedHash = 1;
        } else {
            cachedHash = h;
        }

        return cachedHash;
    }

}
