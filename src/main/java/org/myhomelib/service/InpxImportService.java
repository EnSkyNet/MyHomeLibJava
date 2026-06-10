package org.myhomelib.importer;

import org.myhomelib.db.Database;

import java.sql.*;
import java.util.Locale;

public class InpxImportService {

    /**
     * Нормалізація рядка
     */
    private static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    /**
     * Хеш книги для дедуплікації
     */
    private static String buildBookHash(String title, String author, Integer year) {
        return normalize(title) + "|" + normalize(author) + "|" + (year == null ? "" : year);
    }

    /**
     * UPSERT автора
     */
    private static long getOrCreateAuthor(Connection conn, String authorName) throws SQLException {

        String norm = normalize(authorName);

        // 1. пошук існуючого
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM authors WHERE name_normalized = ? LIMIT 1"
        )) {
            ps.setString(1, norm);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getLong("id");
            }
        }

        // 2. вставка нового
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO authors(name, name_normalized) VALUES(?, ?)",
                Statement.RETURN_GENERATED_KEYS
        )) {
            ps.setString(1, authorName);
            ps.setString(2, norm);
            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) return rs.getLong(1);
        }

        throw new SQLException("Cannot create author");
    }

    /**
     * Перевірка чи книга вже існує
     */
    private static boolean bookExists(Connection conn, String hash) throws SQLException {

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM books WHERE hash = ? LIMIT 1"
        )) {
            ps.setString(1, hash);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        }
    }

    /**
     * Основний імпорт книги
     */
    public static void importBook(Connection conn,
                                  String title,
                                  String author,
                                  Integer year,
                                  String genreCode) throws SQLException {

        String hash = buildBookHash(title, author, year);

        // якщо книга вже є → пропуск
        if (bookExists(conn, hash)) return;

        long authorId = getOrCreateAuthor(conn, author);

        try {
            conn.setAutoCommit(false);

            // 1. вставка книги
            long bookId;

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO books(title, year, hash) VALUES(?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            )) {
                ps.setString(1, title);
                ps.setInt(2, year == null ? 0 : year);
                ps.setString(3, hash);
                ps.executeUpdate();

                ResultSet rs = ps.getGeneratedKeys();
                rs.next();
                bookId = rs.getLong(1);
            }

            // 2. зв'язок книга-автор
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO book_authors(book_id, author_id) VALUES(?, ?)"
            )) {
                ps.setLong(1, bookId);
                ps.setLong(2, authorId);
                ps.executeUpdate();
            }

            // 3. жанр (через code → id)
            long genreId = getGenreId(conn, genreCode);

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO book_genres(book_id, genre_id) VALUES(?, ?)"
            )) {
                ps.setLong(1, bookId);
                ps.setLong(2, genreId);
                ps.executeUpdate();
            }

            conn.commit();

        } catch (SQLException e) {
            conn.rollback();
            throw e;
        }
    }

    /**
     * Отримання жанру по code
     */
    private static long getGenreId(Connection conn, String code) throws SQLException {

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM genres WHERE code = ?"
        )) {
            ps.setString(1, code);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) return rs.getLong(1);
        }

        throw new SQLException("Genre not found: " + code);
    }
}