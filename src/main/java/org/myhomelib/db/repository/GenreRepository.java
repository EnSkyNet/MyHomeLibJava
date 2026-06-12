package org.myhomelib.db.repository;

import org.myhomelib.db.DatabaseManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Репозиторій для управління жанрами у SQLite.
 * Безпечно обробляє SQLException для інтеграції з JavaFX.
 */
public class GenreRepository {

    private final DatabaseManager databaseManager;

    public GenreRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Отримати список усіх унікальних жанрів із бази даних.
     */
    public List<String> findAll() {
        List<String> genres = new ArrayList<>();
        String sql = "SELECT DISTINCT name FROM genres ORDER BY name ASCII;";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                String genre = rs.getString("name");
                if (genre != null && !genre.isBlank()) {
                    genres.add(genre);
                }
            }
        } catch (SQLException e) {
            System.err.println("[REPO ERR] Помилка findAll у GenreRepository: " + e.getMessage());
            throw new RuntimeException(e);
        }
        return genres;
    }

    /**
     * Зберегти новий жанр у базу даних.
     */
    public void save(String genreName) {
        if (genreName == null || genreName.isBlank()) return;

        String sql = "INSERT OR IGNORE INTO genres (name) VALUES (?);";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, genreName.trim());
            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[REPO ERR] Помилка save у GenreRepository: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}