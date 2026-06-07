package org.myhomelib.db;

import org.myhomelib.model.Book;
import org.myhomelib.model.BookEdit;
import org.myhomelib.model.Fb2Book;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface BookCollection extends AutoCloseable {
    void open(Path newPath);

    Path path();

    int importBooks(List<Fb2Book> books);

    List<Book> searchBooks(String query);

    List<String> listAuthors();

    List<String> listSeries();

    List<String> listGenres();

    List<String> listGroups();

    Map<String, Integer> statistics();

    Map<String, String> settings();

    String setting(String key, String fallback);

    void putSetting(String key, String value);

    void updateBook(long bookId, BookEdit edit);

    void setRate(long bookId, int rate);

    void setProgress(long bookId, int progress);

    String getReview(long bookId);

    void setReview(long bookId, String review);

    void addBookToGroup(long bookId, String groupName);

    void removeBookFromGroup(long bookId, String groupName);

    List<String> groupsForBook(long bookId);

    boolean hideDeleted();

    void setHideDeleted(boolean hideDeleted);

    boolean showLocalOnly();

    void setShowLocalOnly(boolean showLocalOnly);

    String authorFilterType();

    void setAuthorFilterType(String authorFilterType);

    String seriesFilterType();

    void setSeriesFilterType(String seriesFilterType);
}
