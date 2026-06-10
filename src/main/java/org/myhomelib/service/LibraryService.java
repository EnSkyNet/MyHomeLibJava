package org.myhomelib.service;

import org.myhomelib.db.Database;
import org.myhomelib.importer.Fb2Importer;
import org.myhomelib.importer.GenreListImporter;
import org.myhomelib.importer.InpxImporter;
import org.myhomelib.model.Book;
import org.myhomelib.model.Fb2Book;
import org.myhomelib.reader.BookContentReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class LibraryService {
    private final Database database;
    private final BookContentReader contentReader = new BookContentReader();

    public LibraryService(Database database) {
        this.database = database;
    }

    public ImportResult importFolder(Path folder, Consumer<String> status) {
        List<Fb2Book> books = new Fb2Importer().importFolder(
                folder,
                status,
                isEnabled("import.readFb2"),
                isEnabled("import.readZip")
        );
        int saved = database.importBooks(books);
        return new ImportResult(books, saved);
    }

    public ImportResult importInpx(Path inpxFile, Consumer<String> status) throws Exception {
        if (!isEnabled("import.readInpx")) {
            return new ImportResult(List.of(), 0);
        }

        int[] totalSaved = {0};
        // Збільшуємо початкову місткість буфера до 20 000
        List<Fb2Book> batch = new ArrayList<>(20000);

        new InpxImporter().importFile(
                inpxFile,
                book -> {
                    batch.add(book);
                    // Скидаємо в базу кожні 20 000 книг
                    if (batch.size() >= 20000) {
                        totalSaved[0] += database.importBooks(batch);
                        batch.clear();
                    }
                },
                status
        );

        // Записуємо залишок
        if (!batch.isEmpty()) {
            totalSaved[0] += database.importBooks(batch);
            batch.clear();
        }

        return new ImportResult(List.of(), totalSaved[0]);
    }

    public int importGenreList(Path file, String source) throws Exception {
        return database.importGenreList(new GenreListImporter().importFile(file), source);
    }

    public String readBookHtml(Book book) throws Exception {
        return contentReader.readHtml(book);
    }

    public void exportBook(Book book, Path destination) throws Exception {
        Path parent = destination.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(destination, contentReader.readBytes(book));
    }

    public Map<String, String> settings() {
        return database.settings();
    }

    private boolean isEnabled(String key) {
        return Boolean.parseBoolean(database.setting(key, "true"));
    }
}