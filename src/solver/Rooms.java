package solver;

public final class Rooms {
    public static int rows, cols;
    public static int[][] roomId;     // -1 for wall, [0..R)
    public static int[] goalsInRoom;

    private Rooms() {}

    public static void build(char[][] map, Coordinate[] goals){
        rows = map.length; cols = rows==0?0:map[0].length;
        roomId = new int[rows][cols];
        for (int y=0;y<rows;y++) java.util.Arrays.fill(roomId[y], -2);
        int rid = 0;

        int max = rows*cols, qx[] = new int[max], qy[] = new int[max];
        for (int y=0;y<rows;y++) for (int x=0;x<cols;x++){
            if (map[y][x]==Constants.WALL){ roomId[y][x] = -1; continue; }
            if (roomId[y][x]!=-2) continue;
            // BFS that does not cross 1-tile doorways (throats)
            int h=0,t=0; roomId[y][x]=rid; qx[t]=x; qy[t]=y; t++;
            while (h<t){
                int cx=qx[h], cy=qy[h]; h++;
                for (int d=0; d<Constants.DIRECTION_X.length; d++){
                    int nx=cx+Constants.DIRECTION_X[d], ny=cy+Constants.DIRECTION_Y[d];
                    if (nx<0||nx>=cols||ny<0||ny>=rows) continue;
                    if (map[ny][nx]==Constants.WALL) { roomId[ny][nx] = -1; continue; }
                    if (roomId[ny][nx]!=-2) continue;
                    // doorway block: cell pair flanked by walls on the perpendicular axis
                    if (isDoorway(map, cx, cy, nx, ny)) continue;
                    roomId[ny][nx]=rid; qx[t]=nx; qy[t]=ny; t++;
                }
            }
            rid++;
        }
        goalsInRoom = new int[rid];
        if (goals!=null){
            for (Coordinate g: goals){
                if (g==null) continue;
                if (g.y<0||g.y>=rows||g.x<0||g.x>=cols) continue;
                int r = roomId[g.y][g.x];
                if (r>=0) goalsInRoom[r]++;
            }
        }
    }

    private static boolean isDoorway(char[][] map, int x0,int y0,int x1,int y1){
        // moving vertical: doorway if both left and right of (x0,y0) OR (x1,y1) are walls
        if (x0==x1){
            boolean left0 = isWall(map, x0-1, y0), right0 = isWall(map, x0+1, y0);
            boolean left1 = isWall(map, x1-1, y1), right1 = isWall(map, x1+1, y1);
            return (left0&&right0) || (left1&&right1);
        } else { // moving horizontal
            boolean up0 = isWall(map, x0, y0-1), down0 = isWall(map, x0, y0+1);
            boolean up1 = isWall(map, x1, y1-1), down1 = isWall(map, x1, y1+1);
            return (up0&&down0) || (up1&&down1);
        }
    }

    private static boolean isWall(char[][] m,int x,int y){
        return (y<0||y>=m.length||x<0||x>=m[0].length) || m[y][x]==Constants.WALL;
    }
}
