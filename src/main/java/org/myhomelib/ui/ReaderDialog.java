package org.myhomelib.ui;

import org.myhomelib.model.Book;
import org.myhomelib.service.LibraryService;

import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;

final class ReaderDialog extends JDialog {
    private final JEditorPane readerPane = new JEditorPane();
    private final Book book;
    private final LibraryService libraryService;

    ReaderDialog(JFrame owner, Book book, LibraryService libraryService) {
        super(owner, "Read: " + book.title(), false);
        this.book = book;
        this.libraryService = libraryService;
        setLayout(new BorderLayout());
        setMinimumSize(new Dimension(760, 620));
        setSize(920, 760);
        setLocationRelativeTo(owner);

        readerPane.setEditable(false);
        readerPane.setContentType("text/html");
        readerPane.setText("<html><body style='font-family: Segoe UI, Arial; padding: 20px;'>Loading...</body></html>");
        add(new JScrollPane(readerPane), BorderLayout.CENTER);
        load();
    }

    private void load() {
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return libraryService.readBookHtml(book);
            }

            @Override
            protected void done() {
                try {
                    readerPane.setText(get());
                } catch (Exception e) {
                    readerPane.setText("""
                            <html><body style='font-family: Segoe UI, Arial; padding: 20px;'>
                            <h2>Reader unavailable</h2>
                            <p>%s</p>
                            </body></html>
                            """.formatted(escape(e.getMessage())));
                }
                readerPane.setCaretPosition(0);
            }
        }.execute();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
