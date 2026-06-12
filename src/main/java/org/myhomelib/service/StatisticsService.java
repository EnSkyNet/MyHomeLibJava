package org.myhomelib.service;

import org.myhomelib.db.repository.SearchRepository;
import org.myhomelib.model.SearchCriteria;

public class StatisticsService {
    private final SearchRepository searchRepository;

    public StatisticsService(SearchRepository searchRepository) {
        this.searchRepository = searchRepository;
    }

    public long getTotalBooksCount() {
        // Отримуємо загальну кількість книг через пошук без лімітів та критеріїв
        return searchRepository.searchBooks(SearchCriteria.empty()).size();
    }

    public String getDatabaseEngineInfo() {
        return "SQLite Core with Full-Text Search (FTS5) & WAL Enabled";
    }
}