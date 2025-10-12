package solver;

public class Heuristic {
    
    public static int calculateManhattanDistance(Coordinate a, Coordinate b){
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }

    public static int boxGoalDistance(State s, Coordinate[] goalCoordinates){
        int total = 0;

        for (Coordinate box : s.boxCoordinates){
            int minDist = Integer.MAX_VALUE;

            for (Coordinate goal : goalCoordinates){
                // cacalculate nya yung distance mula sa isang box sa goal
                int dist = calculateManhattanDistance(box, goal);
                if (dist < minDist){
                    minDist = dist;
                }
            }
            total += minDist;
        }

        return total;
    }
}
