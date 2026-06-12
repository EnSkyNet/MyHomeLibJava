package org.myhomelib.db;

import org.myhomelib.model.Book;
import org.myhomelib.model.Author;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Enterprise Менеджер бази даних SQLite.
 * Повністю сумісний з CollectionManager та репозиторіями додатка.
 */
public class DatabaseManager {

    private final String dbUrl;
    private Connection activeConnection;

    // Конструктор для підтримки Path
    public DatabaseManager(Path dbPath) {
        this.dbUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath().toString();
        initializeDatabase();
    }

    // Конструктор для підтримки String
    public DatabaseManager(String dbPath) {
        this.dbUrl = "jdbc:sqlite:" + dbPath;
        initializeDatabase();
    }

    /**
     * Ініціалізація структур та швидкісних PRAGMA-налаштувань.
     */
    private void initializeDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute("PRAGMA synchronous=OFF;");
            stmt.execute("PRAGMA cache_size=-64000;");
            stmt.execute("PRAGMA foreign_keys=ON;");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS books (
                    id INTEGER PRIMARY KEY,
                    title TEXT NOT NULL,
                    series TEXT,
                    sequence_number INTEGER,
                    file_name TEXT,
                    folder TEXT,
                    archive_entry TEXT,
                    language TEXT,
                    file_size INTEGER,
                    keywords TEXT,
                    annotation TEXT,
                    rate INTEGER,
                    progress INTEGER,
                    date_time TEXT
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS authors (
                    id INTEGER PRIMARY KEY,
                    full_name TEXT NOT NULL,
                    field3 TEXT,
                    field4 TEXT
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS book_authors (
                    book_id INTEGER,
                    author_id INTEGER,
                    PRIMARY KEY (book_id, author_id),
                    FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
                    FOREIGN KEY (author_id) REFERENCES authors(id) ON DELETE CASCADE
                );
            """);

        } catch (SQLException e) {
            System.err.println("[DB ERR] Помилка ініціалізації: " + e.getMessage());
        }
    }

    /**
     * Метод open() для сумісності з CollectionManager.
     */
    public void open() throws SQLException {
        if (activeConnection == null || activeConnection.isClosed()) {
            activeConnection = getConnection();
            System.out.println("[DB] З'єднання з базою даних успішно відкрито.");
        }
    }

    /**
     * Метод close() для сумісності з CollectionManager.
     */
    public void close() throws SQLException {
        if (activeConnection != null && !activeConnection.isClosed()) {
            activeConnection.close();
            System.out.println("[DB] З'єднання з базою даних закрито.");
        }
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }

    /**
     * Високошвидкісний пакетний імпорт книг та авторів.
     */
    public void saveBooksBatch(List<Book> books) {
        String insertBookSql = """
            INSERT OR IGNORE INTO books (id, title, series, sequence_number, file_name, folder, archive_entry, language, file_size, keywords, annotation, rate, progress, date_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;

        String insertAuthorSql = "INSERT OR IGNORE INTO authors (id, full_name, field3, field4) VALUES (?, ?, ?, ?);";
        String insertRelationSql = "INSERT OR IGNORE INTO book_authors (book_id, author_id) VALUES (?, ?);";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement bookStmt = conn.prepareStatement(insertBookSql);
                 PreparedStatement authorStmt = conn.prepareStatement(insertAuthorSql);
                 PreparedStatement relStmt = conn.prepareStatement(insertRelationSql)) {

                for (Book book : books) {
                    bookStmt.setLong(1, book.id());
                    bookStmt.setString(2, book.title());
                    bookStmt.setString(3, book.series());
                    bookStmt.setInt(4, book.sequenceNumber() != null ? book.sequenceNumber() : 0);
                    bookStmt.setString(5, book.fileName());
                    bookStmt.setString(6, book.folder());
                    bookStmt.setString(7, book.archiveEntry());
                    bookStmt.setString(8, book.language());
                    bookStmt.setLong(9, book.fileSize());
                    bookStmt.setString(10, book.keywords());
                    bookStmt.setString(11, book.annotation());
                    bookStmt.setInt(12, book.rate());
                    bookStmt.setInt(13, book.progress());
                    bookStmt.setString(14, java.time.LocalDateTime.now().toString());
                    bookStmt.addBatch();

                    if (book.authors() != null) {
                        for (Author author : book.authors()) {
                            long authId = author.id();
                            String authName = "";
                            String f3 = "";
                            String f4 = "";

                            try {
                                java.lang.reflect.RecordComponent[] components = Author.class.getRecordComponents();
                                if (components != null && components.length >= 2) {
                                    authName = (String) components[1].getAccessor().invoke(author);
                                    if (components.length > 2) f3 = (String) components[2].getAccessor().invoke(author);
                                    if (components.length > 3) f4 = (String) components[3].getAccessor().invoke(author);
                                }
                            } catch (Exception e) {
                                authName = author.toString();
                            }

                            authorStmt.setLong(1, authId);
                            authorStmt.setString(2, authName != null ? authName : "Невідомий автор");
                            authorStmt.setString(3, f3 != null ? f3 : "");
                            authorStmt.setString(4, f4 != null ? f4 : "");
                            authorStmt.addBatch();

                            relStmt.setLong(1, book.id());
                            relStmt.setLong(2, author.id());
                            relStmt.addBatch();
                        }
                    }
                }

                bookStmt.executeBatch();
                authorStmt.executeBatch();
                relStmt.executeBatch();

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            System.err.println("[DB ERR] Помилка пакетного збереження: " + e.getMessage());
        }
    }
}