package org.myhomelib.ui;

import org.myhomelib.model.Book;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

final class BookTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {
            "Title", "Author", "Series", "No.", "Genre", "Lang", "Rate", "Progress", "File", "Size"
    };

    private final List<Book> books = new ArrayList<>();

    public void setBooks(List<Book> newBooks) {
        books.clear();
        books.addAll(newBooks);
        fireTableDataChanged();
    }

    public Book bookAt(int row) {
        return books.get(row);
    }

    public int bookCount() {
        return books.size();
    }

    @Override
    public int getRowCount() {
        return books.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Book book = books.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> book.title();
            case 1 -> book.authorsText();
            case 2 -> book.series();
            case 3 -> book.sequenceNumber() == null ? "" : book.sequenceNumber();
            case 4 -> book.genresText();
            case 5 -> book.language();
            case 6 -> book.rate();
            case 7 -> book.progress() + "%";
            case 8 -> book.fileName();
            case 9 -> formatSize(book.fileSize());
            default -> "";
        };
    }

    private static String formatSize(long size) {
        if (size <= 0) {
            return "";
        }
        if (size < 1024) {
            return size + " B";
        }
        if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        }
        return String.format("%.1f MB", size / 1024.0 / 1024.0);
    }
}
