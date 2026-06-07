package org.myhomelib.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Fb2Book {
    private String title = "";
    private final List<Author> authors = new ArrayList<>();
    private final List<String> genres = new ArrayList<>();
    private String series = "";
    private Integer sequenceNumber;
    private String language = "";
    private String keywords = "";
    private String annotation = "";
    private Path sourcePath;
    private String archiveEntry = "";
    private long fileSize;

    public String title() {
        return title;
    }

    public void title(String title) {
        this.title = title == null ? "" : title.trim();
    }

    public List<Author> authors() {
        return authors;
    }

    public List<String> genres() {
        return genres;
    }

    public String series() {
        return series;
    }

    public void series(String series) {
        this.series = series == null ? "" : series.trim();
    }

    public Integer sequenceNumber() {
        return sequenceNumber;
    }

    public void sequenceNumber(Integer sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public String language() {
        return language;
    }

    public void language(String language) {
        this.language = language == null ? "" : language.trim();
    }

    public String keywords() {
        return keywords;
    }

    public void keywords(String keywords) {
        this.keywords = keywords == null ? "" : keywords.trim();
    }

    public String annotation() {
        return annotation;
    }

    public void annotation(String annotation) {
        this.annotation = annotation == null ? "" : annotation.trim();
    }

    public Path sourcePath() {
        return sourcePath;
    }

    public void sourcePath(Path sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String archiveEntry() {
        return archiveEntry;
    }

    public void archiveEntry(String archiveEntry) {
        this.archiveEntry = archiveEntry == null ? "" : archiveEntry.trim();
    }

    public long fileSize() {
        return fileSize;
    }

    public void fileSize(long fileSize) {
        this.fileSize = fileSize;
    }
}
