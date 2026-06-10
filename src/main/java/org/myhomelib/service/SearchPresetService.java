package org.myhomelib.service;

import org.myhomelib.db.Database;
import org.myhomelib.model.SearchCriteria;
import org.myhomelib.model.SearchPreset;

import java.util.List;

public final class SearchPresetService {

    private final Database database;

    public SearchPresetService(Database database) {
        this.database = database;
    }

    public void savePreset(String name, SearchCriteria criteria) {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Preset name is empty");
        }

        database.saveSearchPreset(
                name.trim(),
                criteria
        );
    }

    public List<SearchPreset> presets() {
        return database.loadSearchPresets();
    }

    public void deletePreset(long presetId) {
        database.deleteSearchPreset(presetId);
    }

    public SearchPreset findById(long presetId) {

        return presets()
                .stream()
                .filter(p -> p.id() == presetId)
                .findFirst()
                .orElse(null);
    }

    public void renamePreset(
            long presetId,
            String newName
    ) {

        if (newName == null || newName.isBlank()) {
            return;
        }

        database.renameSearchPreset(
                presetId,
                newName.trim()
        );
    }
}