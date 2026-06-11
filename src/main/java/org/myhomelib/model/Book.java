package org.myhomelib.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class Book {
    private final long id;
    private final String title;
    private final List<Author> authors;
    private final List<String> genres;
    private final String series;
    private final Integer sequenceNumber;
    private final String language;
    private final String fileName;
    private final String folder;
    private final String archiveEntry;
    private final long fileSize;
    private final String keywords;
    private final String annotation;
    private final int rate;
    private final int progress;
    private final LocalDateTime updateDate;

    public Book(long id, String title, List<Author> authors, List<String> genres, String series,
                Integer sequenceNumber, String language, String fileName, String folder,
                String archiveEntry, long fileSize, String keywords, String annotation,
                int rate, int progress, LocalDateTime updateDate) {
        this.id = id;
        this.title = title != null ? title.trim() : "";
        this.authors = authors != null ? new ArrayList<>(authors) : new ArrayList<>();
        this.genres = genres != null ? new ArrayList<>(genres) : new ArrayList<>();
        this.series = series != null ? series.trim() : null;
        this.sequenceNumber = sequenceNumber;
        this.language = language != null ? language.trim() : "";
        this.fileName = fileName != null ? fileName.trim() : "";
        this.folder = folder != null ? folder.trim() : "";
        this.archiveEntry = archiveEntry != null ? archiveEntry.trim() : "";
        this.fileSize = fileSize;
        this.keywords = keywords != null ? keywords.trim() : "";
        this.annotation = annotation != null ? annotation.trim() : "";
        this.rate = rate;
        this.progress = progress;
        this.updateDate = updateDate != null ? updateDate : LocalDateTime.now();
    }

    public long id() { return id; }
    public String title() { return title; }
    public List<Author> authors() { return new ArrayList<>(authors); }
    public List<String> genres() { return new ArrayList<>(genres); }
    public String series() { return series; }
    public Integer sequenceNumber() { return sequenceNumber; }
    public String language() { return language; }
    public String fileName() { return fileName; }
    public String folder() { return folder; }
    public String archiveEntry() { return archiveEntry; }
    public long fileSize() { return fileSize; }
    public String keywords() { return keywords; }
    public String annotation() { return annotation; }
    public int rate() { return rate; }
    public int progress() { return progress; }
    public LocalDateTime updateDate() { return updateDate; }

    // Сумісність: текстове представлення авторів
    public String authorsText() {
        if (authors.isEmpty()) return "Невідомий Автор";
        return authors.stream().map(Author::displayFullName).collect(Collectors.joining(", "));
    }

    // Сумісність: текстове представлення жанрів
    public String genresText() {
        return String.join(", ", genres);
    }

    // Сумісність з читалками: перевірка наявності книги в архіві zip/rar
    public boolean hasArchiveEntry() {
        return archiveEntry != null && !archiveEntry.isBlank();
    }

    // Сумісність: повертає ім'я файлу або шлях
    public String filePath() {
        return fileName;
    }

    // Сумісність з SystemDatabase
    public String archivePath() {
        return folder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Book book = (Book) o;
        return id == book.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Book{" + "id=" + id + ", title='" + title + '\'' + ", fileName='" + fileName + '\'' + '}';
    }
}