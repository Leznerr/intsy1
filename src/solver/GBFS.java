package solver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;


public class GBFS {
    private char[][] mapData;
    private Coordinate[] goalCoordinates;

    public GBFS(char[][] mapData, Coordinate[] goalCoordinates){
        this.mapData = mapData;
        this.goalCoordinates = goalCoordinates;
    }

    public String search(State initial){
        PriorityQueue<State> open = new PriorityQueue<>(
            Comparator.comparingInt(State::getCachedHeuristic)
        );

        Set<State> visited = new HashSet<>();

        initial.setCachedHeuristic(Heuristic.boxGoalDistance(initial, goalCoordinates));
        open.add(initial);

        while (!open.isEmpty()){
            State current = open.poll();
            
            if (visited.contains(current)) {
                continue;
            }

            if (current.isGoal(goalCoordinates)){
                return current.reconstructMoves();
            }

            visited.add(current);

            for (State next : generateNextStates(current, visited)) {
                open.add(next);
            }
        }

        return null;
    }

    private ArrayList<State> generateNextStates(State s, Set<State> visited){
        ArrayList<State> nextStates = new ArrayList<>();

        for (int i = 0; i < Constants.MOVES.length; i++){

            if (pruneMove(s, i)){
                continue;
            }

            int newX = s.playerCoordinate.x + Constants.DIRECTION_X[i];
            int newY = s.playerCoordinate.y + Constants.DIRECTION_Y[i];
            
            if (newX < 0 || newX >= mapData[0].length || newY < 0 || newY >= mapData.length){
                continue;
            }
            
            if (mapData[newY][newX] == '#'){
                continue;
            }

            // gawa bago
            Coordinate[] newBoxCoordinates = new Coordinate[s.boxCoordinates.length];
            for (int b = 0; b < s.boxCoordinates.length; b++) {
                newBoxCoordinates[b] = new Coordinate(s.boxCoordinates[b].x, s.boxCoordinates[b].y);
            }

            int boxIndex = boxAt(newBoxCoordinates, newX, newY);

            boolean pushed = false;
            // If there is a box, try to push it
            if (boxIndex != -1) {
                int boxNewX = newX + Constants.DIRECTION_X[i];
                int boxNewY = newY + Constants.DIRECTION_Y[i];

                if (boxNewX < 0 || boxNewX >= mapData[0].length || boxNewY < 0 || boxNewY >= mapData.length){
                    continue;
                }

                if (mapData[boxNewY][boxNewX] == Constants.WALL || boxAt(newBoxCoordinates, boxNewX, boxNewY) != -1){
                    continue;
                }

                
                // Move the box
                pushed = true;
                newBoxCoordinates[boxIndex].x = boxNewX;
                newBoxCoordinates[boxIndex].y = boxNewY;

                int j = boxIndex;
                while (j > 0) {
                    Coordinate cur = newBoxCoordinates[j];
                    Coordinate prev = newBoxCoordinates[j-1];
                    if (cur.y < prev.y || (cur.y == prev.y && cur.x < prev.x)) {
                        Coordinate tmp = newBoxCoordinates[j-1];
                        newBoxCoordinates[j-1] = newBoxCoordinates[j];
                        newBoxCoordinates[j] = tmp;
                        j--;
                    } else break;
                }

                while (j < newBoxCoordinates.length - 1) {
                    Coordinate cur = newBoxCoordinates[j];
                    Coordinate next = newBoxCoordinates[j+1];
                    if (cur.y > next.y || (cur.y == next.y && cur.x > next.x)) {
                        Coordinate tmp = newBoxCoordinates[j+1];
                        newBoxCoordinates[j+1] = newBoxCoordinates[j];
                        newBoxCoordinates[j] = tmp;
                        j++;
                    } else break;
                }
            }

            State next = new State(new Coordinate(newX, newY), newBoxCoordinates, s, pushed, Constants.MOVES[i]);

            if (visited.contains(next)) {
                continue;
            }

            next.setCachedHeuristic(Heuristic.boxGoalDistance(next, goalCoordinates));

            Deadlock deadlock = new Deadlock(next, mapData, newBoxCoordinates);

            if (!deadlock.isDeadlock()){
                nextStates.add(next);
            }
        }

        return nextStates;
    }

    private int boxAt(Coordinate[] boxes, int x, int y) {
        for (int i = 0; i < boxes.length; i++) {
            if (boxes[i].x == x && boxes[i].y == y){
                return i;   
            }
        }
        return -1;
    }

    private boolean pruneMove(State s, int index){
        if (s.movesTaken != null && !s.movesTaken.isEmpty()) {
            boolean pushedBox = s.pushedLastMove;

            if (!pushedBox) {
                char last = s.lastMove;
                char next = Constants.MOVES[index];
                if ((last == 'u' && next == 'd') ||
                    (last == 'd' && next == 'u') ||
                    (last == 'l' && next == 'r') ||
                    (last == 'r' && next == 'l')) {
                        return true;
                }
            }
        }

        return false;
    }
}