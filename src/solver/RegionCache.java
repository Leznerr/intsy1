package solver;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

public final class RegionCache {
    private static final int CAP = 16384;
    private static final LinkedHashMap<Long, Boolean> map = new LinkedHashMap<>(CAP, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
            return size() > CAP;
        }
    };
    private static final long P = 1099511628211L;

    private RegionCache() {}

    public static void clear() {
        map.clear();
    }

    public static boolean getOrCompute(Coordinate[] boxes, int movedIdx, int destX, int destY, BooleanSupplier supplier) {
        long key = computeKey(boxes, movedIdx, destX, destY);
        Boolean cached = map.get(key);
        if (cached != null) {
            return cached.booleanValue();
        }
        boolean result = supplier.getAsBoolean();
        map.put(key, result);
        return result;
    }

    private static long computeKey(Coordinate[] boxes, int movedIdx, int destX, int destY) {
        long key = 1469598103934665603L;
        boolean destInserted = false;
        for (int i = 0; i < boxes.length; i++) {
            if (i == movedIdx) {
                continue;
            }
            Coordinate c = boxes[i];
            if (c == null) {
                continue;
            }
            if (!destInserted) {
                int cmp = compare(destX, destY, c.x, c.y);
                if (cmp <= 0) {
                    key = hashCoordinate(key, destX, destY);
                    destInserted = true;
                    i--; // reprocess current coordinate
                    continue;
                }
            }
            key = hashCoordinate(key, c.x, c.y);
        }
        if (!destInserted) {
            key = hashCoordinate(key, destX, destY);
        }
        key = (key ^ boxes.length) * P;
        return key;
    }

    private static long hashCoordinate(long key, int x, int y) {
        key = (key ^ x) * P;
        return (key ^ y) * P;
    }

    private static int compare(int ax, int ay, int bx, int by) {
        if (ay != by) {
            return Integer.compare(ay, by);
        }
        return Integer.compare(ax, bx);
    }
}
