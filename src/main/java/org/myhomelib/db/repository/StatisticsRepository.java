package org.myhomelib.db.repository;

import org.myhomelib.db.DatabaseManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class StatisticsRepository {
    private final DatabaseManager dbManager;

    public StatisticsRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public int getBooksCount() {
        String sql = "SELECT COUNT(*) FROM books";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Помилка розрахунку загальної кількості книг", e);
        }
        return 0;
    }

    public int getAuthorsCount() {
        String sql = "SELECT COUNT(DISTINCT author_name) FROM book_authors";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Помилка розрахунку кількості унікальних авторів", e);
        }
        return 0;
    }

    public int getGenresCount() {
        String sql = "SELECT COUNT(*) FROM genres";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Помилка розрахунку кількості завантажених жанрів", e);
        }
        return 0;
    }
}