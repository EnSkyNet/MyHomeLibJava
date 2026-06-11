package org.myhomelib.service;

import org.myhomelib.db.BookCollection;
import org.myhomelib.importer.Fb2Importer;
import org.myhomelib.importer.InpxImporter;
import org.myhomelib.model.Book;
import org.myhomelib.model.Fb2Book;
import org.myhomelib.reader.BookContentReader;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class LibraryService {
    private final BookCollection database;
    private final Fb2Importer fb2Importer;
    private final InpxImporter inpxImporter;
    private final BookContentReader bookContentReader;

    public LibraryService(BookCollection database) {
        this.database = database;
        this.fb2Importer = new Fb2Importer();
        this.inpxImporter = new InpxImporter();
        this.bookContentReader = new BookContentReader();
    }

    public void importFolder(Path folderPath, Consumer<String> statusLogger, boolean scanSubfolders, boolean includeArchives) {
        if (folderPath == null || !Files.isDirectory(folderPath)) {
            statusLogger.accept("Помилка: Вказаний шлях не є директорією");
            return;
        }

        statusLogger.accept("Початок сканування директорії: " + folderPath);
        List<Fb2Book> collectedBooks = new ArrayList<>();

        try {
            int maxDepth = scanSubfolders ? Integer.MAX_VALUE : 1;
            Files.walk(folderPath, maxDepth).forEach(path -> {
                String filename = path.getFileName().toString().toLowerCase();
                if (Files.isRegularFile(path)) {
                    if (filename.endsWith(".fb2")) {
                        try (InputStream is = new BufferedInputStream(new FileInputStream(path.toFile()))) {
                            long size = Files.size(path);
                            collectedBooks.add(fb2Importer.parseFb2(is, path, "", size));
                        } catch (IOException e) {
                            System.err.println("Помилка читання FB2: " + path + " - " + e.getMessage());
                        }
                    } else if (includeArchives && filename.endsWith(".zip")) {
                        processZipArchive(path, collectedBooks);
                    }
                }
            });

            if (!collectedBooks.isEmpty()) {
                statusLogger.accept("Зібрано " + collectedBooks.size() + " книг. Запис у базу даних...");
                int imported = database.importBooks(collectedBooks);
                statusLogger.accept("Успішно імпортовано нових книг: " + imported);
            } else {
                statusLogger.accept("Нічого не знайдено для імпорту.");
            }

        } catch (IOException e) {
            statusLogger.accept("Критична помилка сканування папки: " + e.getMessage());
        }
    }

    public void importFile(Path inpxPath, Consumer<Fb2Book> progressConsumer, Consumer<String> statusLogger) {
        if (inpxPath == null || !Files.isRegularFile(inpxPath)) {
            statusLogger.accept("Помилка: Невірний шлях до файлу INPX");
            return;
        }

        statusLogger.accept("Аналіз структури метаданих INPX: " + inpxPath.getFileName());
        List<Fb2Book> collectedBooks = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(inpxPath.toFile())))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".inp")) {
                    statusLogger.accept("Парсинг індексного файлу: " + entry.getName());
                    InputStream nonCloseableIs = new InputStream() {
                        @Override
                        public int read() throws IOException { return zis.read(); }
                        @Override
                        public int read(byte[] b, int off, int len) throws IOException { return zis.read(b, off, len); }
                    };

                    List<Fb2Book> booksFromInp = inpxImporter.parseInpFolder(nonCloseableIs, inpxPath);
                    for (Fb2Book b : booksFromInp) {
                        collectedBooks.add(b);
                        if (progressConsumer != null) {
                            progressConsumer.accept(b);
                        }
                    }
                }
                zis.closeEntry();
            }

            if (!collectedBooks.isEmpty()) {
                statusLogger.accept("Зчитано метаданих для " + collectedBooks.size() + " книг. Збереження...");
                int total = database.importBooks(collectedBooks);
                statusLogger.accept("Імпорт метаданих завершено! Додано унікальних записів: " + total);
            } else {
                statusLogger.accept("В архіві INPX не знайдено файлів індексів .inp");
            }

        } catch (IOException e) {
            statusLogger.accept("Помилка імпорту INPX файлу: " + e.getMessage());
        }
    }

    // СУМІСНІСТЬ: Метод, який викликається з MainFXApp.java (двопараметричний логер статусу)
    public void importInpx(Path inpxPath, Consumer<String> statusLogger) {
        // Перенаправляємо виклик на стандартний importFile, ігноруючи поштучний прогрес книг для спрощення логування
        importFile(inpxPath, null, statusLogger);
    }

    // СУМІСНІСТЬ: Перетворення сирого FB2 XML тексту в читабельний базовий HTML для UI компонентів читалок
    public String readBookHtml(Book book) throws IOException {
        if (book == null) {
            return "<html><body><h3>Помилка: Книга відсутня</h3></body></html>";
        }

        // Зчитуємо текст книги (декодований у UTF-8) за допомогою нашого BookContentReader
        String rawXml = bookContentReader.readBookText(book);

        // Простий та швидкий регекс-трансформатор XML-тегів FB2 в чистий HTML для рендерингу
        String htmlContent = rawXml
                .replaceAll("<p>", "<p style='margin-bottom: 8px; text-indent: 20px; font-family: sans-serif;'>")
                .replaceAll("</p>", "</p>")
                .replaceAll("<title>", "<h2 style='color: #2c3e50; font-family: sans-serif; text-align: center;'>")
                .replaceAll("</title>", "</h2>")
                .replaceAll("<subtitle>", "<h3 style='color: #7f8c8d; font-family: sans-serif; text-align: center;'>")
                .replaceAll("</subtitle>", "</h3>")
                .replaceAll("<empty-line/>", "<br/>")
                .replaceAll("<v>", "<p style='font-style: italic; margin-left: 40px; color: #555;'>")
                .replaceAll("</v>", "</p>");

        // Огортаємо в коректну валідну HTML структуру з базовою стилізацією для комфортного читання
        return "<html><head><meta charset='UTF-8'></head><body style='padding: 20px; background-color: #fdfefe; color: #1a1a1a; font-size: 14px; line-height: 1.6;'>"
                + "<h1>" + book.title() + "</h1>"
                + "<h4>" + book.authorsText() + "</h4>"
                + "<hr/>"
                + htmlContent
                + "</body></html>";
    }

    private void processZipArchive(Path zipPath, List<Fb2Book> collectedBooks) {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipPath.toFile())))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".fb2")) {
                    InputStream nonCloseableIs = new InputStream() {
                        @Override
                        public int read() throws IOException { return zis.read(); }
                        @Override
                        public int read(byte[] b, int off, int len) throws IOException { return zis.read(b, off, len); }
                    };
                    collectedBooks.add(fb2Importer.parseFb2(nonCloseableIs, zipPath, entry.getName(), entry.getSize()));
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            System.err.println("Помилка обробки архіву: " + zipPath + " - " + e.getMessage());
        }
    }
}