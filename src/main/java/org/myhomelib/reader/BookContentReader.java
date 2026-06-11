package org.myhomelib.reader;

import org.myhomelib.model.Book;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class BookContentReader {

    public String readBookText(Book book) throws IOException {
        if (book == null) {
            throw new IllegalArgumentException("Об'єкт книги не може бути null");
        }

        byte[] rawBytes = readBookBytes(book);
        if (rawBytes == null || rawBytes.length == 0) {
            return "Файл порожній або не знайдений";
        }

        // Повертаємо текстовий вміст у кодуванні UTF-8 для рендерингу в UI текстових компонентах
        return new String(rawBytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    public byte[] readBookBytes(Book book) throws IOException {
        if (book.hasArchiveEntry()) {
            // Книга знаходиться всередині ZIP архіву. book.folder() містить шлях до архіву
            Path zipFilePath = Paths.get(book.folder());
            if (!Files.exists(zipFilePath)) {
                throw new IOException("Файл архіву не знайдено за шляхом: " + zipFilePath);
            }

            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFilePath.toFile())))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().equals(book.archiveEntry())) {
                        byte[] data = readAllBytesFromStream(zis);
                        zis.closeEntry();
                        return data;
                    }
                    zis.closeEntry();
                }
            }
            throw new IOException("Запис '" + book.archiveEntry() + "' відсутній всередині архіву " + book.folder());
        } else {
            // Книга лежить на диску як звичайний розпакований .fb2 файл
            // book.folder() - шлях до директорії, book.fileName() - назва файлу
            Path plainFilePath = Paths.get(book.folder(), book.fileName());
            if (!Files.exists(plainFilePath)) {
                // Спроба прочитати суто за fileName, якщо папка не вказана
                plainFilePath = Paths.get(book.fileName());
                if (!Files.exists(plainFilePath)) {
                    throw new IOException("Файл книги не знайдено за шляхом: " + book.folder() + " / " + book.fileName());
                }
            }

            try (InputStream is = new BufferedInputStream(new FileInputStream(plainFilePath.toFile()))) {
                return readAllBytesFromStream(is);
            }
        }
    }

    private byte[] readAllBytesFromStream(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[8192];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }
}