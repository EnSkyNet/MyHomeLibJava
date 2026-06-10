package org.myhomelib.service;

import org.myhomelib.db.Database;
import org.myhomelib.db.SystemDatabase;
import org.myhomelib.model.CollectionInfo;
import org.myhomelib.model.CollectionType;

import java.nio.file.Path;
import java.util.List;

public final class CollectionManager implements AutoCloseable {
    private final SystemDatabase systemDatabase;
    private final Database collectionDatabase;
    private CollectionInfo activeCollection;

    public CollectionManager(Path systemDatabasePath, Path defaultCollectionPath) {
        this.systemDatabase = new SystemDatabase(systemDatabasePath);
        long collectionId = systemDatabase.registerCollection(
                defaultCollectionPath,
                displayName(defaultCollectionPath),
                rootFolder(defaultCollectionPath),
                CollectionType.PRIVATE_FB
        );
        this.activeCollection = systemDatabase.collection(collectionId);
        this.collectionDatabase = new Database(activeCollection.databasePath());

        // !!! ВИДАЛІТЬ АБО ЗАКОМЕНТУЙТЕ ЦЕЙ РЯДОК !!!
        // syncActiveCollectionBooks();
    }

    public static CollectionManager open(Path collectionPath) {
        Path absoluteCollection = collectionPath.toAbsolutePath();
        return new CollectionManager(systemDatabasePath(absoluteCollection), absoluteCollection);
    }

    public SystemDatabase systemDatabase() {
        return systemDatabase;
    }

    public Database collectionDatabase() {
        return collectionDatabase;
    }

    public CollectionInfo activeCollection() {
        return activeCollection;
    }

    public List<CollectionInfo> collections() {
        return systemDatabase.collections();
    }

    public void syncActiveCollectionBooks() {
        if (activeCollection == null) {
            return;
        }

        // Запускаємо важку синхронізацію асинхронно у фоновому потоці Java
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            System.out.println("[Синхронізація] Початок обробки книг у фоні...");

            // База даних буде зчитувати книги у фоні, UI залишається повністю робочим
            List<org.myhomelib.model.Book> allBooks = collectionDatabase.searchBooks("");
            systemDatabase.replaceBooks(activeCollection.id(), allBooks);

            System.out.println("[Синхронізація] Успішно синхронізовано " + allBooks.size() + " книг.");
        }).exceptionally(ex -> {
            System.err.println("[Синхронізація] Помилка під час фонової синхронізації:");
            ex.printStackTrace();
            return null;
        });
    }

    public void openOrRegisterCollection(Path databasePath) {
        long id = systemDatabase.registerCollection(
                databasePath,
                displayName(databasePath),
                rootFolder(databasePath),
                CollectionType.PRIVATE_FB
        );
        CollectionInfo collection = systemDatabase.collection(id);
        if (collection == null) {
            throw new IllegalStateException("Collection was not registered: " + databasePath);
        }
        collectionDatabase.open(collection.databasePath());
        activeCollection = collection;
        //syncActiveCollectionBooks();
    }

    private static Path systemDatabasePath(Path collectionPath) {
        Path parent = collectionPath.getParent();
        String fileName = collectionPath.getFileName() == null ? "myhomelib-java" : collectionPath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot <= 0 ? fileName : fileName.substring(0, dot);
        Path systemFile = Path.of(base + ".dbs");
        return parent == null ? systemFile : parent.resolve(systemFile);
    }

    private static Path rootFolder(Path databasePath) {
        Path parent = databasePath.toAbsolutePath().getParent();
        return parent == null ? Path.of("") : parent;
    }

    private static String displayName(Path databasePath) {
        Path fileName = databasePath.getFileName();
        if (fileName == null) {
            return "Collection";
        }
        String value = fileName.toString();
        int dot = value.lastIndexOf('.');
        return dot <= 0 ? value : value.substring(0, dot);
    }

    @Override
    public void close() {
        collectionDatabase.close();
        systemDatabase.close();
    }
}
