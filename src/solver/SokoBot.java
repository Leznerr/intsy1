package solver;

import java.util.ArrayList;

public class SokoBot {
  private Coordinate player = null;
  private final ArrayList<Coordinate> boxList = new ArrayList<>();
  private final ArrayList<Coordinate> goalList = new ArrayList<>();

  /**
   * Solves a single Sokoban puzzle. All previously cached entities are cleared so each invocation
   * works with a fresh snapshot of the provided level data.
   */
  public String solveSokobanPuzzle(int width, int height, char[][] mapData, char[][] itemsData) {
    player = null;
    boxList.clear();
    goalList.clear();

    extractMap(mapData, itemsData, height, width);

    // coconvert array list to arr
    Coordinate[] boxCoordinates = boxList.toArray(new Coordinate[0]);
    Coordinate[] goalCoordinates = goalList.toArray(new Coordinate[0]);

    State initial = new State(player, boxCoordinates);
    Heuristic.initialize(mapData, goalCoordinates);
    initial.setCachedHeuristic(Heuristic.evaluate(initial));
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
