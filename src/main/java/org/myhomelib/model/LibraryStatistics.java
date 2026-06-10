package org.myhomelib.model;

import java.util.Map;

public final class LibraryStatistics {

    private final int totalBooks;
    private final int totalAuthors;
    private final int totalGenres;
    private final Map<String, Integer> booksPerGenre;

    public LibraryStatistics(
            int totalBooks,
            int totalAuthors,
            int totalGenres,
            Map<String, Integer> booksPerGenre
    ) {
        this.totalBooks = totalBooks;
        this.totalAuthors = totalAuthors;
        this.totalGenres = totalGenres;
        this.booksPerGenre = booksPerGenre;
    }

    public int totalBooks() {
        return totalBooks;
    }

    public int totalAuthors() {
        return totalAuthors;
    }

    public int totalGenres() {
        return totalGenres;
    }

    public Map<String, Integer> booksPerGenre() {
        return booksPerGenre;
    }
}