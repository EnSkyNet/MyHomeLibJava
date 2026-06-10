package org.myhomelib.cache;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SearchCache {

    private static final int MAX_SIZE = 200;

    private static final Map<String, List<String>> cache =
            new LinkedHashMap<>(MAX_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<String>> eldest) {
                    return size() > MAX_SIZE;
                }
            };

    public static List<String> get(String key) {
        return cache.get(key);
    }

    public static void put(String key, List<String> value) {
        cache.put(key, value);
    }

    public static void clear() {
        cache.clear();
    }
}