package org.myhomelib.db;

import javax.swing.SwingWorker;
import org.myhomelib.model.Fb2Book;
import java.util.ArrayList;
import java.util.List;

public final class BookImportWorker extends SwingWorker<Integer, Integer> {
    private final BookCollection database;
    private final List<Fb2Book> allBooksToImport;
    private final int batchSize = 2000; // Оптимальний розмір пакета для SQLite WAL-транзакції
    private final ProgressListener progressListener;

    public interface ProgressListener {
        void onProgressUpdate(int percentage, int importedCount);
        void onImportComplete(int totalImported);
    }

    public BookImportWorker(BookCollection database, List<Fb2Book> books, ProgressListener listener) {
        this.database = database;
        this.allBooksToImport = books != null ? books : new ArrayList<>();
        this.progressListener = listener;
    }

    @Override
    protected Integer doInBackground() throws Exception {
        int totalImported = 0;
        int totalSize = allBooksToImport.size();

        if (totalSize == 0) {
            return 0;
        }

        List<Fb2Book> currentBatch = new ArrayList<>(batchSize);

        for (int i = 0; i < totalSize; i++) {
            if (isCancelled()) {
                break;
            }

            currentBatch.add(allBooksToImport.get(i));

            if (currentBatch.size() >= batchSize || i == totalSize - 1) {
                // Виклик нашого високошвидкісного атомарного методу з транзакцією
                int savedInBatch = database.importBooks(currentBatch);
                totalImported += savedInBatch;
                currentBatch.clear();

                int progressPercentage = (int) (((double) (i + 1) / totalSize) * 100);
                publish(progressPercentage, totalImported);
            }
        }
        return totalImported;
    }

    @Override
    protected void process(List<Integer> chunks) {
        if (progressListener != null && chunks.size() >= 2) {
            int importedCount = chunks.get(chunks.size() - 1);
            int percentage = chunks.get(chunks.size() - 2);
            progressListener.onProgressUpdate(percentage, importedCount);
        }
    }

    @Override
    protected void done() {
        try {
            int finalImportedCount = get();
            if (progressListener != null) {
                progressListener.onImportComplete(finalImportedCount);
            }
        } catch (Exception e) {
            if (progressListener != null) {
                progressListener.onImportComplete(0);
            }
        }
    }
}