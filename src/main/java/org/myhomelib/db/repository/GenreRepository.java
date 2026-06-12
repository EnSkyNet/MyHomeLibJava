package org.myhomelib.db.repository;

import org.myhomelib.db.DatabaseManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class GenreRepository {
    private final DatabaseManager dbManager;

    public GenreRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void insertGenre(String code, String name, String lang) {
        String sql = "INSERT OR REPLACE INTO genres (code, name, lang) VALUES (?, ?, ?)";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, code);
            pstmt.setString(2, name);
            pstmt.setString(3, lang);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Помилка вставки жанру з кодом: " + code, e);
        }
    }

    public String findGenreNameByCode(String code) {
        String sql = "SELECT name FROM genres WHERE code = ? LIMIT 1";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, code);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("name");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Помилка пошуку назви жанру за кодом: " + code, e);
        }
        return code; // Якщо перекладу немає, повертаємо початковий код
    }
}