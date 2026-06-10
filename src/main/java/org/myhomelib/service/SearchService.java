package org.myhomelib.db;

import org.myhomelib.cache.SearchCache;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SearchService {

    private static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    /**
     * FTS + cache + fast lookup
     */
    public static List<String> search(String query) throws SQLException {

        String q = normalize(query);

        // CACHE HIT
        List<String> cached = SearchCache.get(q);
        if (cached != null) return cached;

        Connection conn = Database.getConnection();

        String sql = """
            SELECT b.title
            FROM books_fts f
            JOIN books b ON b.id = f.rowid
            WHERE books_fts MATCH ?
            LIMIT 500
        """;

        List<String> results = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, q);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                results.add(rs.getString("title"));
            }
        }

        SearchCache.put(q, results);

        return results;
    }
}