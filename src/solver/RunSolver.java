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
                long solveStart = System.nanoTime();
                String plan = bot.solveSokobanPuzzle(md.columns, md.rows, mapData, itemsData);
                long solveEnd = System.nanoTime();

                if (plan == null) {
                    plan = "";
                }

                SearchStats st = bot.getLastStats();
                long elapsedMs = (solveEnd - solveStart) / 1_000_000L;
                ReplayValidator.ValidationResult validation = ReplayValidator.validate(mapData, itemsData, plan);
                boolean solved = validation.fullyValid && validation.solved;
                int len = plan.length();
                if ("original2".equals(mapName)) {
                    if (len <= 700) {
                        System.out.println(mapName + " NOTE: plan length <= 700; consider enabling deeper/push-heavier tie-break fallback.");
                    }
                }
                System.out.println(mapName + " len=" + len
                        + " pushes=" + validation.pushes
                        + " time=" + elapsedMs + "ms"
                        + " region=" + st.getRegionPruned()
                        + " corner=" + st.getCornerPruned()
                        + " freeze=" + st.getFreezePruned()
                        + " wallLine=" + st.getWallLinePruned()
                        + " dup=" + st.getDuplicatePruned()
                        + " goals=" + validation.boxesOnGoals
                        + " solved=" + solved);

                if (!solved && validation.finalItems != null) {
                    System.out.println(mapName + " final board:");
                    dumpBoard(mapData, validation.finalItems);
                    System.out.println("remaining goals: " + listGoals(mapData, validation.finalItems));
                    System.out.println("box positions: " + listBoxes(validation.finalItems));
                }

                if (Diagnostics.ENABLED) {
                    Diagnostics.emitDiagnostics(st.getElapsedMillis(), solved, st.isTimeLimitHit(), st.getClosedStates());
                }

                if (len == 0) {
                    anyFail = true;
                }
            } catch (OutOfMemoryError oom) {
                anyFail = true;
                System.out.println(mapName + " ERROR: out of memory. Consider running with -Xmx1024m or increasing the system paging file.");
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
        if (diagEnabled && !Diagnostics.ENABLED) {
            System.err.println("Diagnostics requested but disabled at compile time.");
        }
    }

    private static void dumpBoard(char[][] map, char[][] items) {
        for (int y = 0; y < map.length; y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < map[y].length; x++) {
                char item = items[y][x];
                char cell = map[y][x];
                char render;
                if (item != ' ' && item != 0) {
                    render = item;
                } else if (cell == Constants.WALL) {
                    render = '#';
                } else if (cell == Constants.GOAL) {
                    render = '.';
                } else {
                    render = ' ';
                }
                row.append(render);
            }
            System.out.println(row);
        }
    }

    private static java.util.List<String> listGoals(char[][] map, char[][] items) {
        java.util.List<String> goals = new java.util.ArrayList<>();
        for (int y = 0; y < map.length; y++) {
            for (int x = 0; x < map[y].length; x++) {
                if (map[y][x] == Constants.GOAL && items[y][x] != Constants.BOX_ON_GOAL) {
                    goals.add(x + "," + y);
                }
            }
        }
        return goals;
    }

    private static java.util.List<String> listBoxes(char[][] items) {
        java.util.List<String> boxes = new java.util.ArrayList<>();
        for (int y = 0; y < items.length; y++) {
            if (items[y] == null) {
                continue;
            }
            for (int x = 0; x < items[y].length; x++) {
                char c = items[y][x];
                if (c == Constants.BOX || c == Constants.BOX_ON_GOAL) {
                    boxes.add(x + "," + y + (c == Constants.BOX_ON_GOAL ? "*" : ""));
                }
            }
        }
        return boxes;
    }
}
