package solver;

import java.util.Objects;

public final class Coordinate implements Comparable<Coordinate> {
    public final int x;
    public final int y;

    public Coordinate(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Coordinate)) {
            return false;
        }
        Coordinate that = (Coordinate) o;
        return x == that.x && y == that.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public int compareTo(Coordinate other) {
        if (y != other.y) {
            return Integer.compare(y, other.y);
        }
        return Integer.compare(x, other.x);
    }
}
