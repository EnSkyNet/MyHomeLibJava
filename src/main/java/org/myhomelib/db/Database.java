package org.myhomelib.db;

import org.myhomelib.model.Author;
import org.myhomelib.model.Book;
import org.myhomelib.model.BookEdit;
import org.myhomelib.model.Fb2Book;

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

    public Database(Path path) {
        open(path);
    }

    public void open(Path newPath) {
        closeQuietly();
        try {
            this.path = newPath.toAbsolutePath();
            connection = DriverManager.getConnection("jdbc:sqlite:" + this.path);
            connection.setAutoCommit(true);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA journal_mode = WAL");
            }
            initializeSchema();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot open database: " + newPath, e);
        }
    }

    public Path path() {
        return path;
    }

    private void initializeSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS Settings (
                        Key TEXT PRIMARY KEY,
                        Value TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS Series (
                        SeriesID INTEGER PRIMARY KEY AUTOINCREMENT,
                        SeriesTitle TEXT NOT NULL UNIQUE,
                        SearchSeriesTitle TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS Genres (
                        GenreCode TEXT PRIMARY KEY,
                        ParentCode TEXT,
                        FB2Code TEXT,
                        GenreAlias TEXT NOT NULL,
                        GenreSource TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS Authors (
                        AuthorID INTEGER PRIMARY KEY AUTOINCREMENT,
                        LastName TEXT NOT NULL,
                        FirstName TEXT,
                        MiddleName TEXT,
                        SearchName TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS Books (
                        BookID INTEGER PRIMARY KEY AUTOINCREMENT,
                        LibID TEXT NOT NULL,
                        Title TEXT NOT NULL,
                        SeriesID INTEGER,
                        SeqNumber INTEGER,
                        UpdateDate TEXT NOT NULL,
                        LibRate INTEGER NOT NULL DEFAULT 0,
                        Lang TEXT,
                        Folder TEXT,
                        FileName TEXT NOT NULL,
                        ArchiveEntry TEXT NOT NULL DEFAULT '',
                        InsideNo INTEGER NOT NULL DEFAULT 0,
                        Ext TEXT,
                        BookSize INTEGER,
                        IsLocal INTEGER NOT NULL DEFAULT 1,
                        IsDeleted INTEGER NOT NULL DEFAULT 0,
                        KeyWords TEXT,
                        Rate INTEGER NOT NULL DEFAULT 0,
                        Progress INTEGER NOT NULL DEFAULT 0,
                        Annotation TEXT,
                        Review TEXT,
                        ExtraInfo TEXT,
                        SearchTitle TEXT,
                        SearchLang TEXT,
                        SearchFolder TEXT,
                        SearchFileName TEXT,
                        SearchExt TEXT,
                        SearchKeyWords TEXT,
                        SearchAnnotation TEXT,
                        FOREIGN KEY (SeriesID) REFERENCES Series(SeriesID)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS Genre_List (
                        GenreCode TEXT NOT NULL,
                        BookID INTEGER NOT NULL,
                        PRIMARY KEY (BookID, GenreCode),
                        FOREIGN KEY (BookID) REFERENCES Books(BookID) ON DELETE CASCADE,
                        FOREIGN KEY (GenreCode) REFERENCES Genres(GenreCode)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS Author_List (
                        AuthorID INTEGER NOT NULL,
                        BookID INTEGER NOT NULL,
                        PRIMARY KEY (BookID, AuthorID),
                        FOREIGN KEY (BookID) REFERENCES Books(BookID) ON DELETE CASCADE,
                        FOREIGN KEY (AuthorID) REFERENCES Authors(AuthorID)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS Groups (
                        GroupID INTEGER PRIMARY KEY AUTOINCREMENT,
                        GroupName TEXT NOT NULL UNIQUE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS BookGroups (
                        GroupID INTEGER NOT NULL,
                        BookID INTEGER NOT NULL,
                        PRIMARY KEY (GroupID, BookID),
                        FOREIGN KEY (GroupID) REFERENCES Groups(GroupID) ON DELETE CASCADE,
                        FOREIGN KEY (BookID) REFERENCES Books(BookID) ON DELETE CASCADE
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS IXBooks_Title ON Books (Title)");
            statement.execute("CREATE INDEX IF NOT EXISTS IXBooks_FileName ON Books (FileName)");
            statement.execute("CREATE INDEX IF NOT EXISTS IXAuthors_SearchName ON Authors (SearchName)");
            statement.execute("CREATE INDEX IF NOT EXISTS IXSeries_SearchSeriesTitle ON Series (SearchSeriesTitle)");
            statement.execute("CREATE INDEX IF NOT EXISTS IXGenreList_GenreCode_BookID ON Genre_List (GenreCode, BookID)");
            statement.execute("CREATE INDEX IF NOT EXISTS IXAuthorList_AuthorID_BookID ON Author_List (AuthorID, BookID)");
            statement.execute("CREATE INDEX IF NOT EXISTS IXBookGroups_GroupID_BookID ON BookGroups (GroupID, BookID)");
        }
        addColumnIfMissing("Books", "ArchiveEntry", "TEXT NOT NULL DEFAULT ''");
        addColumnIfMissing("Books", "Review", "TEXT");
        addColumnIfMissing("Books", "ExtraInfo", "TEXT");
        addColumnIfMissing("Genres", "GenreSource", "TEXT");
        seedDefaultSettings();
    }

    public int importBooks(List<Fb2Book> books) {
        int imported = 0;
        try {
            connection.setAutoCommit(false);
            for (Fb2Book book : books) {
                upsertBook(book);
                imported++;
            }
            connection.commit();
            return imported;
        } catch (SQLException e) {
            rollbackQuietly();
            throw new IllegalStateException("Import failed", e);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    private void upsertBook(Fb2Book book) throws SQLException {
        long seriesId = book.series().isBlank() ? 0 : findOrCreateSeries(book.series());
        Path source = book.sourcePath();
        String fileName = source == null ? "unknown.fb2" : source.getFileName().toString();
        String folder = source == null || source.getParent() == null ? "" : source.getParent().toString();
        String archiveEntry = book.archiveEntry();
        String ext = extension(archiveEntry.isBlank() ? fileName : archiveEntry);
        String libId = source == null ? fileName : source.toAbsolutePath() + "!" + archiveEntry;

        Long existingBookId = findBookByLibId(libId);
        long bookId;
        if (existingBookId == null) {
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO Books (
                        LibID, Title, SeriesID, SeqNumber, UpdateDate, Lang, Folder, FileName, InsideNo,
                        ArchiveEntry, Ext, BookSize, IsLocal, KeyWords, Annotation, SearchTitle, SearchLang,
                        SearchFolder, SearchFileName, SearchExt, SearchKeyWords, SearchAnnotation
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                bindBook(ps, book, libId, seriesId, folder, fileName, archiveEntry, ext);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("No generated BookID returned");
                    }
                    bookId = keys.getLong(1);
                }
            }
        } else {
            bookId = existingBookId;
            try (PreparedStatement ps = connection.prepareStatement("""
                    UPDATE Books SET
                        Title = ?, SeriesID = ?, SeqNumber = ?, UpdateDate = ?, Lang = ?, Folder = ?,
                        FileName = ?, ArchiveEntry = ?, Ext = ?, BookSize = ?, KeyWords = ?, Annotation = ?,
                        SearchTitle = ?, SearchLang = ?, SearchFolder = ?, SearchFileName = ?,
                        SearchExt = ?, SearchKeyWords = ?, SearchAnnotation = ?
                    WHERE BookID = ?
                    """)) {
                bindBookUpdate(ps, book, seriesId, folder, fileName, archiveEntry, ext, bookId);
                ps.executeUpdate();
            }
            deleteLinks(bookId);
        }

        for (Author author : book.authors()) {
            long authorId = findOrCreateAuthor(author);
            try (PreparedStatement ps = connection.prepareStatement("INSERT OR IGNORE INTO Author_List (AuthorID, BookID) VALUES (?, ?)")) {
                ps.setLong(1, authorId);
                ps.setLong(2, bookId);
                ps.executeUpdate();
            }
        }
        for (String genre : book.genres()) {
            String value = resolveGenreCode(genre == null || genre.isBlank() ? "unknown" : genre.trim());
            try (PreparedStatement ps = connection.prepareStatement("INSERT OR IGNORE INTO Genre_List (GenreCode, BookID) VALUES (?, ?)")) {
                ps.setString(1, value);
                ps.setLong(2, bookId);
                ps.executeUpdate();
            }
        }
    }

    private void bindBook(PreparedStatement ps, Fb2Book book, String libId, long seriesId, String folder, String fileName, String archiveEntry, String ext) throws SQLException {
        ps.setString(1, libId);
        ps.setString(2, fallback(book.title(), fileName));
        setNullableLong(ps, 3, seriesId);
        setNullableInteger(ps, 4, book.sequenceNumber());
        ps.setString(5, LocalDateTime.now().toString());
        ps.setString(6, book.language());
        ps.setString(7, folder);
        ps.setString(8, fileName);
        ps.setString(9, archiveEntry);
        ps.setString(10, ext);
        ps.setLong(11, book.fileSize());
        ps.setString(12, book.keywords());
        ps.setString(13, book.annotation());
        ps.setString(14, normalize(book.title()));
        ps.setString(15, normalize(book.language()));
        ps.setString(16, normalize(folder));
        ps.setString(17, normalize(fileName));
        ps.setString(18, normalize(ext));
        ps.setString(19, normalize(book.keywords()));
        ps.setString(20, normalize(book.annotation()));
    }

    private void bindBookUpdate(PreparedStatement ps, Fb2Book book, long seriesId, String folder, String fileName, String archiveEntry, String ext, long bookId) throws SQLException {
        ps.setString(1, fallback(book.title(), fileName));
        setNullableLong(ps, 2, seriesId);
        setNullableInteger(ps, 3, book.sequenceNumber());
        ps.setString(4, LocalDateTime.now().toString());
        ps.setString(5, book.language());
        ps.setString(6, folder);
        ps.setString(7, fileName);
        ps.setString(8, archiveEntry);
        ps.setString(9, ext);
        ps.setLong(10, book.fileSize());
        ps.setString(11, book.keywords());
        ps.setString(12, book.annotation());
        ps.setString(13, normalize(book.title()));
        ps.setString(14, normalize(book.language()));
        ps.setString(15, normalize(folder));
        ps.setString(16, normalize(fileName));
        ps.setString(17, normalize(ext));
        ps.setString(18, normalize(book.keywords()));
        ps.setString(19, normalize(book.annotation()));
        ps.setLong(20, bookId);
    }

    private void deleteLinks(long bookId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM Author_List WHERE BookID = ?")) {
            ps.setLong(1, bookId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM Genre_List WHERE BookID = ?")) {
            ps.setLong(1, bookId);
            ps.executeUpdate();
        }
    }

    public List<Book> searchBooks(String query) {
        String normalized = normalize(query);
        String sql = """
                SELECT b.*, s.SeriesTitle
                FROM Books b
                LEFT JOIN Series s ON s.SeriesID = b.SeriesID
                WHERE (? = 0 OR b.IsDeleted = 0)
                  AND (? = 0 OR b.IsLocal = 1)
                  AND (? = ''
                    OR b.SearchTitle LIKE ?
                    OR b.SearchFileName LIKE ?
                    OR b.SearchLang LIKE ?
                    OR b.SearchKeyWords LIKE ?
                    OR b.SearchAnnotation LIKE ?
                    OR EXISTS (
                        SELECT 1 FROM Author_List al JOIN Authors a ON a.AuthorID = al.AuthorID
                        WHERE al.BookID = b.BookID AND a.SearchName LIKE ?
                    )
                    OR EXISTS (
                        SELECT 1 FROM Genre_List gl JOIN Genres g ON g.GenreCode = gl.GenreCode
                        WHERE gl.BookID = b.BookID AND UPPER(g.GenreAlias) LIKE ?
                    )
                    OR EXISTS (
                        SELECT 1 FROM BookGroups bg JOIN Groups g ON g.GroupID = bg.GroupID
                        WHERE bg.BookID = b.BookID AND UPPER(g.GroupName) LIKE ?
                    )
                  )
                ORDER BY b.Title COLLATE NOCASE, b.FileName COLLATE NOCASE
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            String pattern = "%" + normalized + "%";
            ps.setInt(1, hideDeleted() ? 1 : 0);
            ps.setInt(2, showLocalOnly() ? 1 : 0);
            ps.setString(3, normalized);
            for (int i = 4; i <= 11; i++) {
                ps.setString(i, pattern);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Book> books = new ArrayList<>();
                while (rs.next()) {
                    books.add(mapBook(rs));
                }
                return books;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot search books", e);
        }
    }

    public List<String> listAuthors() {
        return listValues("""
                SELECT a.SearchName AS value
                FROM Authors a
                JOIN Author_List al ON al.AuthorID = a.AuthorID
                JOIN Books b ON b.BookID = al.BookID
                WHERE (? = 0 OR b.IsDeleted = 0)
                  AND (? = 0 OR b.IsLocal = 1)
                GROUP BY a.AuthorID
                ORDER BY a.SearchName COLLATE NOCASE
                """);
    }

    public List<String> listSeries() {
        return listValues("""
                SELECT s.SeriesTitle AS value
                FROM Series s
                JOIN Books b ON b.SeriesID = s.SeriesID
                WHERE (? = 0 OR b.IsDeleted = 0)
                  AND (? = 0 OR b.IsLocal = 1)
                GROUP BY s.SeriesID
                ORDER BY s.SeriesTitle COLLATE NOCASE
                """);
    }

    public List<String> listGenres() {
        return listValues("""
                SELECT g.GenreAlias AS value
                FROM Genres g
                JOIN Genre_List gl ON gl.GenreCode = g.GenreCode
                JOIN Books b ON b.BookID = gl.BookID
                WHERE (? = 0 OR b.IsDeleted = 0)
                  AND (? = 0 OR b.IsLocal = 1)
                GROUP BY g.GenreCode
                ORDER BY g.GenreAlias COLLATE NOCASE
                """);
    }

    public Map<String, Integer> statistics() {
        Map<String, Integer> values = new LinkedHashMap<>();
        values.put("Books", count("Books"));
        values.put("Authors", count("Authors"));
        values.put("Series", count("Series"));
        values.put("Genres", count("Genres"));
        values.put("Groups", count("Groups"));
        return values;
    }

    public Map<String, String> settings() {
        Map<String, String> settings = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT Key, Value FROM Settings ORDER BY Key");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                settings.put(rs.getString("Key"), rs.getString("Value"));
            }
            return settings;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot load settings", e);
        }
    }

    public String setting(String key, String fallback) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT Value FROM Settings WHERE Key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("Value") : fallback;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot load setting: " + key, e);
        }
    }

    public void putSetting(String key, String value) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO Settings(Key, Value)
                VALUES(?, ?)
                ON CONFLICT(Key) DO UPDATE SET Value = excluded.Value
                """)) {
            ps.setString(1, key);
            ps.setString(2, value == null ? "" : value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot save setting: " + key, e);
        }
    }

    public List<String> listGroups() {
        return listValues("""
                SELECT g.GroupName AS value
                FROM Groups g
                JOIN BookGroups bg ON bg.GroupID = g.GroupID
                JOIN Books b ON b.BookID = bg.BookID
                WHERE (? = 0 OR b.IsDeleted = 0)
                  AND (? = 0 OR b.IsLocal = 1)
                GROUP BY g.GroupID
                ORDER BY g.GroupName COLLATE NOCASE
                """);
    }

    public void updateBook(long bookId, BookEdit edit) {
        try {
            connection.setAutoCommit(false);
            long seriesId = edit.series() == null || edit.series().isBlank() ? 0 : findOrCreateSeries(edit.series());
            try (PreparedStatement ps = connection.prepareStatement("""
                    UPDATE Books SET
                        Title = ?, SeriesID = ?, SeqNumber = ?, UpdateDate = ?, Lang = ?,
                        KeyWords = ?, Annotation = ?, Rate = ?, Progress = ?,
                        SearchTitle = ?, SearchLang = ?, SearchKeyWords = ?, SearchAnnotation = ?
                    WHERE BookID = ?
                    """)) {
                ps.setString(1, fallback(edit.title(), "Untitled"));
                setNullableLong(ps, 2, seriesId);
                setNullableInteger(ps, 3, edit.sequenceNumber());
                ps.setString(4, LocalDateTime.now().toString());
                ps.setString(5, edit.language());
                ps.setString(6, edit.keywords());
                ps.setString(7, edit.annotation());
                ps.setInt(8, clamp(edit.rate(), 0, 5));
                ps.setInt(9, clamp(edit.progress(), 0, 100));
                ps.setString(10, normalize(edit.title()));
                ps.setString(11, normalize(edit.language()));
                ps.setString(12, normalize(edit.keywords()));
                ps.setString(13, normalize(edit.annotation()));
                ps.setLong(14, bookId);
                ps.executeUpdate();
            }
            deleteLinks(bookId);
            for (Author author : edit.authors()) {
                long authorId = findOrCreateAuthor(author);
                try (PreparedStatement ps = connection.prepareStatement("INSERT OR IGNORE INTO Author_List (AuthorID, BookID) VALUES (?, ?)")) {
                    ps.setLong(1, authorId);
                    ps.setLong(2, bookId);
                    ps.executeUpdate();
                }
            }
            for (String genre : edit.genres()) {
                if (genre == null || genre.isBlank()) {
                    continue;
                }
                String value = resolveGenreCode(genre.trim());
                try (PreparedStatement ps = connection.prepareStatement("INSERT OR IGNORE INTO Genre_List (GenreCode, BookID) VALUES (?, ?)")) {
                    ps.setString(1, value);
                    ps.setLong(2, bookId);
                    ps.executeUpdate();
                }
            }
            connection.commit();
        } catch (SQLException e) {
            rollbackQuietly();
            throw new IllegalStateException("Cannot update book", e);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    public void setRate(long bookId, int rate) {
        updateSingleInt(bookId, "Rate", clamp(rate, 0, 5));
    }

    public void setProgress(long bookId, int progress) {
        updateSingleInt(bookId, "Progress", clamp(progress, 0, 100));
    }

    public String getReview(long bookId) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT Review FROM Books WHERE BookID = ?")) {
            ps.setLong(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? fallback(rs.getString("Review"), "") : "";
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot load review", e);
        }
    }

    public void setReview(long bookId, String review) {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE Books SET Review = ?, UpdateDate = ? WHERE BookID = ?")) {
            ps.setString(1, review == null ? "" : review);
            ps.setString(2, LocalDateTime.now().toString());
            ps.setLong(3, bookId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot save review", e);
        }
    }

    public boolean hideDeleted() {
        return Boolean.parseBoolean(setting("view.hideDeleted", "true"));
    }

    public void setHideDeleted(boolean hideDeleted) {
        putSetting("view.hideDeleted", Boolean.toString(hideDeleted));
    }

    public boolean showLocalOnly() {
        return Boolean.parseBoolean(setting("view.showLocalOnly", "false"));
    }

    public void setShowLocalOnly(boolean showLocalOnly) {
        putSetting("view.showLocalOnly", Boolean.toString(showLocalOnly));
    }

    public String authorFilterType() {
        return setting("filter.authorType", "all");
    }

    public void setAuthorFilterType(String authorFilterType) {
        putSetting("filter.authorType", authorFilterType == null || authorFilterType.isBlank() ? "all" : authorFilterType.trim());
    }

    public String seriesFilterType() {
        return setting("filter.seriesType", "all");
    }

    public void setSeriesFilterType(String seriesFilterType) {
        putSetting("filter.seriesType", seriesFilterType == null || seriesFilterType.isBlank() ? "all" : seriesFilterType.trim());
    }

    public void addBookToGroup(long bookId, String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return;
        }
        try {
            long groupId = findOrCreateGroup(groupName.trim());
            try (PreparedStatement ps = connection.prepareStatement("INSERT OR IGNORE INTO BookGroups (GroupID, BookID) VALUES (?, ?)")) {
                ps.setLong(1, groupId);
                ps.setLong(2, bookId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot add book to group", e);
        }
    }

    public void removeBookFromGroup(long bookId, String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                DELETE FROM BookGroups
                WHERE BookID = ? AND GroupID IN (SELECT GroupID FROM Groups WHERE GroupName = ?)
                """)) {
            ps.setLong(1, bookId);
            ps.setString(2, groupName.trim());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot remove book from group", e);
        }
    }

    public List<String> groupsForBook(long bookId) {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT g.GroupName AS value
                FROM Groups g
                JOIN BookGroups bg ON bg.GroupID = g.GroupID
                WHERE bg.BookID = ?
                ORDER BY g.GroupName COLLATE NOCASE
                """)) {
            ps.setLong(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> groups = new ArrayList<>();
                while (rs.next()) {
                    groups.add(rs.getString("value"));
                }
                return groups;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot load book groups", e);
        }
    }

    private void updateSingleInt(long bookId, String column, int value) {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE Books SET " + column + " = ?, UpdateDate = ? WHERE BookID = ?")) {
            ps.setInt(1, value);
            ps.setString(2, LocalDateTime.now().toString());
            ps.setLong(3, bookId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot update " + column, e);
        }
    }

    private int count(String table) {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot count " + table, e);
        }
    }

    private List<String> listValues(String sql) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, hideDeleted() ? 1 : 0);
            ps.setInt(2, showLocalOnly() ? 1 : 0);
            try (ResultSet rs = ps.executeQuery()) {
            List<String> values = new ArrayList<>();
            while (rs.next()) {
                values.add(rs.getString("value"));
            }
            return values;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot load list", e);
        }
    }

    private Book mapBook(ResultSet rs) throws SQLException {
        long id = rs.getLong("BookID");
        return new Book(
                id,
                rs.getString("Title"),
                loadAuthors(id),
                loadGenres(id),
                rs.getString("SeriesTitle"),
                nullableInt(rs, "SeqNumber"),
                rs.getString("Lang"),
                rs.getString("FileName"),
                rs.getString("Folder"),
                rs.getString("ArchiveEntry"),
                rs.getLong("BookSize"),
                rs.getString("KeyWords"),
                rs.getString("Annotation"),
                rs.getInt("Rate"),
                rs.getInt("Progress"),
                LocalDateTime.parse(rs.getString("UpdateDate"))
        );
    }

    private List<Author> loadAuthors(long bookId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT a.* FROM Authors a
                JOIN Author_List al ON al.AuthorID = a.AuthorID
                WHERE al.BookID = ?
                ORDER BY a.LastName, a.FirstName, a.MiddleName
                """)) {
            ps.setLong(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Author> authors = new ArrayList<>();
                while (rs.next()) {
                    authors.add(new Author(
                            rs.getLong("AuthorID"),
                            rs.getString("FirstName"),
                            rs.getString("MiddleName"),
                            rs.getString("LastName")
                    ));
                }
                return authors;
            }
        }
    }

    private List<String> loadGenres(long bookId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT g.GenreAlias FROM Genres g
                JOIN Genre_List gl ON gl.GenreCode = g.GenreCode
                WHERE gl.BookID = ?
                ORDER BY g.GenreAlias
                """)) {
            ps.setLong(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> genres = new ArrayList<>();
                while (rs.next()) {
                    genres.add(rs.getString("GenreAlias"));
                }
                return genres;
            }
        }
    }

    private Long findBookByLibId(String libId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT BookID FROM Books WHERE LibID = ?")) {
            ps.setString(1, libId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private long findOrCreateSeries(String series) throws SQLException {
        Long id = findLong("SELECT SeriesID FROM Series WHERE SearchSeriesTitle = ?", normalize(series));
        if (id != null) {
            return id;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO Series (SeriesTitle, SearchSeriesTitle) VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, series);
            ps.setString(2, normalize(series));
            ps.executeUpdate();
            return generatedId(ps);
        }
    }

    private long findOrCreateAuthor(Author author) throws SQLException {
        String searchName = normalize(author.displayName());
        Long id = findLong("SELECT AuthorID FROM Authors WHERE SearchName = ?", searchName);
        if (id != null) {
            return id;
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO Authors (LastName, FirstName, MiddleName, SearchName)
                VALUES (?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, fallback(author.lastName(), "Unknown"));
            ps.setString(2, author.firstName());
            ps.setString(3, author.middleName());
            ps.setString(4, searchName);
            ps.executeUpdate();
            return generatedId(ps);
        }
    }

    private String resolveGenreCode(String genre) throws SQLException {
        if (genre == null || genre.isBlank()) {
            return "unknown";
        }
        String byFb2 = findString("""
                SELECT GenreCode
                FROM Genres
                WHERE FB2Code = ?
                  AND GenreCode <> ?
                ORDER BY GenreSource, GenreCode
                LIMIT 1
                """, genre, genre);
        if (byFb2 != null && !byFb2.isBlank()) {
            return byFb2;
        }
        Long byCode = findLong("SELECT COUNT(*) FROM Genres WHERE GenreCode = ?", genre);
        if (byCode != null && byCode > 0) {
            return genre;
        }
        findOrCreateGenre(genre);
        return genre;
    }

    private void findOrCreateGenre(String genre) throws SQLException {
        Long exists = findLong("SELECT COUNT(*) FROM Genres WHERE GenreCode = ?", genre);
        if (exists != null && exists > 0) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO Genres (GenreCode, FB2Code, GenreAlias) VALUES (?, ?, ?)")) {
            ps.setString(1, genre);
            ps.setString(2, genre);
            ps.setString(3, genre);
            ps.executeUpdate();
        }
    }

    public int importGenreList(List<GenreImport> genres, String source) {
        try {
            connection.setAutoCommit(false);
            int imported = 0;
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO Genres (GenreCode, ParentCode, FB2Code, GenreAlias, GenreSource)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(GenreCode) DO UPDATE SET
                        ParentCode = excluded.ParentCode,
                        FB2Code = excluded.FB2Code,
                        GenreAlias = excluded.GenreAlias,
                        GenreSource = excluded.GenreSource
                    """)) {
                for (GenreImport genre : genres) {
                    ps.setString(1, genre.code());
                    ps.setString(2, genre.parentCode());
                    ps.setString(3, genre.fb2Code());
                    ps.setString(4, genre.alias());
                    ps.setString(5, source);
                    ps.addBatch();
                    imported++;
                }
                ps.executeBatch();
            }
            migrateGenreLinksToImportedCodes(source);
            connection.commit();
            return imported;
        } catch (SQLException e) {
            rollbackQuietly();
            throw new IllegalStateException("Cannot import genres", e);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    private void migrateGenreLinksToImportedCodes(String source) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT OR IGNORE INTO Genre_List (GenreCode, BookID)
                SELECT g.GenreCode, gl.BookID
                FROM Genre_List gl
                JOIN Genres g ON g.FB2Code = gl.GenreCode
                WHERE g.GenreSource = ?
                  AND g.FB2Code IS NOT NULL
                  AND g.FB2Code <> ''
                  AND g.GenreCode <> gl.GenreCode
                """)) {
            insert.setString(1, source);
            insert.executeUpdate();
        }
        try (PreparedStatement delete = connection.prepareStatement("""
                DELETE FROM Genre_List
                WHERE EXISTS (
                    SELECT 1
                    FROM Genres g
                    WHERE g.GenreSource = ?
                      AND g.FB2Code = Genre_List.GenreCode
                      AND g.FB2Code IS NOT NULL
                      AND g.FB2Code <> ''
                      AND g.GenreCode <> Genre_List.GenreCode
                )
                """)) {
            delete.setString(1, source);
            delete.executeUpdate();
        }
    }

    private long findOrCreateGroup(String groupName) throws SQLException {
        Long id = findLong("SELECT GroupID FROM Groups WHERE GroupName = ?", groupName);
        if (id != null) {
            return id;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO Groups (GroupName) VALUES (?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, groupName);
            ps.executeUpdate();
            return generatedId(ps);
        }
    }

    private Long findLong(String sql, String value) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private String findString(String sql, String value) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private String findString(String sql, String firstValue, String secondValue) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, firstValue);
            ps.setString(2, secondValue);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private void seedDefaultSettings() throws SQLException {
        seedSetting("fb2.folderTemplate", "{author}/{series}");
        seedSetting("fb2.fileTemplate", "{title}");
        seedSetting("import.readFb2", "true");
        seedSetting("import.readZip", "true");
        seedSetting("import.readInpx", "true");
        seedSetting("view.hideDeleted", "true");
        seedSetting("view.showLocalOnly", "false");
        seedSetting("filter.authorType", "all");
        seedSetting("filter.seriesType", "all");
    }

    private void seedSetting(String key, String value) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("INSERT OR IGNORE INTO Settings(Key, Value) VALUES(?, ?)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    private long generatedId(PreparedStatement ps) throws SQLException {
        try (ResultSet keys = ps.getGeneratedKeys()) {
            if (!keys.next()) {
                throw new SQLException("No generated id returned");
            }
            return keys.getLong(1);
        }
    }

    private static void setNullableLong(PreparedStatement ps, int index, long value) throws SQLException {
        if (value == 0) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setLong(index, value);
        }
    }

    private static void setNullableInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void addColumnIfMissing(String table, String column, String definition) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    public record GenreImport(String code, String parentCode, String fb2Code, String alias) {
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
        }
    }

    private void closeQuietly() {
        try {
            close();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
        } finally {
            connection = null;
        }
    }
}
