package solver;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntSupplier;

public final class AssignCache {
    private static final int CAP = 8192;
    private static final LinkedHashMap<Long, Integer> map = new LinkedHashMap<>(CAP, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Integer> eldest) {
            return size() > CAP;
        }
    };
    private static final long P = 1099511628211L;

    private AssignCache() {}

    public static void clear() {
        map.clear();
    }

    public static int getOrCompute(Coordinate[] sortedBoxes, IntSupplier compute) {
        long key = 1469598103934665603L;
        for (Coordinate c : sortedBoxes) {
            key = (key ^ c.x) * P;
            key = (key ^ c.y) * P;
        }
        Integer cached = map.get(key);
        if (cached != null) {
            return cached;
        }
        int result = compute.getAsInt();
        map.put(key, result);
        return result;
    }
}
//1