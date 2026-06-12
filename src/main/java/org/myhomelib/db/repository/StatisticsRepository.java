package org.myhomelib.db.repository;

import org.myhomelib.db.DatabaseManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Репозиторій для збору аналітики та статистичних даних по бібліотеці.
 * Безпечно обробляє SQLException для інтеграції з UI JavaFX.
 */
public class StatisticsRepository {

    private final DatabaseManager databaseManager;

    public StatisticsRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Отримати загальну кількість книг у базі даних.
     */
    public long getTotalBooksCount() {
        String sql = "SELECT COUNT(*) FROM books;";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            System.err.println("[STATS REPO ERR] Помилка getTotalBooksCount: " + e.getMessage());
            throw new RuntimeException(e);
        }
        return 0;
    }

    /**
     * Отримати загальну кількість унікальних авторів у системі.
     */
    public long getTotalAuthorsCount() {
        String sql = "SELECT COUNT(*) FROM authors;";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            System.err.println("[STATS REPO ERR] Помилка getTotalAuthorsCount: " + e.getMessage());
            throw new RuntimeException(e);
        }
        return 0;
    }

    /**
     * Отримати сумарний розмір усіх зареєстрованих файлів книг у байтах.
     */
    public long getTotalLibrarySizeInBytes() {
        String sql = "SELECT TOTAL(file_size) FROM books;";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            System.err.println("[STATS REPO ERR] Помилка getTotalLibrarySizeInBytes: " + e.getMessage());
            throw new RuntimeException(e);
        }
        return 0;
    }
}