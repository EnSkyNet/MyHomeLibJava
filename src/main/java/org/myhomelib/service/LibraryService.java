package org.myhomelib.service;

import java.sql.Statement;

public class LibraryService {
    private final CollectionManager collectionManager;

    public LibraryService(CollectionManager collectionManager) {
        this.collectionManager = collectionManager;
    }

    public void clearEntireLibrary() throws Exception {
        try (Statement s = collectionManager.collectionDatabase().getConnection().createStatement()) {
            // Транзакційне каскадне видалення таблиць
            s.executeUpdate("DELETE FROM books");
            s.executeUpdate("DELETE FROM books_fts");
        }
    }
}