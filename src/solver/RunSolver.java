package solver;

import reader.FileReader;
import reader.MapData;

public final class RunSolver {
    private RunSolver() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java -cp out solver.RunSolver <map1> [map2 ...]");
            System.exit(1);
        }
        boolean anyFail = false;
        for (String mapName : args) {
            try {
                FileReader fr = new FileReader();
                MapData md = fr.readFile(mapName);
                if (md == null) {
                    System.out.println(mapName + " ERROR: failed to load map");
                    anyFail = true;
                    continue;
                }

                char[][] mapData = new char[md.rows][md.columns];
                char[][] itemsData = new char[md.rows][md.columns];
                for (int y = 0; y < md.rows; y++) {
                    for (int x = 0; x < md.columns; x++) {
                        char cell = md.tiles[y][x];
                        switch (cell) {
                            case '#':
                                mapData[y][x] = '#';
                                itemsData[y][x] = ' ';
                                break;
                            case '@':
                                mapData[y][x] = ' ';
                                itemsData[y][x] = '@';
                                break;
                            case '$':
                                mapData[y][x] = ' ';
                                itemsData[y][x] = '$';
                                break;
                            case '.':
                                mapData[y][x] = '.';
                                itemsData[y][x] = ' ';
                                break;
                            case '+':
                                mapData[y][x] = '.';
                                itemsData[y][x] = '@';
                                break;
                            case '*':
                                mapData[y][x] = '.';
                                itemsData[y][x] = '$';
                                break;
                            default:
                                mapData[y][x] = ' ';
                                itemsData[y][x] = ' ';
                                break;
                        }
                    }
                }

                SokoBot bot = new SokoBot();
                String plan = bot.solveSokobanPuzzle(md.columns, md.rows, mapData, itemsData);

                SearchStats st = bot.getLastStats();
                int len = (plan == null) ? 0 : plan.length();
                System.out.println(mapName + " len=" + len
                        + " pushes=" + st.getBestPlanPushes()
                        + " time=" + st.getElapsedMillis() + "ms"
                        + " prePruned=" + st.getRegionPrePruned()
                        + " postPruned=" + st.getRegionPostPruned()
                        + " wallLine=" + st.getWallLinePruned());

                if (len == 0) {
                    anyFail = true;
                }
            } catch (Exception e) {
                anyFail = true;
                System.out.println(mapName + " ERROR: " + e.getMessage());
            }
        }
        if (anyFail) {
            System.exit(2);
        }
    }
}
