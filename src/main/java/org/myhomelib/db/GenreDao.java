package org.myhomelib.db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GenreDao {

    /**
     * Отримати жанри книги (UI-ready alias)
     */
    public static List<String> getGenresForBook(long bookId) throws SQLException {

        Connection conn = Database.getConnection();

        String sql = """
            SELECT g.alias
            FROM book_genres bg
            JOIN genres g ON g.id = bg.genre_id
            WHERE bg.book_id = ?
        """;

        List<String> genres = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, bookId);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                genres.add(rs.getString("alias"));
            }
        }

        return genres;
    }
}