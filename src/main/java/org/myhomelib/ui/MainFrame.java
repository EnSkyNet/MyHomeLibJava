package org.myhomelib.ui;

import org.myhomelib.db.Database;
import org.myhomelib.model.Book;
import org.myhomelib.model.CollectionInfo;
import org.myhomelib.service.CollectionManager;
import org.myhomelib.service.ImportResult;
import org.myhomelib.service.LibraryService;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class MainFrame extends JFrame {
    private final CollectionManager collectionManager;
    private final Database database;
    private final LibraryService libraryService;
    private final BookTableModel tableModel = new BookTableModel();
    private final JTable booksTable = new JTable(tableModel);
    private final JTextField searchField = new JTextField();
    private final JTextArea detailArea = new JTextArea();
    private final JLabel statusLabel = new JLabel("Ready");
    private final DefaultListModel<String> authorsModel = new DefaultListModel<>();
    private final DefaultListModel<String> seriesModel = new DefaultListModel<>();
    private final DefaultListModel<String> genresModel = new DefaultListModel<>();
    private final DefaultListModel<String> groupsModel = new DefaultListModel<>();

    public MainFrame(Path dbPath) {
        super("MyHomeLib Java");
        collectionManager = CollectionManager.open(dbPath);
        database = collectionManager.collectionDatabase();
        libraryService = new LibraryService(database);
        configureFrame();
        buildMenu();
        buildContent();
        refreshAll();
    }

    private void configureFrame() {
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(980, 680));
        setLocationByPlatform(true);
        setSize(1120, 760);
    }

    @Override
    public void dispose() {
        collectionManager.close();
        super.dispose();
    }

    private void buildMenu() {
        JMenuBar menuBar = new JMenuBar();

        JMenu file = new JMenu("File");
        file.add(item("Open collection...", this::openCollection));
        file.add(item("Registered collections...", this::showCollections));
        file.add(item("Import FB2/FBD/ZIP folder...", this::importFolder));
        file.add(item("Import INPX...", this::importInpx));
        file.add(item("Import genre list...", this::importGenres));
        file.addSeparator();
        file.add(item("Export selected book...", this::exportSelectedBook));
        file.add(item("Open selected book", this::openSelectedBook));
        file.add(item("Exit", event -> dispose()));

        JMenu book = new JMenu("Book");
        book.add(item("Read inside app", this::readSelectedBook));
        book.add(item("Open", this::openSelectedBook));
        book.add(item("Export...", this::exportSelectedBook));
        book.add(item("Edit...", this::editSelectedBook));
        book.add(item("Edit review...", this::editSelectedReview));
        book.add(item("Add to Favorites", event -> addSelectedToGroup("Favorites")));
        book.add(item("Add to group...", this::addSelectedToGroupDialog));
        book.add(item("Remove from group...", this::removeSelectedFromGroupDialog));
        book.addSeparator();
        book.add(item("Set rate...", this::setSelectedRate));
        book.add(item("Set progress...", this::setSelectedProgress));

        JMenu view = new JMenu("View");
        view.add(item("Refresh", event -> refreshAll()));
        view.add(item("Statistics", this::showStatistics));
        view.add(item("Settings", this::showSettings));

        JMenu help = new JMenu("Help");
        help.add(item("About", this::showAbout));

        menuBar.add(file);
        menuBar.add(book);
        menuBar.add(view);
        menuBar.add(help);
        setJMenuBar(menuBar);
    }

    private JMenuItem item(String title, java.awt.event.ActionListener listener) {
        JMenuItem item = new JMenuItem(title);
        item.addActionListener(listener);
        return item;
    }

    private void buildContent() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setContentPane(root);

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        JButton openButton = new JButton("Open");
        openButton.addActionListener(this::openCollection);
        JButton importButton = new JButton("Import FB2");
        importButton.addActionListener(this::importFolder);
        JButton importInpxButton = new JButton("Import INPX");
        importInpxButton.addActionListener(this::importInpx);
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(event -> refreshAll());
        JButton readButton = new JButton("Read");
        readButton.addActionListener(this::readSelectedBook);
        JButton openBookButton = new JButton("Open Book");
        openBookButton.addActionListener(this::openSelectedBook);
        JButton editButton = new JButton("Edit");
        editButton.addActionListener(this::editSelectedBook);
        JButton favoriteButton = new JButton("Favorite");
        favoriteButton.addActionListener(event -> addSelectedToGroup("Favorites"));
        toolbar.add(openButton);
        toolbar.add(importButton);
        toolbar.add(importInpxButton);
        toolbar.add(refreshButton);
        toolbar.add(readButton);
        toolbar.add(openBookButton);
        toolbar.add(editButton);
        toolbar.add(favoriteButton);
        toolbar.addSeparator();
        toolbar.add(new JLabel("Search: "));
        searchField.setMaximumSize(new Dimension(420, 30));
        toolbar.add(searchField);
        root.add(toolbar, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("All books", makePlaceholder("All imported books are shown in the table."));
        tabs.addTab("Authors", listPanel(authorsModel));
        tabs.addTab("Series", listPanel(seriesModel));
        tabs.addTab("Genres", listPanel(genresModel));
        tabs.addTab("Groups", listPanel(groupsModel));
        tabs.addTab("Download", makePlaceholder("Download list is reserved for a later LibRusEc/Flibusta port."));

        booksTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        booksTable.setAutoCreateRowSorter(true);
        booksTable.getColumnModel().getColumn(0).setPreferredWidth(220);
        booksTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        booksTable.getColumnModel().getColumn(4).setPreferredWidth(160);
        booksTable.getSelectionModel().addListSelectionListener(event -> showSelectedBook());
        booksTable.setComponentPopupMenu(bookPopupMenu());

        detailArea.setEditable(false);
        detailArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);

        JSplitPane vertical = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(booksTable),
                new JScrollPane(detailArea)
        );
        vertical.setResizeWeight(0.68);

        JSplitPane horizontal = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tabs, vertical);
        horizontal.setResizeWeight(0.22);
        root.add(horizontal, BorderLayout.CENTER);

        JPanel status = new JPanel(new FlowLayout(FlowLayout.LEFT));
        status.add(statusLabel);
        root.add(status, BorderLayout.SOUTH);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshBooks();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshBooks();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshBooks();
            }
        });
    }

    private JPanel listPanel(DefaultListModel<String> model) {
        JPanel panel = new JPanel(new BorderLayout());
        JList<String> list = new JList<>(model);
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && list.getSelectedValue() != null) {
                searchField.setText(list.getSelectedValue());
            }
        });
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        return panel;
    }

    private JPanel makePlaceholder(String text) {
        JPanel panel = new JPanel(new GridLayout(1, 1));
        JLabel label = new JLabel(text);
        label.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.add(label);
        return panel;
    }

    private void openCollection(ActionEvent event) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open or create MyHomeLib Java collection");
        chooser.setSelectedFile(database.path().toFile());
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        collectionManager.openOrRegisterCollection(chooser.getSelectedFile().toPath());
        refreshAll();
    }

    private void importFolder(ActionEvent event) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select folder with FB2/FBD/ZIP books");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path folder = chooser.getSelectedFile().toPath();
        setBusy(true, "Importing books from " + folder + "...");
        new SwingWorker<ImportResult, Void>() {
            @Override
            protected ImportResult doInBackground() {
                return libraryService.importFolder(folder, status -> statusLabel.setText(status));
            }

            @Override
            protected void done() {
                try {
                    ImportResult result = get();
                    refreshAll();
                    statusLabel.setText("Imported " + result.savedBooks() + " of " + result.scannedBooks().size() + " scanned book(s) from " + folder);
                } catch (Exception e) {
                    showError("Import failed", e);
                } finally {
                    setBusy(false, null);
                }
            }
        }.execute();
    }

    private void importInpx(ActionEvent event) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import INPX catalog");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path file = chooser.getSelectedFile().toPath();
        setBusy(true, "Importing INPX " + file + "...");
        new SwingWorker<ImportResult, Void>() {
            @Override
            protected ImportResult doInBackground() throws Exception {
                return libraryService.importInpx(file, status -> statusLabel.setText(status));
            }

            @Override
            protected void done() {
                try {
                    ImportResult result = get();
                    refreshAll();
                    statusLabel.setText("Imported " + result.savedBooks() + " of " + result.scannedBooks().size() + " INPX record(s)");
                } catch (Exception e) {
                    showError("INPX import failed", e);
                } finally {
                    setBusy(false, null);
                }
            }
        }.execute();
    }

    private void importGenres(ActionEvent event) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import MyHomeLib genre list");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path file = chooser.getSelectedFile().toPath();
        String source = file.getFileName().toString().toLowerCase().contains("nonfb2") ? "nonfb2" : "fb2";
        try {
            int count = libraryService.importGenreList(file, source);
            refreshAll();
            statusLabel.setText("Imported " + count + " genre(s) from " + file.getFileName());
        } catch (Exception e) {
            showError("Genre import failed", e);
        }
    }

    private void refreshAll() {
        refreshBooks();
        collectionManager.syncActiveCollectionBooks();
        fill(authorsModel, database.listAuthors());
        fill(seriesModel, database.listSeries());
        fill(genresModel, database.listGenres());
        fill(groupsModel, database.listGroups());
        CollectionInfo active = collectionManager.activeCollection();
        String name = active == null ? database.path().toString() : active.displayName();
        statusLabel.setText("Collection: " + name + " | " + database.path() + " | " + tableModel.bookCount() + " book(s)");
    }

    private void refreshBooks() {
        tableModel.setBooks(database.searchBooks(searchField.getText()));
        statusLabel.setText(tableModel.bookCount() + " book(s)");
    }

    private void fill(DefaultListModel<String> model, List<String> values) {
        model.clear();
        for (String value : values) {
            model.addElement(value);
        }
    }

    private void showSelectedBook() {
        Book book = selectedBook();
        if (book == null) {
            detailArea.setText("");
            return;
        }
        List<String> groups = database.groupsForBook(book.id());
        detailArea.setText("""
                Title: %s
                Author: %s
                Series: %s %s
                Genres: %s
                Groups: %s
                Language: %s
                File: %s
                Size: %d bytes
                Rate: %d
                Progress: %d%%
                Updated: %s

                Keywords:
                %s

                Annotation:
                %s

                Review:
                %s
                """.formatted(
                book.title(),
                book.authorsText(),
                book.series() == null ? "" : book.series(),
                book.sequenceNumber() == null ? "" : "#" + book.sequenceNumber(),
                book.genresText(),
                groups.isEmpty() ? "" : String.join(", ", groups),
                book.language() == null ? "" : book.language(),
                book.archivePath(),
                book.fileSize(),
                book.rate(),
                book.progress(),
                book.updateDate(),
                book.keywords() == null ? "" : book.keywords(),
                book.annotation() == null ? "" : book.annotation(),
                database.getReview(book.id())
        ));
        detailArea.setCaretPosition(0);
    }

    private JPopupMenu bookPopupMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(item("Read inside app", this::readSelectedBook));
        menu.add(item("Open", this::openSelectedBook));
        menu.add(item("Export...", this::exportSelectedBook));
        menu.add(item("Edit...", this::editSelectedBook));
        menu.add(item("Edit review...", this::editSelectedReview));
        menu.add(item("Add to Favorites", event -> addSelectedToGroup("Favorites")));
        menu.add(item("Add to group...", this::addSelectedToGroupDialog));
        menu.add(item("Remove from group...", this::removeSelectedFromGroupDialog));
        menu.addSeparator();
        menu.add(item("Set rate...", this::setSelectedRate));
        menu.add(item("Set progress...", this::setSelectedProgress));
        return menu;
    }

    private Book selectedBook() {
        int viewRow = booksTable.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        int modelRow = booksTable.convertRowIndexToModel(viewRow);
        return tableModel.bookAt(modelRow);
    }

    private void openSelectedBook(ActionEvent event) {
        Book book = selectedBook();
        if (book == null) {
            statusLabel.setText("Select a book first");
            return;
        }
        if (!Desktop.isDesktopSupported()) {
            statusLabel.setText("Desktop open is not supported on this system");
            return;
        }
        try {
            Desktop.getDesktop().open(book.filePath().toFile());
            statusLabel.setText("Opened " + book.filePath());
        } catch (IOException | IllegalArgumentException e) {
            showError("Cannot open book file", e);
        }
    }

    private void readSelectedBook(ActionEvent event) {
        Book book = selectedBook();
        if (book == null) {
            statusLabel.setText("Select a book first");
            return;
        }
        ReaderDialog dialog = new ReaderDialog(this, book, libraryService);
        dialog.setVisible(true);
    }

    private void exportSelectedBook(ActionEvent event) {
        Book book = selectedBook();
        if (book == null) {
            statusLabel.setText("Select a book first");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export selected book");
        chooser.setSelectedFile(Path.of(exportFileName(book)).toFile());
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            Path destination = chooser.getSelectedFile().toPath();
            libraryService.exportBook(book, destination);
            statusLabel.setText("Exported book to " + destination);
        } catch (Exception e) {
            showError("Export failed", e);
        }
    }

    private void editSelectedBook(ActionEvent event) {
        Book book = selectedBook();
        if (book == null) {
            statusLabel.setText("Select a book first");
            return;
        }
        BookEditDialog dialog = new BookEditDialog(this, book);
        dialog.setVisible(true);
        if (dialog.result() == null) {
            return;
        }
        database.updateBook(book.id(), dialog.result());
        refreshAll();
        statusLabel.setText("Updated " + dialog.result().title());
    }

    private void editSelectedReview(ActionEvent event) {
        Book book = selectedBook();
        if (book == null) {
            statusLabel.setText("Select a book first");
            return;
        }
        JTextArea reviewArea = new JTextArea(database.getReview(book.id()), 12, 60);
        reviewArea.setLineWrap(true);
        reviewArea.setWrapStyleWord(true);
        int result = JOptionPane.showConfirmDialog(
                this,
                new JScrollPane(reviewArea),
                "Review: " + book.title(),
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        database.setReview(book.id(), reviewArea.getText());
        refreshAll();
        statusLabel.setText("Updated review for " + book.title());
    }

    private void addSelectedToGroupDialog(ActionEvent event) {
        String group = JOptionPane.showInputDialog(this, "Group name:", "Add to group", JOptionPane.PLAIN_MESSAGE);
        addSelectedToGroup(group);
    }

    private void addSelectedToGroup(String groupName) {
        Book book = selectedBook();
        if (book == null) {
            statusLabel.setText("Select a book first");
            return;
        }
        if (groupName == null || groupName.isBlank()) {
            return;
        }
        database.addBookToGroup(book.id(), groupName);
        showSelectedBook();
        statusLabel.setText("Added to group: " + groupName.trim());
    }

    private void removeSelectedFromGroupDialog(ActionEvent event) {
        Book book = selectedBook();
        if (book == null) {
            statusLabel.setText("Select a book first");
            return;
        }
        List<String> groups = database.groupsForBook(book.id());
        if (groups.isEmpty()) {
            statusLabel.setText("The selected book is not in any group");
            return;
        }
        String group = (String) JOptionPane.showInputDialog(
                this,
                "Group:",
                "Remove from group",
                JOptionPane.PLAIN_MESSAGE,
                null,
                groups.toArray(String[]::new),
                groups.getFirst()
        );
        if (group == null) {
            return;
        }
        database.removeBookFromGroup(book.id(), group);
        showSelectedBook();
        statusLabel.setText("Removed from group: " + group);
    }

    private void setSelectedRate(ActionEvent event) {
        Book book = selectedBook();
        if (book == null) {
            statusLabel.setText("Select a book first");
            return;
        }
        Integer[] values = {0, 1, 2, 3, 4, 5};
        Integer rate = (Integer) JOptionPane.showInputDialog(this, "Rate:", "Set rate", JOptionPane.PLAIN_MESSAGE, null, values, book.rate());
        if (rate == null) {
            return;
        }
        database.setRate(book.id(), rate);
        refreshAll();
    }

    private void setSelectedProgress(ActionEvent event) {
        Book book = selectedBook();
        if (book == null) {
            statusLabel.setText("Select a book first");
            return;
        }
        String value = JOptionPane.showInputDialog(this, "Progress 0-100:", book.progress());
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            database.setProgress(book.id(), Integer.parseInt(value.trim()));
            refreshAll();
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Progress must be an integer.", "Invalid value", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showStatistics(ActionEvent event) {
        Map<String, Integer> stats = database.statistics();
        StringBuilder text = new StringBuilder();
        stats.forEach((key, value) -> text.append(key).append(": ").append(value).append(System.lineSeparator()));
        JOptionPane.showMessageDialog(this, text.toString(), "Collection statistics", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showSettings(ActionEvent event) {
        Map<String, String> settings = libraryService.settings();
        StringBuilder text = new StringBuilder();
        settings.forEach((key, value) -> text.append(key).append(" = ").append(value).append(System.lineSeparator()));
        JOptionPane.showMessageDialog(this, text.toString(), "Collection settings", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showCollections(ActionEvent event) {
        StringBuilder text = new StringBuilder();
        text.append("System DB: ").append(collectionManager.systemDatabase().path()).append(System.lineSeparator()).append(System.lineSeparator());
        CollectionInfo active = collectionManager.activeCollection();
        for (CollectionInfo collection : collectionManager.collections()) {
            text.append(active != null && collection.id() == active.id() ? "* " : "  ")
                    .append(collection.id())
                    .append(" | ")
                    .append(collection.displayName())
                    .append(" | ")
                    .append(collection.databasePath())
                    .append(System.lineSeparator());
        }
        JOptionPane.showMessageDialog(this, text.toString(), "Registered collections", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showAbout(ActionEvent event) {
        JOptionPane.showMessageDialog(
                this,
                "MyHomeLib Java 0.1.0\nJava/Swing port foundation for the Delphi MyHomeLib project.",
                "About",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void setBusy(boolean busy, String message) {
        setCursor(busy ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR) : java.awt.Cursor.getDefaultCursor());
        if (message != null) {
            statusLabel.setText(message);
        }
    }

    private void showError(String title, Exception e) {
        JOptionPane.showMessageDialog(this, e.getMessage(), title, JOptionPane.ERROR_MESSAGE);
        statusLabel.setText(title);
    }

    private static String exportFileName(Book book) {
        String source = book.hasArchiveEntry() ? book.archiveEntry() : book.fileName();
        String extension = ".fb2";
        int dot = source == null ? -1 : source.lastIndexOf('.');
        if (dot >= 0) {
            extension = source.substring(dot);
        }
        String title = book.title() == null || book.title().isBlank() ? "book" : book.title();
        return title.replaceAll("[<>:\"/\\\\|*?]", " ").trim() + extension;
    }
}
