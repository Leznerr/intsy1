package solver;

import java.util.Arrays;

public final class Rooms {
    public static int rows, cols;
    public static int[][] roomId;      // -1 wall, [0..R)
    public static int[] goalsInRoom;

    private Rooms(){}

    private static boolean isDoorway(char[][] map, int x, int y){
        if (map[y][x] == Constants.WALL) return false;
        boolean horiz = (x-1<0 || map[y][x-1]==Constants.WALL) && (x+1>=map[0].length || map[y][x+1]==Constants.WALL);
        boolean vert  = (y-1<0 || map[y-1][x]==Constants.WALL) && (y+1>=map.length    || map[y+1][x]==Constants.WALL);
        return horiz || vert;
    }

    public static void build(char[][] map, Coordinate[] goals){
        rows = map.length; cols = rows==0?0:map[0].length;
        roomId = new int[rows][cols];
        for (int y=0;y<rows;y++) Arrays.fill(roomId[y], -2);
        int rid = 0;
        int[] qx = new int[rows*cols], qy = new int[rows*cols];
        for (int y=0;y<rows;y++) for (int x=0;x<cols;x++){
            if (map[y][x]==Constants.WALL){ roomId[y][x] = -1; continue; }
            if (roomId[y][x]!=-2) continue;

            int h=0,t=0; qx[t]=x; qy[t]=y; t++; roomId[y][x]=rid;
            while(h<t){
                int cx=qx[h], cy=qy[h]; h++;
                for (int d=0; d<Constants.DIRECTION_X.length; d++){
                    int nx=cx+Constants.DIRECTION_X[d], ny=cy+Constants.DIRECTION_Y[d];
                    if (nx<0||ny<0||ny>=rows||nx>=cols) continue;
                    if (map[ny][nx]==Constants.WALL) { roomId[ny][nx] = -1; continue; }
                    if (isDoorway(map, cx, cy) || isDoorway(map, nx, ny)) continue;
                    if (roomId[ny][nx]!=-2) continue;
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
}
