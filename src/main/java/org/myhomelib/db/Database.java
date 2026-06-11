package org.myhomelib.db;

import org.myhomelib.model.Book;
import org.myhomelib.model.Fb2Book;
import org.myhomelib.model.BookEdit;
import org.myhomelib.model.SearchCriteria;
import org.myhomelib.model.SearchPreset;
import org.myhomelib.model.Author;

import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class Database implements BookCollection {

    public record GenreImport(String code, String name) {}

    private Connection connection;
    private Path currentDbPath;

    public Database(Path dbPath) {
        this.currentDbPath = dbPath;
    }

    @Override
    public void open(Path dbPath) {
        try {
            if (connection != null && !connection.isClosed()) {
                return;
            }
            this.currentDbPath = dbPath;
            String url = "jdbc:sqlite:" + dbPath.toAbsolutePath().toString();
            this.connection = DriverManager.getConnection(url);

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON;");
                stmt.execute("PRAGMA journal_mode = WAL;");
                stmt.execute("PRAGMA synchronous = NORMAL;");
            }

            initSchema();
        } catch (SQLException e) {
            throw new RuntimeException("Не вдалося відкрити підключення до бази даних SQLite", e);
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Помилка під час закриття бази даних", e);
        }
    }

    @Override
    public boolean isOpen() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    private void initSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS books (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    language TEXT,
                    series_name TEXT,
                    archive_name TEXT,
                    file_name TEXT
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS authors (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT UNIQUE NOT NULL
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

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS genres (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code TEXT UNIQUE NOT NULL,
                    name TEXT NOT NULL
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS book_genres (
                    book_id INTEGER,
                    genre_id INTEGER,
                    PRIMARY KEY (book_id, genre_id),
                    FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
                    FOREIGN KEY (genre_id) REFERENCES genres(id) ON DELETE CASCADE
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS search_presets (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT UNIQUE NOT NULL,
                    title_filter TEXT,
                    author_filter TEXT,
                    genre_filter TEXT,
                    series_filter TEXT
                );
            """);
        }
    }

    @Override
    public int getBooksCount() {
        String sql = "SELECT COUNT(*) FROM books";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Помилка отримання кількості книг", e);
        }
        return 0;
    }

    @Override
    public int getAuthorsCount() {
        String sql = "SELECT COUNT(*) FROM authors";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Помилка отримання кількості авторів", e);
        }
        return 0;
    }

    @Override
    public int getGenresCount() {
        String sql = "SELECT COUNT(*) FROM genres";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Помилка отримання кількості жанрів", e);
        }
        return 0;
    }

    @Override
    public int importBooks(List<Fb2Book> books) {
        String insertBookSql = "INSERT INTO books (title, language, series_name, archive_name, file_name) VALUES (?, ?, ?, ?, ?)";
        String insertAuthorSql = "INSERT OR IGNORE INTO authors (name) VALUES (?)";
        String selectAuthorSql = "SELECT id FROM authors WHERE name = ?";
        String linkAuthorSql = "INSERT OR IGNORE INTO book_authors (book_id, author_id) VALUES (?, ?)";

        int insertedCount = 0;

        try {
            beginTransaction();

            try (PreparedStatement psBook = connection.prepareStatement(insertBookSql, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement psAuthor = connection.prepareStatement(insertAuthorSql);
                 PreparedStatement psSelectAuthor = connection.prepareStatement(selectAuthorSql);
                 PreparedStatement psLinkAuthor = connection.prepareStatement(linkAuthorSql)) {

                for (Fb2Book fb2 : books) {
                    psBook.setString(1, fb2.title());
                    psBook.setString(2, fb2.language());
                    psBook.setString(3, fb2.series());
                    psBook.setString(4, fb2.archiveEntry());

                    String fileNameStr = "";
                    if (fb2.sourcePath() != null && fb2.sourcePath().getFileName() != null) {
                        fileNameStr = fb2.sourcePath().getFileName().toString();
                    }
                    psBook.setString(5, fileNameStr);
                    psBook.executeUpdate();

                    long bookId;
                    try (ResultSet generatedKeys = psBook.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            bookId = generatedKeys.getLong(1);
                        } else {
                            continue;
                        }
                    }

                    if (fb2.authors() != null) {
                        for (Author authorObj : fb2.authors()) {
                            String authorName = authorObj.displayFullName();
                            if (authorName == null || authorName.strip().isEmpty()) {
                                continue;
                            }

                            psAuthor.setString(1, authorName);
                            psAuthor.executeUpdate();

                            psSelectAuthor.setString(1, authorName);
                            long authorId;
                            try (ResultSet rs = psSelectAuthor.executeQuery()) {
                                if (rs.next()) {
                                    authorId = rs.getLong(1);
                                } else {
                                    continue;
                                }
                            }

                            psLinkAuthor.setLong(1, bookId);
                            psLinkAuthor.setLong(2, authorId);
                            psLinkAuthor.executeUpdate();
                        }
                    }
                    insertedCount++;
                }
            }
            commitTransaction();
        } catch (SQLException e) {
            rollbackTransaction();
            throw new RuntimeException("Помилка транзакційного пакетного імпорту книг", e);
        }

        return insertedCount;
    }

    @Override
    public void updateBookFields(long bookId, BookEdit editData) {
        // Оновлено: SQL-запит скориговано під доступні поля рекорду BookEdit (title, language)
        String sql = "UPDATE books SET title = ?, language = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, editData.title());
            pstmt.setString(2, editData.language());
            pstmt.setLong(3, bookId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Помилка оновлення полей книги", e);
        }
    }

    @Override
    public List<Book> findBooks(SearchCriteria criteria) {
        return searchAdvanced(criteria);
    }

    @Override
    public List<Book> searchBooks(String keyword) {
        SearchCriteria criteria = new SearchCriteria(keyword, "", "", "", "", null, null, null, null, null, null, "", "", "", "", "");
        return searchAdvanced(criteria);
    }

    @Override
    public List<Book> searchAdvanced(SearchCriteria criteria) {
        List<Book> books = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT DISTINCT b.* FROM books b " +
                        "LEFT JOIN book_authors ba ON b.id = ba.book_id " +
                        "LEFT JOIN authors a ON ba.author_id = a.id " +
                        "LEFT JOIN book_genres bg ON b.id = bg.book_id " +
                        "LEFT JOIN genres g ON bg.genre_id = g.id " +
                        "WHERE 1=1 "
        );

        List<Object> params = new ArrayList<>();

        if (criteria.title() != null && !criteria.title().strip().isEmpty()) {
            sql.append("AND b.title LIKE ? ");
            params.add("%" + criteria.title().strip() + "%");
        }
        if (criteria.author() != null && !criteria.author().strip().isEmpty()) {
            sql.append("AND a.name LIKE ? ");
            params.add("%" + criteria.author().strip() + "%");
        }
        if (criteria.genre() != null && !criteria.genre().strip().isEmpty()) {
            sql.append("AND (g.name LIKE ? OR g.code LIKE ?) ");
            params.add("%" + criteria.genre().strip() + "%");
            params.add("%" + criteria.genre().strip() + "%");
        }
        if (criteria.series() != null && !criteria.series().strip().isEmpty()) {
            sql.append("AND b.series_name LIKE ? ");
            params.add("%" + criteria.series().strip() + "%");
        }

        sql.append("ORDER BY b.title ASC LIMIT 500");

        try (PreparedStatement pstmt = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    String title = rs.getString("title");
                    String language = rs.getString("language");
                    String seriesName = rs.getString("series_name");
                    String archiveName = rs.getString("archive_name");
                    String fileName = rs.getString("file_name");

                    Book book = new Book(
                            id,
                            title,
                            new ArrayList<>(),
                            new ArrayList<>(),
                            language,
                            0,
                            seriesName,
                            "",
                            archiveName,
                            fileName,
                            0L,
                            "",
                            "",
                            0,
                            0,
                            null
                    );

                    books.add(book);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Помилка виконання розширеного фільтра пошуку", e);
        }
        return books;
    }

    @Override
    public List<String> listAuthors() {
        List<String> authors = new ArrayList<>();
        String sql = "SELECT name FROM authors ORDER BY name ASC";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                authors.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return authors;
    }

    @Override
    public List<String> listSeries() {
        List<String> series = new ArrayList<>();
        String sql = "SELECT DISTINCT series_name FROM books WHERE series_name IS NOT NULL ORDER BY series_name ASC";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                series.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return series;
    }

    @Override
    public List<String> listGenres() {
        List<String> genres = new ArrayList<>();
        String sql = "SELECT name FROM genres ORDER BY name ASC";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                genres.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return genres;
    }

    @Override
    public String statistics() {
        return "Книг: " + getBooksCount() + ", Авторів: " + getAuthorsCount() + ", Жанрів: " + getGenresCount();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void importGenreList(List<?> genres, String lang) {
        String sql = "INSERT OR REPLACE INTO genres (code, name) VALUES (?, ?)";
        try {
            beginTransaction();
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                for (Object obj : genres) {
                    if (obj instanceof GenreImport gi) {
                        pstmt.setString(1, gi.code());
                        pstmt.setString(2, gi.name());
                        pstmt.addBatch();
                    }
                }
                pstmt.executeBatch();
            }
            commitTransaction();
        } catch (SQLException e) {
            rollbackTransaction();
            throw new RuntimeException("Помилка пакетного завантаження списку жанрів", e);
        }
    }

    @Override
    public List<SearchPreset> loadSearchPresets() {
        List<SearchPreset> presets = new ArrayList<>();
        String sql = "SELECT * FROM search_presets ORDER BY name ASC";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                long id = rs.getLong("id");
                String name = rs.getString("name");

                SearchCriteria criteria = new SearchCriteria(
                        rs.getString("title_filter"),
                        rs.getString("author_filter"),
                        rs.getString("genre_filter"),
                        rs.getString("series_filter"),
                        "", null, null, null, null, null, null, "", "", "", "", ""
                );

                SearchPreset preset = new SearchPreset(id, name, criteria);
                presets.add(preset);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Помилка завантаження пресетів", e);
        }
        return presets;
    }

    @Override
    public void saveSearchPreset(String name, SearchCriteria criteria) {
        String sql = "INSERT OR REPLACE INTO search_presets (name, title_filter, author_filter, genre_filter, series_filter) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, criteria.title());
            pstmt.setString(3, criteria.author());
            pstmt.setString(4, criteria.genre());
            pstmt.setString(5, criteria.series());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Помилка збереження пресета", e);
        }
    }

    @Override
    public void deleteSearchPreset(long id) {
        String sql = "DELETE FROM search_presets WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Помилка видалення пресета", e);
        }
    }

    @Override
    public void executeRawSql(String sql) {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Помилка виконання сирого SQL запиту", e);
        }
    }

    @Override
    public void beginTransaction() {
        try {
            if (connection != null) {
                connection.setAutoCommit(false);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Не вдалося відкрити транзакцію", e);
        }
    }

    @Override
    public void commitTransaction() {
        try {
            if (connection != null) {
                connection.commit();
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Не вдалося зафіксувати транзакцію", e);
        }
    }

    @Override
    public void rollbackTransaction() {
        try {
            if (connection != null) {
                connection.rollback();
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Не вдалося скасувати транзакцію", e);
        }
    }
}