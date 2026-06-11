package org.myhomelib.service;

import org.myhomelib.db.BookCollection;
import org.myhomelib.model.SearchCriteria;
import org.myhomelib.model.SearchPreset;

import java.util.List;

public final class SearchPresetService {
    private final BookCollection database;

    public SearchPresetService(BookCollection database) {
        this.database = database;
    }

    public List<SearchPreset> getAllPresets() {
        // Тепер типи повністю збігаються з інтерфейсом та реалізацією в Database
        return database.loadSearchPresets();
    }

    public void createPreset(String name, SearchCriteria criteria) {
        if (name == null || name.strip().isEmpty()) {
            throw new IllegalArgumentException("Назва пресета не може бути порожньою");
        }
        database.saveSearchPreset(name, criteria);
    }

    public void removePreset(long id) {
        database.deleteSearchPreset(id);
    }
}