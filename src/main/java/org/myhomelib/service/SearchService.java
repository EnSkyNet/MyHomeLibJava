package org.myhomelib.service;

import org.myhomelib.db.repository.SearchRepository;
import org.myhomelib.model.Book;
import org.myhomelib.model.SearchCriteria;

import java.util.Collections;
import java.util.List;

public class SearchService {
    private final SearchRepository searchRepository;

    public SearchService(SearchRepository searchRepository) {
        this.searchRepository = searchRepository;
    }

    public List<Book> findBooks(SearchCriteria criteria) {
        if (criteria == null) {
            return Collections.emptyList();
        }
        // Бізнес-логіка: очищення пробілів для коректної роботи FTS5 MATCH
        String cleanTitle = criteria.title() != null ? criteria.title().trim() : "";
        String cleanAuthor = criteria.author() != null ? criteria.author().trim() : "";
        String cleanSeries = criteria.series() != null ? criteria.series().trim() : "";
        String cleanLang = "Всі мови".equals(criteria.language()) ? "" : criteria.language();

        SearchCriteria optimizedCriteria = new SearchCriteria(
                cleanTitle, cleanAuthor, "", cleanSeries, cleanLang
        );

        return searchRepository.searchBooks(optimizedCriteria);
    }

    public List<String> getAvailableLanguages() {
        return searchRepository.getDistinctLanguages();
    }
}