package solver;

import java.util.ArrayList;
import java.util.List;

import reader.FileReader;
import reader.MapData;

public final class RunSolver {
    private RunSolver() {
    }

    public static void main(String[] args) {
        List<String> mapNames = new ArrayList<>();
        boolean anyFail = false;
        parseArguments(args, mapNames);
        if (mapNames.isEmpty()) {
            System.err.println("Usage: java -cp out solver.RunSolver [--diag] [--diag-sample=N] [--diag-no-proximity] <map1> [map2 ...]");
            System.exit(1);
        }
        for (String mapName : mapNames) {
            try {
                if (Diagnostics.ENABLED) {
                    Diagnostics.resetForMap(mapName);
                }
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
                SearchOutcome outcome = bot.getLastOutcome();
                boolean solved = outcome != null && outcome.bestPlanSolves();
                int len = (plan == null) ? 0 : plan.length();
                System.out.println(mapName + " len=" + len
                        + " pushes=" + st.getBestPlanPushes()
                        + " time=" + st.getElapsedMillis() + "ms"
                        + " prePruned=" + st.getRegionPrePruned()
                        + " postPruned=" + st.getRegionPostPruned()
                        + " wallLine=" + st.getWallLinePruned());

                if (Diagnostics.ENABLED) {
                    Diagnostics.emitDiagnostics(st.getElapsedMillis(), solved, st.isTimeLimitHit(), st.getClosedStates());
                }

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

    private static void parseArguments(String[] args, List<String> mapNames) {
        boolean diagEnabled = false;
        for (String arg : args) {
            if ("--diag".equals(arg)) {
                diagEnabled = true;
                continue;
            }
            if (arg.startsWith("--diag-sample=")) {
                diagEnabled = true;
                String value = arg.substring("--diag-sample=".length());
                try {
                    int parsed = Integer.parseInt(value);
                    Diagnostics.SAMPLE_MASK = Math.max(0, parsed);
                } catch (NumberFormatException ignore) {
                    System.err.println("Invalid --diag-sample value: " + value);
                }
                continue;
            }
            if ("--diag-no-proximity".equals(arg)) {
                diagEnabled = true;
                Diagnostics.ZERO_PROXIMITY = true;
                continue;
            }
            mapNames.add(arg);
        }
        Diagnostics.ENABLED = diagEnabled;
    }
}
