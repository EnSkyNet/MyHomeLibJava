package org.myhomelib.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.nio.file.Path;

public class DatabaseManager {
    private Connection connection;
    private final Path dbPath;

    public DatabaseManager(Path dbPath) {
        this.dbPath = dbPath;
    }

    public void open() {
        try {
            if (connection == null || connection.isClosed()) {
                String url = "jdbc:sqlite:" + dbPath.toAbsolutePath().toString();
                this.connection = DriverManager.getConnection(url);

                // Увімкнення системних прагм для максимальної продуктивності SQLite
                try (var stmt = connection.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = ON;");
                    stmt.execute("PRAGMA journal_mode = WAL;"); // Режим Write-Ahead Logging для паралельного доступу
                    stmt.execute("PRAGMA synchronous = NORMAL;"); // Оптимізація швидкості запису на диск

                    // Створення структури таблиць (якщо вони відсутні)
                    stmt.execute("CREATE TABLE IF NOT EXISTS books (" +
                            "id INTEGER PRIMARY KEY, " +
                            "title TEXT, " +
                            "authors TEXT, " +
                            "series TEXT, " +
                            "language TEXT, " +
                            "genre TEXT, " +
                            "file_name TEXT, " +
                            "folder TEXT, " +
                            "archive_entry TEXT, " +
                            "file_size INTEGER, " +
                            "keywords TEXT, " +
                            "annotation TEXT, " +
                            "rate INTEGER, " +
                            "progress INTEGER, " +
                            "update_date TEXT" +
                            ");");

                    stmt.execute("CREATE TABLE IF NOT EXISTS book_authors (" +
                            "book_id INTEGER, " +
                            "author_name TEXT, " +
                            "PRIMARY KEY (book_id, author_name)" +
                            ");");

                    stmt.execute("CREATE TABLE IF NOT EXISTS genres (" +
                            "code TEXT PRIMARY KEY, " +
                            "name TEXT, " +
                            "lang TEXT" +
                            ");");

                    // ==========================================
                    // АВТОМАТИЧНЕ СТВОРЕННЯ ОПТИМІЗОВАНИХ ІНДЕКСІВ
                    // ==========================================
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_books_title ON books(title);");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_books_series ON books(series);");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_books_language ON books(language);");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_books_genre ON books(genre);");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_book_authors_name ON book_authors(author_name);");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_book_authors_book_id ON book_authors(book_id);");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_genres_code ON genres(code);");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Не вдалося відкрити підключення або ініціалізувати індекси баз даних", e);
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Помилка під час закриття підключення до бази даних", e);
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                open();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Підключення закрите або не ініціалізоване", e);
        }
        return connection;
    }

    public void beginTransaction() {
        try {
            getConnection().setAutoCommit(false);
        } catch (SQLException e) {
            throw new RuntimeException("Не вдалося запустити транзакцію", e);
        }
    }

    public void commitTransaction() {
        try {
            if (!getConnection().getAutoCommit()) {
                getConnection().commit();
                getConnection().setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Не вдалося зафіксувати транзакцію (commit)", e);
        }
    }

    public void rollbackTransaction() {
        try {
            if (!getConnection().getAutoCommit()) {
                getConnection().rollback();
                getConnection().setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Не вдалося відкотити транзакцію (rollback)", e);
        }
    }
}