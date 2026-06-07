package org.myhomelib.db;

import org.myhomelib.model.CollectionInfo;
import org.myhomelib.model.CollectionType;
import org.myhomelib.model.Book;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SystemDatabase implements AutoCloseable {
    private static final long INVALID_COLLECTION_ID = -1;

    private final Path path;
    private final Connection connection;

    public SystemDatabase(Path path) {
        try {
            this.path = path.toAbsolutePath();
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + this.path);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA journal_mode = WAL");
            }
            initializeSchema();
            seedDefaultGroups();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot open system database: " + path, e);
        }
    }

    public Path path() {
        return path;
    }

    private void initializeSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS SystemSettings (
                        Key TEXT PRIMARY KEY,
                        Value TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS Bases (
                        DatabaseID INTEGER PRIMARY KEY AUTOINCREMENT,
                        BaseName TEXT NOT NULL COLLATE NOCASE UNIQUE,
                        DBFileName TEXT NOT NULL COLLATE NOCASE,
                        RootFolder TEXT NOT NULL COLLATE NOCASE,
                        DataVersion INTEGER,
                        Code INTEGER NOT NULL,
                        LibUser TEXT,
                        LibPassword TEXT,
                        Notes TEXT,
                        CreationDate TEXT NOT NULL,
                        URL TEXT COLLATE NOCASE,
                        ConnectionScript TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS Groups (
                        GroupID INTEGER PRIMARY KEY AUTOINCREMENT,
                        GroupName TEXT NOT NULL COLLATE NOCASE UNIQUE,
                        AllowDelete INTEGER NOT NULL,
                        Notes TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS Books (
                        BookID INTEGER NOT NULL,
                        DatabaseID INTEGER NOT NULL,
                        LibID TEXT NOT NULL,
                        Title TEXT NOT NULL COLLATE NOCASE,
                        SeriesID INTEGER,
                        SeqNumber INTEGER,
                        UpdateDate TEXT NOT NULL,
                        LibRate INTEGER NOT NULL DEFAULT 0,
                        Lang TEXT COLLATE NOCASE,
                        Folder TEXT COLLATE NOCASE,
                        FileName TEXT NOT NULL COLLATE NOCASE,
                        InsideNo INTEGER NOT NULL DEFAULT 0,
                        Ext TEXT COLLATE NOCASE,
                        BookSize INTEGER,
                        IsLocal INTEGER NOT NULL DEFAULT 1,
                        IsDeleted INTEGER NOT NULL DEFAULT 0,
                        KeyWords TEXT COLLATE NOCASE,
                        Rate INTEGER NOT NULL DEFAULT 0,
                        Progress INTEGER NOT NULL DEFAULT 0,
                        Annotation TEXT COLLATE NOCASE,
                        Review TEXT,
                        ExtraInfo TEXT,
                        PRIMARY KEY (BookID, DatabaseID),
                        FOREIGN KEY (DatabaseID) REFERENCES Bases(DatabaseID) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS BookGroups (
                        BookID INTEGER NOT NULL,
                        DatabaseID INTEGER NOT NULL,
                        GroupID INTEGER NOT NULL,
                        PRIMARY KEY (GroupID, BookID, DatabaseID),
                        FOREIGN KEY (GroupID) REFERENCES Groups(GroupID) ON DELETE CASCADE,
                        FOREIGN KEY (BookID, DatabaseID) REFERENCES Books(BookID, DatabaseID) ON DELETE CASCADE
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS IXSystemBooks_FileName ON Books (FileName)");
            statement.execute("CREATE INDEX IF NOT EXISTS IXSystemBookGroups_BookID_DatabaseID ON BookGroups (DatabaseID, BookID)");
        }
    }

    private void seedDefaultGroups() throws SQLException {
        addGroup("Обране", false);
        addGroup("До прочитання", false);
    }

    public boolean hasCollections() {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM Bases")) {
            return rs.next() && rs.getLong(1) > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot count collections", e);
        }
    }

    public List<CollectionInfo> collections() {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT *
                FROM Bases
                ORDER BY BaseName COLLATE NOCASE
                """);
             ResultSet rs = ps.executeQuery()) {
            List<CollectionInfo> collections = new ArrayList<>();
            while (rs.next()) {
                collections.add(mapCollection(rs));
            }
            return collections;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot load collections", e);
        }
    }

    public CollectionInfo activeCollection() {
        long activeId = activeCollectionId();
        if (activeId != INVALID_COLLECTION_ID) {
            CollectionInfo active = collection(activeId);
            if (active != null) {
                return active;
            }
        }
        List<CollectionInfo> collections = collections();
        if (collections.isEmpty()) {
            return null;
        }
        CollectionInfo first = collections.getFirst();
        setActiveCollection(first.id());
        return first;
    }

    public CollectionInfo collection(long id) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM Bases WHERE DatabaseID = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapCollection(rs) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot load collection: " + id, e);
        }
    }

    public long registerCollection(Path databasePath, String displayName, Path rootFolder, CollectionType type) {
        Path absoluteDb = databasePath.toAbsolutePath();
        CollectionInfo existing = findCollectionByPath(absoluteDb);
        if (existing != null) {
            setActiveCollection(existing.id());
            return existing.id();
        }

        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO Bases (
                    BaseName, DBFileName, RootFolder, DataVersion, Code, LibUser, LibPassword,
                    Notes, CreationDate, URL, ConnectionScript
                ) VALUES (?, ?, ?, ?, ?, '', '', '', ?, '', '')
                """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, displayName == null || displayName.isBlank() ? fallbackName(absoluteDb) : displayName.trim());
            ps.setString(2, absoluteDb.toString());
            ps.setString(3, rootFolder == null ? "" : rootFolder.toAbsolutePath().toString());
            ps.setInt(4, 0);
            ps.setInt(5, type.code());
            ps.setString(6, LocalDateTime.now().toString());
            ps.executeUpdate();
            long id = generatedId(ps);
            setActiveCollection(id);
            return id;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot register collection: " + databasePath, e);
        }
    }

    public void replaceBooks(long collectionId, List<Book> books) {
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM Books WHERE DatabaseID = ?")) {
                delete.setLong(1, collectionId);
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO Books (
                        BookID, DatabaseID, LibID, Title, SeriesID, SeqNumber, UpdateDate, LibRate,
                        Lang, Folder, FileName, InsideNo, Ext, BookSize, IsLocal, IsDeleted,
                        KeyWords, Rate, Progress, Annotation, Review, ExtraInfo
                    ) VALUES (?, ?, ?, ?, NULL, ?, ?, 0, ?, ?, ?, 0, ?, ?, 1, 0, ?, ?, ?, ?, '', '')
                    """)) {
                for (Book book : books) {
                    insert.setLong(1, book.id());
                    insert.setLong(2, collectionId);
                    insert.setString(3, book.archivePath());
                    insert.setString(4, book.title());
                    setNullableInteger(insert, 5, book.sequenceNumber());
                    insert.setString(6, book.updateDate().toString());
                    insert.setString(7, book.language());
                    insert.setString(8, book.folder());
                    insert.setString(9, book.fileName());
                    insert.setString(10, extension(book.hasArchiveEntry() ? book.archiveEntry() : book.fileName()));
                    insert.setLong(11, book.fileSize());
                    insert.setString(12, book.keywords());
                    insert.setInt(13, book.rate());
                    insert.setInt(14, book.progress());
                    insert.setString(15, book.annotation());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            rollbackQuietly();
            throw new IllegalStateException("Cannot sync system books for collection: " + collectionId, e);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    public long systemBookCount(long collectionId) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM Books WHERE DatabaseID = ?")) {
            ps.setLong(1, collectionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot count system books", e);
        }
    }

    public void setActiveCollection(long collectionId) {
        if (collectionId == INVALID_COLLECTION_ID) {
            return;
        }
        putSetting("activeCollection", Long.toString(collectionId));
    }

    public long activeCollectionId() {
        String value = setting("activeCollection", Long.toString(INVALID_COLLECTION_ID));
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return INVALID_COLLECTION_ID;
        }
    }

    private CollectionInfo findCollectionByPath(Path databasePath) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM Bases WHERE DBFileName = ?")) {
            ps.setString(1, databasePath.toAbsolutePath().toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapCollection(rs) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot find collection: " + databasePath, e);
        }
    }

    private void addGroup(String groupName, boolean allowDelete) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT OR IGNORE INTO Groups (GroupName, AllowDelete, Notes)
                VALUES (?, ?, '')
                """)) {
            ps.setString(1, groupName);
            ps.setInt(2, allowDelete ? 1 : 0);
            ps.executeUpdate();
        }
    }

    private void putSetting(String key, String value) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO SystemSettings(Key, Value)
                VALUES(?, ?)
                ON CONFLICT(Key) DO UPDATE SET Value = excluded.Value
                """)) {
            ps.setString(1, key);
            ps.setString(2, value == null ? "" : value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot save system setting: " + key, e);
        }
    }

    private String setting(String key, String fallback) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT Value FROM SystemSettings WHERE Key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("Value") : fallback;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot load system setting: " + key, e);
        }
    }

    private static CollectionInfo mapCollection(ResultSet rs) throws SQLException {
        return new CollectionInfo(
                rs.getLong("DatabaseID"),
                rs.getString("BaseName"),
                Path.of(rs.getString("DBFileName")),
                rs.getString("RootFolder") == null || rs.getString("RootFolder").isBlank() ? Path.of("") : Path.of(rs.getString("RootFolder")),
                rs.getInt("DataVersion"),
                CollectionType.fromCode(rs.getInt("Code")),
                rs.getString("Notes"),
                rs.getString("LibUser"),
                rs.getString("LibPassword"),
                rs.getString("URL"),
                rs.getString("ConnectionScript"),
                LocalDateTime.parse(rs.getString("CreationDate"))
        );
    }

    private static long generatedId(PreparedStatement ps) throws SQLException {
        try (ResultSet keys = ps.getGeneratedKeys()) {
            if (!keys.next()) {
                throw new SQLException("No generated id returned");
            }
            return keys.getLong(1);
        }
    }

    private static void setNullableInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
        }
    }

    private static String fallbackName(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? "Collection" : fileName.toString();
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }
}
