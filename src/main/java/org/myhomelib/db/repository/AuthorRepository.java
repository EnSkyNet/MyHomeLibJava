package org.myhomelib.db.repository;

import org.myhomelib.db.DatabaseManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AuthorRepository {
    private final DatabaseManager dbManager;

    public AuthorRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void insertAuthor(long bookId, String authorName) {
        String sql = "INSERT OR IGNORE INTO book_authors (book_id, author_name) VALUES (?, ?)";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, bookId);
            pstmt.setString(2, authorName != null ? authorName.trim() : "Невідомий Автор");
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Помилка додавання автора для книги ID: " + bookId, e);
        }
    }

    public String findAuthorForBook(long bookId) {
        String sql = "SELECT author_name FROM book_authors WHERE book_id = ? LIMIT 1";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, bookId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("author_name");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Помилка пошуку автора для книги ID: " + bookId, e);
        }
        return "Невідомий Автор";
    }
}