package solver;

public class Constants {
    public static final int[] DIRECTION_X = {0, 0, -1, 1};
    public static final int[] DIRECTION_Y = {-1, 1, 0, 0};
    public static final char[] MOVES = {'u', 'd', 'l', 'r'};
    
    public static final char WALL = '#';
    public static final char GOAL = '.';
    public static final char BOX = '$';
    public static final char PLAYER = '@';

    public static final int UP = 0;
    public static final int DOWN = 1;
    public static final int LEFT = 2;
    public static final int RIGHT = 3;
    
    private Constants() {};
}
