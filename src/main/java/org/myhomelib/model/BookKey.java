package org.myhomelib.model;

public record BookKey(long bookId, long databaseId) {
    public static final long INVALID_ID = -1;
    public static final BookKey INVALID = new BookKey(INVALID_ID, INVALID_ID);

    public boolean isValid() {
        return bookId != INVALID_ID && databaseId != INVALID_ID;
    }
}
