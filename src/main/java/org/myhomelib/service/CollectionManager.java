package org.myhomelib.service;

import org.myhomelib.db.DatabaseManager;
import org.myhomelib.db.SystemDatabase;
import org.myhomelib.db.repository.SearchRepository;
import org.myhomelib.model.CollectionInfo;
import org.myhomelib.model.CollectionType;
import org.myhomelib.model.SearchCriteria;

import java.nio.file.Path;
import java.util.List;

public final class CollectionManager implements AutoCloseable {
    private final SystemDatabase systemDatabase;
    // Прибрано модифікатор final, щоб мати можливість безпечно перемикати бази даних при зміні колекції
    private DatabaseManager collectionDatabase;
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

        // Ініціалізація бази даних через конструктор, як того вимагає нова архітектура
        this.collectionDatabase = new DatabaseManager(activeCollection.databasePath());
        this.collectionDatabase.open();

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

    public DatabaseManager collectionDatabase() {
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

            SearchRepository searchRepository = new SearchRepository(collectionDatabase);
            List<org.myhomelib.model.Book> allBooks = searchRepository.searchBooks(SearchCriteria.empty());

            systemDatabase.replaceBooks(activeCollection.id(), allBooks);

            System.out.println("[Синхронізація] Успішно синхронізовано " + allBooks.size() + " книг.");
        }).exceptionally(ex -> {
            System.err.println("[Синхронізація] Помилка під час фонової синхронізації:");
            ex.printStackTrace();
            return null;
        });
    }

    /**
     * Відкриває або реєструє нову колекцію, коректно перевизначаючи менеджер підключення.
     */
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

        // ВИПРАВЛЕНО: Закриваємо попереднє з'єднання перед відкриттям нового файлу БД
        if (collectionDatabase != null) {
            collectionDatabase.close();
        }

        // Створюємо новий екземпляр DatabaseManager для нового шляху, викликаючи метод open() без аргументів
        collectionDatabase = new DatabaseManager(collection.databasePath());
        collectionDatabase.open();

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
        if (collectionDatabase != null) {
            collectionDatabase.close();
        }
        if (systemDatabase != null) {
            systemDatabase.close();
        }
    }
}