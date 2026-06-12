package org.myhomelib.db.repository;

import org.myhomelib.db.DatabaseManager;
import org.myhomelib.model.Book;
import org.myhomelib.model.Author;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class BookRepository {
    private final DatabaseManager dbManager;
    private final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public BookRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void saveBook(Book book) {
        String sql = "INSERT INTO books (id, title, authors, series, language, genre, file_name, folder, archive_entry, file_size, keywords, annotation, rate, progress, update_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, book.id());
            pstmt.setString(2, book.title());
            pstmt.setString(3, book.authorsText());
            pstmt.setString(4, book.series());
            pstmt.setString(5, book.language());
            pstmt.setString(6, book.genresText());
            pstmt.setString(7, book.fileName());
            pstmt.setString(8, book.folder());
            pstmt.setString(9, book.archiveEntry());
            pstmt.setLong(10, book.fileSize());
            pstmt.setString(11, book.keywords());
            pstmt.setString(12, book.annotation());
            pstmt.setInt(13, book.rate());
            pstmt.setInt(14, book.progress());
            pstmt.setString(15, book.updateDate() != null ? book.updateDate().format(formatter) : LocalDateTime.now().format(formatter));
            pstmt.executeUpdate();

            String ftsSql = "INSERT INTO books_fts(rowid, title, authors, genres) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ftsPstmt = conn.prepareStatement(ftsSql)) {
                ftsPstmt.setLong(1, book.id());
                ftsPstmt.setString(2, book.title());
                ftsPstmt.setString(3, book.authorsText());
                ftsPstmt.setString(4, book.genresText());
                ftsPstmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Помилка збереження книги з ID: " + book.id(), e);
        }
    }

    public void updateBook(Book book) {
        String sql = "UPDATE books SET title = ?, authors = ?, series = ?, language = ?, genre = ?, rate = ?, progress = ?, update_date = ? WHERE id = ?";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, book.title());
            pstmt.setString(2, book.authorsText());
            pstmt.setString(3, book.series());
            pstmt.setString(4, book.language());
            pstmt.setString(5, book.genresText());
            pstmt.setInt(6, book.rate());
            pstmt.setInt(7, book.progress());
            pstmt.setString(8, book.updateDate() != null ? book.updateDate().format(formatter) : LocalDateTime.now().format(formatter));
            pstmt.setLong(9, book.id());
            pstmt.executeUpdate();

            String ftsSql = "UPDATE books_fts SET title = ?, authors = ?, genres = ? WHERE rowid = ?";
            try (PreparedStatement ftsPstmt = conn.prepareStatement(ftsSql)) {
                ftsPstmt.setString(1, book.title());
                ftsPstmt.setString(2, book.authorsText());
                ftsPstmt.setString(3, book.genresText());
                ftsPstmt.setLong(4, book.id());
                ftsPstmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Помилка оновлення книги з ID: " + book.id(), e);
        }
    }

    public void deleteBook(long id) {
        String sql = "DELETE FROM books WHERE id = ?";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            pstmt.executeUpdate();

            String ftsSql = "DELETE FROM books_fts WHERE rowid = ?";
            try (PreparedStatement ftsPstmt = conn.prepareStatement(ftsSql)) {
                ftsPstmt.setLong(1, id);
                ftsPstmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Помилка видалення книги з ID: " + id, e);
        }
    }

    public Book findBook(long id) {
        String sql = "SELECT * FROM books WHERE id = ?";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String dateStr = rs.getString("update_date");
                    LocalDateTime ldt = (dateStr != null && !dateStr.isBlank()) ? LocalDateTime.parse(dateStr, formatter) : LocalDateTime.now();

                    List<Author> authorsList = parseAuthors(rs.getString("authors"));
                    List<String> genresList = parseGenres(rs.getString("genre"));

                    return new Book(
                            rs.getLong("id"),
                            rs.getString("title"),
                            authorsList,
                            genresList,
                            rs.getString("series"),
                            rs.getInt("rate"),
                            rs.getString("file_name"),
                            rs.getString("folder"),
                            rs.getString("archive_entry"),
                            rs.getString("language"),
                            rs.getLong("file_size"),
                            rs.getString("keywords"),
                            rs.getString("annotation"),
                            rs.getInt("progress"),
                            0,
                            ldt
                    );
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Помилка пошуку книги за ID: " + id, e);
        }
        return null;
    }

    private List<Author> parseAuthors(String authorsText) {
        List<Author> list = new ArrayList<>();
        if (authorsText != null && !authorsText.isBlank()) {
            for (String name : authorsText.split(",")) {
                if (!name.strip().isEmpty()) {
                    list.add(new Author(0L, name.strip(), "", ""));
                }
            }
        }
        return list;
    }

    private List<String> parseGenres(String genresText) {
        List<String> list = new ArrayList<>();
        if (genresText != null && !genresText.isBlank()) {
            for (String g : genresText.split(",")) {
                if (!g.strip().isEmpty()) {
                    list.add(g.strip());
                }
            }
        }
        return list;
    }
}