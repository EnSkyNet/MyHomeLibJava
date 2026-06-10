package org.myhomelib.db;

import java.sql.*;

public class Database {

    private static final String DB_URL = "jdbc:sqlite:myhomelib.db";
    private static Connection connection;

    /**
     * Ініціалізація підключення до SQLite
     */
    public static synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(DB_URL);
            connection.setAutoCommit(false); // КРИТИЧНО для продуктивності
        }
        return connection;
    }

    /**
     * Ініціалізація схеми БД
     * Викликається при старті застосунку
     */
    public static void initDatabase() throws SQLException {
        Connection conn = getConnection();

        try (Statement stmt = conn.createStatement()) {

            // BOOKS
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS books (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    series_id INTEGER,
                    year INTEGER,
                    hash TEXT UNIQUE
                )
            """);

            // AUTHORS
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS authors (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE,
                    name_normalized TEXT NOT NULL
                )
            """);

            // BOOK_AUTHORS
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS book_authors (
                    book_id INTEGER NOT NULL,
                    author_id INTEGER NOT NULL,
                    UNIQUE(book_id, author_id)
                )
            """);

            // GENRES
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS genres (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code TEXT NOT NULL UNIQUE,
                    alias TEXT NOT NULL
                )
            """);

            // BOOK_GENRES
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS book_genres (
                    book_id INTEGER NOT NULL,
                    genre_id INTEGER NOT NULL,
                    UNIQUE(book_id, genre_id)
                )
            """);

            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        }
    }

    /**
     * Створення індексів для продуктивності (500k+ книг)
     */
    public static void createIndexes() throws SQLException {
        Connection conn = getConnection();

        try (Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_books_title ON books(title)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_authors_norm ON authors(name_normalized)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_book_authors_author ON book_authors(author_id)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_book_genres_genre ON book_genres(genre_id)");

            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        }
    }

    /**
     * Закриття з’єднання
     */
    public static void shutdown() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.commit();
                connection.close();
            }
        } catch (SQLException ignored) {}
    }
}