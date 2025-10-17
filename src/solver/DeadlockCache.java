package solver;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

public final class DeadlockCache {
    private static final int CAP = 8192;
    private static final LinkedHashMap<Long, Boolean> map = new LinkedHashMap<>(CAP, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
            return size() > CAP;
        }
    };
    private static final long P = 1099511628211L;

    private DeadlockCache() {}

    public static void clear() {
        map.clear();
    }

    public static boolean getOrCompute(Coordinate[] sortedBoxes, BooleanSupplier supplier) {
        long key = 1469598103934665603L;
        for (Coordinate c : sortedBoxes) {
            key = (key ^ c.x) * P;
            key = (key ^ c.y) * P;
        }
        Boolean cached = map.get(key);
        if (cached != null) {
            return cached;
        }
        boolean result = supplier.getAsBoolean();
        map.put(key, result);
        return result;
    }
}
