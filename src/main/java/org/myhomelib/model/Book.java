package org.myhomelib.model;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

public record Book(
        long id,
        String title,
        List<Author> authors,
        List<String> genres,
        String series,
        Integer sequenceNumber,
        String language,
        String fileName,
        String folder,
        String archiveEntry,
        long fileSize,
        String keywords,
        String annotation,
        int rate,
        int progress,
        LocalDateTime updateDate
) {
    public String authorsText() {
        if (authors == null || authors.isEmpty()) {
            return "";
        }
        return authors.stream().map(Author::displayName).reduce((a, b) -> a + ", " + b).orElse("");
    }

    public String genresText() {
        if (genres == null || genres.isEmpty()) {
            return "";
        }
        return String.join(", ", genres);
    }

    public Path filePath() {
        if (folder == null || folder.isBlank()) {
            return Path.of(fileName == null ? "" : fileName);
        }
        return Path.of(folder, fileName == null ? "" : fileName);
    }

    public boolean hasArchiveEntry() {
        return archiveEntry != null && !archiveEntry.isBlank();
    }

    public String archivePath() {
        if (!hasArchiveEntry()) {
            return filePath().toString();
        }
        return filePath() + "!" + archiveEntry;
    }
}
