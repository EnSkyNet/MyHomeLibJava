package org.myhomelib.db.repository;

import org.myhomelib.db.DatabaseManager;
import org.myhomelib.model.Book;
import org.myhomelib.model.Author;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Репозиторій для управління об'єктами книг у SQLite.
 * Містить методи-аліаси для повної сумісності із застарілими викликами в сервісах та UI.
 */
public class BookRepository {

    private final DatabaseManager databaseManager;

    public BookRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Аліас для сумісності з LibraryViewModel.
     */
    public List<Book> getBooks() {
        return findAll();
    }

    /**
     * Аліас для сумісності з BookImportTask, BookImportWorker та ImportService.
     */
    public void saveBook(Book book) {
        save(book);
    }

    /**
     * Знайти всі книги в базі даних.
     */
    public List<Book> findAll() {
        List<Book> books = new ArrayList<>();
        String sql = "SELECT * FROM books ORDER BY title ASCII;";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                books.add(mapResultSetToBook(rs));
            }
        } catch (SQLException e) {
            System.err.println("[REPO ERR] Помилка findAll у BookRepository: " + e.getMessage());
            throw new RuntimeException(e);
        }
        return books;
    }

    /**
     * Знайти книгу за її унікальним ідентифікатором ID.
     */
    public Book findById(long id) {
        String sql = "SELECT * FROM books WHERE id = ?;";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToBook(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[REPO ERR] Помилка findById у BookRepository: " + e.getMessage());
            throw new RuntimeException(e);
        }
        return null;
    }

    /**
     * Пошук книг за назвою (LIKE шаблон).
     */
    public List<Book> findByTitle(String titlePattern) {
        List<Book> books = new ArrayList<>();
        String sql = "SELECT * FROM books WHERE title LIKE ? ORDER BY title ASCII;";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, "%" + titlePattern + "%");
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    books.add(mapResultSetToBook(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[REPO ERR] Помилка findByTitle у BookRepository: " + e.getMessage());
            throw new RuntimeException(e);
        }
        return books;
    }

    /**
     * Збереження нової або оновлення існуючої книги в базі даних.
     */
    public void save(Book book) {
        String sql = """
            INSERT OR REPLACE INTO books (id, title, series, sequence_number, file_name, folder, archive_entry, language, file_size, keywords, annotation, rate, progress, date_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, book.id());
            pstmt.setString(2, book.title());
            pstmt.setString(3, book.series());
            pstmt.setInt(4, book.sequenceNumber() != null ? book.sequenceNumber() : 0);
            pstmt.setString(5, book.fileName());
            pstmt.setString(6, book.folder());
            pstmt.setString(7, book.archiveEntry());
            pstmt.setString(8, book.language());
            pstmt.setLong(9, book.fileSize());
            pstmt.setString(10, book.keywords());
            pstmt.setString(11, book.annotation());
            pstmt.setInt(12, book.rate());
            pstmt.setInt(13, book.progress());

            pstmt.setString(14, book.updateDate() != null ? book.updateDate().toString() : LocalDateTime.now().toString());

            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[REPO ERR] Помилка save у BookRepository: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Видалення книги з індексу бази даних за ID.
     */
    public void deleteById(long id) {
        String sql = "DELETE FROM books WHERE id = ?;";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[REPO ERR] Помилка deleteById у BookRepository: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private Book mapResultSetToBook(ResultSet rs) throws SQLException {
        List<Author> authorsList = new ArrayList<>();
        List<String> genresList = new ArrayList<>();

        String dtStr = rs.getString("date_time");
        LocalDateTime bookDate = (dtStr == null || dtStr.isBlank()) ? LocalDateTime.now() : LocalDateTime.parse(dtStr);

        return new Book(
                rs.getLong("id"),
                rs.getString("title"),
                authorsList,
                genresList,
                rs.getString("series"),
                rs.getInt("sequence_number"),
                rs.getString("language"),
                rs.getString("file_name"),
                rs.getString("folder"),
                rs.getString("archive_entry"),
                rs.getLong("file_size"),
                rs.getString("keywords"),
                rs.getString("annotation"),
                rs.getInt("rate"),
                rs.getInt("progress"),
                bookDate
        );
    }
}