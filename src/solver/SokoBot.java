package solver;

import java.util.ArrayList;

public class SokoBot {
  private Coordinate player = null;
  private ArrayList<Coordinate> boxList = new ArrayList<>();
  private ArrayList<Coordinate> goalList = new ArrayList<>();
  public String solveSokobanPuzzle(int width, int height, char[][] mapData, char[][] itemsData) {
    extractMap(mapData, itemsData, height, width);

    // coconvert array list to arr
    Coordinate[] boxCoordinates = boxList.toArray(new Coordinate[0]);
    Coordinate[] goalCoordinates = goalList.toArray(new Coordinate[0]);

    State initial = new State(player, boxCoordinates, "", false, '\0');
    int heuristic = Heuristic.boxGoalDistance(initial, goalCoordinates);
    GBFS solver = new GBFS(mapData, goalCoordinates);

    return solver.search(initial);
  }

  private void extractMap(char[][] mapData, char[][] itemsData, int height, int width){
    for (int y = 0; y < height; y++){
      for (int x = 0; x < width; x++){
        char mapDataContent = mapData[y][x];
        char itemsDataContent = itemsData[y][x];

        if (mapDataContent == Constants.PLAYER || itemsDataContent == Constants.PLAYER) {
          player = new Coordinate(x, y);
        }

        if (mapDataContent == Constants.BOX || itemsDataContent == Constants.BOX) {
          boxList.add(new Coordinate(x, y));
        }

        if (mapDataContent == Constants.GOAL || itemsDataContent == Constants.GOAL) {
          goalList.add(new Coordinate(x, y));
        }
      }
    }
  }
}
