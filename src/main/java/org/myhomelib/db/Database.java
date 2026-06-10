package org.myhomelib.db;

import org.myhomelib.model.*;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Database implements BookCollection {
    private Connection connection;
    private Path path;
    private final Map<String, Long> globalSeriesCache = new java.util.HashMap<>();
    private final Map<String, Long> globalAuthorCache = new java.util.HashMap<>();
    private String seriesFilterType = "all";
    private String genreFilterType = "all";

    public Database(Path path) {
        open(path);
    }

    @Override
    public void open(Path newPath) {
        closeQuietly();
        try {
            this.path = newPath.toAbsolutePath();
            connection = DriverManager.getConnection("jdbc:sqlite:" + this.path);
            connection.setAutoCommit(true);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA synchronous = NORMAL");
                statement.execute("PRAGMA cache_size = -64000"); // 64MB Cache
            }
            initializeSchema();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot open database: " + newPath, e);
        }
    }

    private void initializeSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS Authors (
                        AuthorID INTEGER PRIMARY KEY AUTOINCREMENT,
                        FirstName TEXT,
                        MiddleName TEXT,
                        LastName TEXT NOT NULL
                    );
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS Series (
                        SeriesID INTEGER PRIMARY KEY AUTOINCREMENT,
                        Name TEXT NOT NULL UNIQUE
                    );
                    """);

            boolean hasNameColumn = false;
            try (ResultSet rs = statement.executeQuery("PRAGMA table_info(Series);")) {
                while (rs.next()) {
                    if ("Name".equalsIgnoreCase(rs.getString("name"))) {
                        hasNameColumn = true;
                        break;
                    }
                }
            }
            if (!hasNameColumn) {
                try {
                    statement.execute("ALTER TABLE Series ADD COLUMN Name TEXT NOT NULL DEFAULT '';");
                } catch (SQLException ignored) {
                    statement.execute("ALTER TABLE Series ADD COLUMN Name TEXT;");
                }
            }

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS Books (
                        BookID INTEGER PRIMARY KEY AUTOINCREMENT,
                        Title TEXT NOT NULL,
                        SeriesID INTEGER,
                        SequenceNumber INTEGER,
                        Language TEXT,
                        FileName TEXT NOT NULL,
                        Folder TEXT,
                        ArchiveEntry TEXT,
                        FileSize INTEGER NOT NULL,
                        Keywords TEXT,
                        Annotation TEXT,
                        Rate INTEGER DEFAULT 0,
                        Progress INTEGER DEFAULT 0,
                        UpdateDate TEXT,
                        FOREIGN KEY (SeriesID) REFERENCES Series(SeriesID) ON DELETE SET NULL
                    );
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS BookAuthors (
                        BookID INTEGER,
                        AuthorID INTEGER,
                        PRIMARY KEY (BookID, AuthorID),
                        FOREIGN KEY (BookID) REFERENCES Books(BookID) ON DELETE CASCADE,
                        FOREIGN KEY (AuthorID) REFERENCES Authors(AuthorID) ON DELETE CASCADE
                    );
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS Genres (
                        GenreID INTEGER PRIMARY KEY AUTOINCREMENT,
                        Code TEXT NOT NULL UNIQUE,
                        ParentCode TEXT,
                        Fb2Code TEXT,
                        Alias TEXT
                    );
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS BookGenres (
                        BookID INTEGER,
                        GenreCode TEXT,
                        PRIMARY KEY (BookID, GenreCode),
                        FOREIGN KEY (BookID) REFERENCES Books(BookID) ON DELETE CASCADE
                    );
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS BookGroups (
                        BookID INTEGER,
                        GroupName TEXT,
                        PRIMARY KEY (BookID, GroupName),
                        FOREIGN KEY (BookID) REFERENCES Books(BookID) ON DELETE CASCADE
                    );
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS Settings (
                        Key TEXT PRIMARY KEY,
                        Value TEXT
                    );
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS SearchPresets (
                        PresetID INTEGER PRIMARY KEY AUTOINCREMENT,
                        Name TEXT NOT NULL UNIQUE,
                        Title TEXT,
                        Author TEXT,
                        Genre TEXT,
                        Series TEXT,
                        Language TEXT,
                        RateFrom INTEGER,
                        RateTo INTEGER,
                        ProgressFrom INTEGER,
                        ProgressTo INTEGER,
                        SizeFrom INTEGER,
                        SizeTo INTEGER,
                        Keywords TEXT,
                        Annotation TEXT,
                        GroupName TEXT
                    );
                    """);

            statement.execute("CREATE INDEX IF NOT EXISTS idx_books_title ON Books(Title);");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_books_series ON Books(SeriesID);");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_bookauthors_book ON BookAuthors(BookID);");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_bookauthors_author ON BookAuthors(AuthorID);");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_bookgenres_book ON BookGenres(BookID);");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_bookgenres_genre ON BookGenres(GenreCode);");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_series_name ON Series(Name);");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_authors_name ON Authors(LastName, FirstName);");
        }
    }

    @Override
    public Path path() {
        return path;
    }

    @Override
    public int importBooks(List<Fb2Book> books) {
        if (books == null || books.isEmpty()) {
            return 0;
        }
        String insertBookSql = """
                INSERT INTO Books (Title, SeriesID, SequenceNumber, Language, FileName, Folder, ArchiveEntry, FileSize, Keywords, Annotation, Rate, Progress, UpdateDate)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?);
                """;
        String insertBookAuthorSql = "INSERT OR IGNORE INTO BookAuthors (BookID, AuthorID) VALUES (?, ?);";
        String insertBookGenreSql = "INSERT OR IGNORE INTO BookGenres (BookID, GenreCode) VALUES (?, ?);";

        int savedCount = 0;
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement psBook = connection.prepareStatement(insertBookSql, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement psBookAuthor = connection.prepareStatement(insertBookAuthorSql);
                 PreparedStatement psBookGenre = connection.prepareStatement(insertBookGenreSql)) {

                for (Fb2Book fb2Book : books) {
                    Long seriesId = null;
                    if (fb2Book.series() != null && !fb2Book.series().isBlank()) {
                        seriesId = getOrInsertSeries(fb2Book.series());
                    }

                    psBook.setString(1, fb2Book.title());
                    if (seriesId == null) {
                        psBook.setNull(2, Types.INTEGER);
                    } else {
                        psBook.setLong(2, seriesId);
                    }
                    if (fb2Book.sequenceNumber() == null) {
                        psBook.setNull(3, Types.INTEGER);
                    } else {
                        psBook.setInt(3, fb2Book.sequenceNumber());
                    }
                    psBook.setString(4, fb2Book.language());
                    psBook.setString(5, fb2Book.sourcePath().getFileName().toString());
                    psBook.setString(6, fb2Book.sourcePath().getParent() == null ? "" : fb2Book.sourcePath().getParent().toString());
                    psBook.setString(7, fb2Book.archiveEntry());
                    psBook.setLong(8, fb2Book.fileSize());
                    psBook.setString(9, fb2Book.keywords());
                    psBook.setString(10, fb2Book.annotation());
                    psBook.setString(11, LocalDateTime.now().toString());
                    psBook.executeUpdate();

                    long bookId;
                    try (ResultSet keys = psBook.getGeneratedKeys()) {
                        if (keys.next()) {
                            bookId = keys.getLong(1);
                        } else {
                            continue;
                        }
                    }

                    for (Author author : fb2Book.authors()) {
                        long authorId = getOrInsertAuthor(author);
                        psBookAuthor.setLong(1, bookId);
                        psBookAuthor.setLong(2, authorId);
                        psBookAuthor.addBatch();
                    }
                    psBookAuthor.executeBatch();

                    for (String genre : fb2Book.genres()) {
                        if (genre != null && !genre.isBlank()) {
                            psBookGenre.setLong(1, bookId);
                            psBookGenre.setString(2, genre.trim());
                            psBookGenre.addBatch();
                        }
                    }
                    psBookGenre.executeBatch();
                    savedCount++;
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error importing books", e);
        }
        return savedCount;
    }

    private Long getOrInsertSeries(String seriesName) throws SQLException {
        String trimmed = seriesName.trim();
        if (globalSeriesCache.containsKey(trimmed)) {
            return globalSeriesCache.get(trimmed);
        }
        String select = "SELECT SeriesID FROM Series WHERE Name = ?;";
        try (PreparedStatement ps = connection.prepareStatement(select)) {
            ps.setString(1, trimmed);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    globalSeriesCache.put(trimmed, id);
                    return id;
                }
            }
        }
        String insert = "INSERT INTO Series (Name) VALUES (?);";
        try (PreparedStatement ps = connection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, trimmed);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    globalSeriesCache.put(trimmed, id);
                    return id;
                }
            }
        }
        return null;
    }

    private long getOrInsertAuthor(Author author) throws SQLException {
        String key = (author.lastName() + "|" + author.firstName() + "|" + author.middleName()).toLowerCase(Locale.ROOT);
        if (globalAuthorCache.containsKey(key)) {
            return globalAuthorCache.get(key);
        }
        String select = "SELECT AuthorID FROM Authors WHERE LastName = ? AND FirstName = ? AND MiddleName = ?;";
        try (PreparedStatement ps = connection.prepareStatement(select)) {
            ps.setString(1, author.lastName().trim());
            ps.setString(2, author.firstName() == null ? "" : author.firstName().trim());
            ps.setString(3, author.middleName() == null ? "" : author.middleName().trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    globalAuthorCache.put(key, id);
                    return id;
                }
            }
        }
        String insert = "INSERT INTO Authors (FirstName, MiddleName, LastName) VALUES (?, ?, ?);";
        try (PreparedStatement ps = connection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, author.firstName() == null ? "" : author.firstName().trim());
            ps.setString(2, author.middleName() == null ? "" : author.middleName().trim());
            ps.setString(3, author.lastName().trim());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    globalAuthorCache.put(key, id);
                    return id;
                }
            }
        }
        return 0;
    }

    @Override
    public List<Book> searchBooks(String query) {
        return searchBooks(query, 500);
    }

    @Override
    public List<Book> searchBooks(String query, int limit) {
        List<Book> result = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return result;
        }

        // ІДЕАЛЬНИЙ УНІВЕРСАЛЬНИЙ ШВИДКИЙ ПОШУК: Шукає і за назвою книги, і за прізвищем/іменем автора одночасно.
        // LOWER() гарантує 100% ігнорування регістру (Caps Lock) для будь-якої кирилиці та ASCII.
        String sql = """
            SELECT DISTINCT b.BookID, b.Title, b.SeriesID, b.SequenceNumber, b.Language, b.FileName, 
                            b.Folder, b.ArchiveEntry, b.FileSize, b.Keywords, b.Annotation, 
                            b.Rate, b.Progress, b.UpdateDate, s.Name AS SeriesName
            FROM Books b
            LEFT JOIN Series s ON b.SeriesID = s.SeriesID
            LEFT JOIN BookAuthors ba ON b.BookID = ba.BookID
            LEFT JOIN Authors a ON ba.AuthorID = a.AuthorID
            WHERE LOWER(b.Title) LIKE ? 
               OR LOWER(a.LastName) LIKE ? 
               OR LOWER(a.FirstName) LIKE ? 
               OR LOWER(a.LastName || ' ' || a.FirstName) LIKE ?
            ORDER BY b.BookID DESC
            LIMIT ?;
            """;

        String searchPattern = "%" + query.trim().toLowerCase(java.util.Locale.ROOT) + "%";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, searchPattern);
            ps.setString(2, searchPattern);
            ps.setString(3, searchPattern);
            ps.setString(4, searchPattern);
            ps.setInt(5, limit <= 0 ? 500 : limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("BookID");
                    String title = rs.getString("Title");
                    String series = rs.getString("SeriesName");
                    Integer seqNumber = rs.getObject("SequenceNumber") != null ? rs.getInt("SequenceNumber") : null;
                    String lang = rs.getString("Language");
                    String fileName = rs.getString("FileName");
                    String folder = rs.getString("Folder");
                    String archiveEntry = rs.getString("ArchiveEntry");
                    long fileSize = rs.getLong("FileSize");
                    String keywords = rs.getString("Keywords");
                    String annotation = rs.getString("Annotation");
                    int rate = rs.getInt("Rate");
                    int progress = rs.getInt("Progress");

                    String uDateStr = rs.getString("UpdateDate");
                    LocalDateTime updateDate = uDateStr != null ? LocalDateTime.parse(uDateStr) : LocalDateTime.now();

                    // Обов'язково заповнюємо авторів та жанри, щоб вони відображалися у стовпчиках таблиці UI
                    List<Author> authors = getAuthorsForBook(id);
                    List<String> genres = getGenresForBook(id);

                    result.add(new Book(
                            id, title, authors, genres, series, seqNumber, lang,
                            fileName, folder, archiveEntry, fileSize, keywords, annotation,
                            rate, progress, updateDate
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error executing comprehensive quick search", e);
        }
        return result;
    }

    @Override
    public List<Book> searchBooksPaged(String query, int pageSize, int pageNumber) {
        return searchBooks(query, pageSize * Math.max(1, pageNumber + 1)).stream()
                .skip((long) pageSize * Math.max(0, pageNumber))
                .limit(pageSize)
                .toList();
    }

    public List<Book> searchAdvanced(SearchCriteria criteria) {
        List<Book> result = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT b.BookID, b.Title, b.SeriesID, b.SequenceNumber, b.Language, b.FileName, 
                       b.Folder, b.ArchiveEntry, b.FileSize, b.Keywords, b.Annotation, 
                       b.Rate, b.Progress, b.UpdateDate, s.Name AS SeriesName FROM Books b
                LEFT JOIN Series s ON b.SeriesID = s.SeriesID
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();

        if (criteria.title() != null && !criteria.title().isBlank()) {
            sql.append(" AND b.Title LIKE ?");
            params.add("%" + criteria.title().trim() + "%");
        }
        if (criteria.series() != null && !criteria.series().isBlank()) {
            sql.append(" AND s.Name LIKE ?");
            params.add("%" + criteria.series().trim() + "%");
        }
        if (criteria.language() != null && !criteria.language().isBlank()) {
            sql.append(" AND b.Language = ?");
            params.add(criteria.language().trim());
        }
        if (criteria.rateFrom() != null) {
            sql.append(" AND b.Rate >= ?");
            params.add(criteria.rateFrom());
        }
        if (criteria.rateTo() != null) {
            sql.append(" AND b.Rate <= ?");
            params.add(criteria.rateTo());
        }
        if (criteria.progressFrom() != null) {
            sql.append(" AND b.Progress >= ?");
            params.add(criteria.progressFrom());
        }
        if (criteria.progressTo() != null) {
            sql.append(" AND b.Progress <= ?");
            params.add(criteria.progressTo());
        }
        if (criteria.sizeFrom() != null) {
            sql.append(" AND b.FileSize >= ?");
            params.add(criteria.sizeFrom());
        }
        if (criteria.sizeTo() != null) {
            sql.append(" AND b.FileSize <= ?");
            params.add(criteria.sizeTo());
        }
        if (criteria.keywords() != null && !criteria.keywords().isBlank()) {
            sql.append(" AND b.Keywords LIKE ?");
            params.add("%" + criteria.keywords().trim() + "%");
        }
        if (criteria.annotation() != null && !criteria.annotation().isBlank()) {
            sql.append(" AND b.Annotation LIKE ?");
            params.add("%" + criteria.annotation().trim() + "%");
        }
        if (criteria.group() != null && !criteria.group().isBlank()) {
            sql.append(" AND b.BookID IN (SELECT BookID FROM BookGroups WHERE GroupName = ?)");
            params.add(criteria.group().trim());
        }
        if (criteria.genre() != null && !criteria.genre().isBlank()) {
            sql.append(" AND b.BookID IN (SELECT BookID FROM BookGenres WHERE GenreCode LIKE ?)");
            params.add("%" + criteria.genre().trim() + "%");
        }
        if (criteria.author() != null && !criteria.author().isBlank()) {
            sql.append("""
                     AND b.BookID IN (
                        SELECT ba.BookID FROM BookAuthors ba
                        JOIN Authors a ON ba.AuthorID = a.AuthorID
                        WHERE (a.LastName || ' ' || a.FirstName || ' ' || a.MiddleName) LIKE ?
                     )
                    """);
            params.add("%" + criteria.author().trim() + "%");
        }

        sql.append(" ORDER BY b.BookID DESC LIMIT 1000;");
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("BookID");
                    String title = rs.getString("Title");
                    String series = rs.getString("SeriesName");
                    Integer seqNum = rs.getObject("SequenceNumber") != null ? rs.getInt("SequenceNumber") : null;
                    String lang = rs.getString("Language");
                    String fName = rs.getString("FileName");
                    String folder = rs.getString("Folder");
                    String archEntry = rs.getString("ArchiveEntry");
                    long size = rs.getLong("FileSize");
                    String kw = rs.getString("Keywords");
                    String ann = rs.getString("Annotation");
                    int rate = rs.getInt("Rate");
                    int prog = rs.getInt("Progress");
                    String uDateStr = rs.getString("UpdateDate");
                    LocalDateTime uDate = uDateStr != null ? LocalDateTime.parse(uDateStr) : LocalDateTime.now();

                    List<Author> authors = getAuthorsForBook(id);
                    List<String> genres = getGenresForBook(id);
                    result.add(new Book(id, title, authors, genres, series, seqNum, lang, fName, folder, archEntry, size, kw, ann, rate, prog, uDate));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error executing search query", e);
        }
        return result;
    }

    private List<Author> getAuthorsForBook(long bookId) throws SQLException {
        List<Author> list = new ArrayList<>();
        String sql = """
                SELECT a.* FROM Authors a
                JOIN BookAuthors ba ON a.AuthorID = ba.AuthorID
                WHERE ba.BookID = ?;
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Author(
                            rs.getLong("AuthorID"),
                            rs.getString("FirstName"),
                            rs.getString("MiddleName"),
                            rs.getString("LastName")
                    ));
                }
            }
        }
        return list;
    }

    private List<String> getGenresForBook(long bookId) throws SQLException {
        List<String> list = new ArrayList<>();
        String sql = "SELECT GenreCode FROM BookGenres WHERE BookID = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getString("GenreCode"));
                }
            }
        }
        return list;
    }

    @Override
    public List<String> listAuthors() {
        List<String> result = new ArrayList<>();
        String sql = "SELECT LastName, FirstName, MiddleName FROM Authors ORDER BY LastName, FirstName;";
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                String ln = rs.getString("LastName");
                String fn = rs.getString("FirstName");
                String mn = rs.getString("MiddleName");
                StringBuilder sb = new StringBuilder(ln);
                if (fn != null && !fn.isBlank()) sb.append(" ").append(fn);
                if (mn != null && !mn.isBlank()) sb.append(" ").append(mn);
                result.add(sb.toString());
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return result;
    }

    @Override
    public List<String> listSeries() {
        List<String> result = new ArrayList<>();
        String sql = "SELECT Name FROM Series ORDER BY Name;";
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                result.add(rs.getString("Name"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return result;
    }

    @Override
    public List<String> listGenres() {
        List<String> result = new ArrayList<>();
        String sql = "SELECT Code FROM Genres ORDER BY Code;";
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                result.add(rs.getString("Code"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return result;
    }

    @Override
    public List<String> listGroups() {
        List<String> result = new ArrayList<>();
        String sql = "SELECT DISTINCT GroupName FROM BookGroups ORDER BY GroupName;";
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                result.add(rs.getString("GroupName"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return result;
    }

    @Override
    public Map<String, Integer> statistics() {
        Map<String, Integer> stats = new LinkedHashMap<>();
        try (Statement stmt = connection.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM Books;")) {
                if (rs.next()) stats.put("Всього книг", rs.getInt(1));
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM Authors;")) {
                if (rs.next()) stats.put("Всього авторів", rs.getInt(1));
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM Series;")) {
                if (rs.next()) stats.put("Всього серій", rs.getInt(1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return stats;
    }

    @Override
    public Map<String, String> settings() {
        Map<String, String> map = new java.util.HashMap<>();
        String sql = "SELECT Key, Value FROM Settings;";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                map.put(rs.getString("Key"), rs.getString("Value"));
            }
        } catch (SQLException ignored) {}
        return map;
    }

    @Override
    public String setting(String key, String fallback) {
        String sql = "SELECT Value FROM Settings WHERE Key = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String val = rs.getString("Value");
                    return val == null ? fallback : val;
                }
            }
        } catch (SQLException ignored) {}
        return fallback;
    }

    @Override
    public void putSetting(String key, String value) {
        String sql = "INSERT OR REPLACE INTO Settings (Key, Value) VALUES (?, ?);";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void updateBook(long bookId, BookEdit edit) {
        String sql = """
                UPDATE Books SET Title = ?, SequenceNumber = ?, Language = ?, Keywords = ?, Annotation = ?, Rate = ?, Progress = ?, UpdateDate = ?
                WHERE BookID = ?;
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, edit.title());
            if (edit.sequenceNumber() == null) {
                ps.setNull(2, Types.INTEGER);
            } else {
                ps.setInt(2, edit.sequenceNumber());
            }
            ps.setString(3, edit.language());
            ps.setString(4, edit.keywords());
            ps.setString(5, edit.annotation());
            ps.setInt(6, edit.rate());
            ps.setInt(7, edit.progress());
            ps.setString(8, LocalDateTime.now().toString());
            ps.setLong(9, bookId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void setRate(long bookId, int rate) {
        String sql = "UPDATE Books SET Rate = ?, UpdateDate = ? WHERE BookID = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, rate);
            ps.setString(2, LocalDateTime.now().toString());
            ps.setLong(3, bookId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void setProgress(long bookId, int progress) {
        String sql = "UPDATE Books SET Progress = ?, UpdateDate = ? WHERE BookID = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, progress);
            ps.setString(2, LocalDateTime.now().toString());
            ps.setLong(3, bookId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String getReview(long bookId) {
        return setting("book.review." + bookId, "");
    }

    @Override
    public void setReview(long bookId, String review) {
        putSetting("book.review." + bookId, review);
    }

    @Override
    public void addBookToGroup(long bookId, String groupName) {
        String sql = "INSERT OR IGNORE INTO BookGroups (BookID, GroupName) VALUES (?, ?);";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, bookId);
            ps.setString(2, groupName.trim());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void removeBookFromGroup(long bookId, String groupName) {
        String sql = "DELETE FROM BookGroups WHERE BookID = ? AND GroupName = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, bookId);
            ps.setString(2, groupName.trim());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public List<String> groupsForBook(long bookId) {
        List<String> list = new ArrayList<>();
        String sql = "SELECT GroupName FROM BookGroups WHERE BookID = ? ORDER BY GroupName;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getString("GroupName"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return list;
    }

    @Override
    public boolean hideDeleted() {
        return Boolean.parseBoolean(setting("ui.hideDeleted", "false"));
    }

    @Override
    public void setHideDeleted(boolean hideDeleted) {
        putSetting("ui.hideDeleted", String.valueOf(hideDeleted));
    }

    @Override
    public boolean showLocalOnly() {
        return Boolean.parseBoolean(setting("ui.showLocalOnly", "false"));
    }

    @Override
    public void setShowLocalOnly(boolean showLocalOnly) {
        putSetting("ui.showLocalOnly", String.valueOf(showLocalOnly));
    }

    @Override
    public String authorFilterType() {
        return setting("ui.authorFilterType", "ALL");
    }

    @Override
    public void setAuthorFilterType(String type) {
        putSetting("ui.authorFilterType", type);
    }

    @Override
    public String seriesFilterType() {
        return seriesFilterType;
    }

    @Override
    public void setSeriesFilterType(String seriesFilterType) {
        this.seriesFilterType = seriesFilterType == null ? "all" : seriesFilterType;
    }

    @Override
    public String genreFilterType() {
        return genreFilterType;
    }

    @Override
    public void setGenreFilterType(String genreFilterType) {
        this.genreFilterType = genreFilterType == null ? "all" : genreFilterType;
    }

    @Override
    public int importGenreList(List<GenreImport> genres, String source) {
        if (genres == null || genres.isEmpty()) {
            return 0;
        }

        // Повне очищення довідника перед імпортом для запобігання дублів
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DELETE FROM Genres;");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to clear Genres table", e);
        }

        String sql = "INSERT OR REPLACE INTO Genres (Code, ParentCode, Fb2Code, Alias) VALUES (?, ?, ?, ?);";
        int count = 0;

        try {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (GenreImport g : genres) {
                    if (g.code() == null || g.code().isBlank()) {
                        continue;
                    }

                    String rawLine = g.code().trim();

                    // Ігноруємо коментарі у файлі, якщо вони раптом пролізли через UI парсер
                    if (rawLine.startsWith("#")) {
                        continue;
                    }

                    String code = null;
                    String parentCode = null;
                    String fb2Code = null;
                    String alias = null;

                    // НАДІЙНИЙ ПАРСЕР .GLST СТРУКТУРИ:
                    // Приклад 1: "0.1 Фантастика"
                    // Приклад 2: "0.1.1 sf_history;Альтернативная история"
                    int firstSpace = rawLine.indexOf(' ');
                    if (firstSpace != -1) {
                        fb2Code = rawLine.substring(0, firstSpace).trim(); // "0.1" або "0.1.1"
                        String rest = rawLine.substring(firstSpace).trim(); // "Фантастика" або "sf_history;Альтернативная история"

                        if (rest.contains(";")) {
                            // Рядок другого рівня з описом конкретного піджанру книги
                            String[] parts = rest.split(";", 2);
                            code = parts[0].trim();  // "sf_history" -> це те, що шукає FB2 книга
                            alias = parts[1].trim(); // "Альтернативная история"
                        } else {
                            // Кореневий ієрархічний рядок (напр. "0.1 Фантастика")
                            code = fb2Code; // Використовуємо ієрархічний числовий індекс як тимчасовий код
                            alias = rest;   // "Фантастика"
                        }
                    } else {
                        // Якщо прокинувся чистий неформатований код
                        code = rawLine;
                        fb2Code = rawLine;
                        alias = rawLine;
                    }

                    // Визначення ParentCode на основі ієрархічних крапок (напр. для "0.1.1" батьком буде "0.1")
                    if (fb2Code.contains(".")) {
                        int lastDot = fb2Code.lastIndexOf('.');
                        if (lastDot > 0) {
                            parentCode = fb2Code.substring(0, lastDot);
                        }
                    }

                    // Перевіряємо, чи отримали ми валідні значення
                    if (code == null || code.isBlank()) {
                        continue;
                    }

                    ps.setString(1, code);

                    if (parentCode == null || parentCode.isBlank()) {
                        ps.setNull(2, Types.VARCHAR);
                    } else {
                        ps.setString(2, parentCode);
                    }

                    ps.setString(3, fb2Code);
                    ps.setString(4, alias);

                    ps.addBatch();
                    count++;
                }
                ps.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error executing stable batch genre import from UI data", e);
        }
        return count;
    }

    public void saveSearchPreset(String name, SearchCriteria criteria) {
        String sql = """
                INSERT OR REPLACE INTO SearchPresets (Name, Title, Author, Genre, Series, Language, RateFrom, RateTo, ProgressFrom, ProgressTo, SizeFrom, SizeTo, Keywords, Annotation, GroupName)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, criteria.title());
            ps.setString(3, criteria.author());
            ps.setString(4, criteria.genre());
            ps.setString(5, criteria.series());
            ps.setString(6, criteria.language());
            setNullableInteger(ps, 7, criteria.rateFrom());
            setNullableInteger(ps, 8, criteria.rateTo());
            setNullableInteger(ps, 9, criteria.progressFrom());
            setNullableInteger(ps, 10, criteria.progressTo());
            setNullableLong(ps, 11, criteria.sizeFrom());
            setNullableLong(ps, 12, criteria.sizeTo());
            ps.setString(13, criteria.keywords());
            ps.setString(14, criteria.annotation());
            ps.setString(15, criteria.group());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot save search preset", e);
        }
    }

    private void setNullableInteger(PreparedStatement ps, int idx, Integer val) throws SQLException {
        if (val == null) ps.setNull(idx, Types.INTEGER);
        else ps.setInt(idx, val);
    }

    private void setNullableLong(PreparedStatement ps, int idx, Long val) throws SQLException {
        if (val == null) ps.setNull(idx, Types.INTEGER);
        else ps.setLong(idx, val);
    }

    public List<SearchPreset> loadSearchPresets() {
        List<SearchPreset> result = new ArrayList<>();
        String sql = "SELECT * FROM SearchPresets ORDER BY Name;";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                SearchCriteria criteria = new SearchCriteria(
                        rs.getString("Title"),
                        rs.getString("Author"),
                        rs.getString("Genre"),
                        rs.getString("Series"),
                        rs.getString("Language"),
                        (Integer) rs.getObject("RateFrom"),
                        (Integer) rs.getObject("RateTo"),
                        (Integer) rs.getObject("ProgressFrom"),
                        (Integer) rs.getObject("ProgressTo"),
                        (Long) rs.getObject("SizeFrom"),
                        (Long) rs.getObject("SizeTo"),
                        null, null,
                        rs.getString("Keywords"),
                        rs.getString("Annotation"),
                        rs.getString("GroupName")
                );
                result.add(new SearchPreset(rs.getLong("PresetID"), rs.getString("Name"), criteria));
            }
        } catch (SQLException ignored) {}
        return result;
    }

    public void deleteSearchPreset(long presetId) {
        String sql = "DELETE FROM SearchPresets WHERE PresetID = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, presetId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void renameSearchPreset(long presetId, String newName) {
        String sql = "UPDATE SearchPresets SET Name = ? WHERE PresetID = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, newName);
            ps.setLong(2, presetId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void closeQuietly() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {}
        }
    }

    @Override
    public void close() {
        closeQuietly();
    }

    public record GenreImport(String code, String parentCode, String fb2Code, String alias) {}
}