package org.myhomelib.ui;

import org.myhomelib.db.Database;
import org.myhomelib.model.Book;
import org.myhomelib.model.CollectionInfo;
import org.myhomelib.service.CollectionManager;
import org.myhomelib.service.ImportResult;
import org.myhomelib.service.LibraryService;
import org.myhomelib.model.SearchCriteria;
import org.myhomelib.model.BookEdit;
import org.myhomelib.model.SearchPreset;
import org.myhomelib.service.SearchPresetService;

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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

public final class MainFrame extends JFrame {
    private final Database database;
    private final LibraryService libraryService;
    private final SearchPresetService searchPresetService;

    private final BookTableModel tableModel = new BookTableModel();
    private final JTable booksTable = new JTable(tableModel);

    private final DefaultListModel<String> authorsModel = new DefaultListModel<>();
    private final JList<String> authorsList = new JList<>(authorsModel);

    private final DefaultListModel<String> seriesModel = new DefaultListModel<>();
    private final JList<String> seriesList = new JList<>(seriesModel);

    private final DefaultListModel<String> genresModel = new DefaultListModel<>();
    private final JList<String> genresList = new JList<>(genresModel);

    private final DefaultListModel<String> groupsModel = new DefaultListModel<>();
    private final JList<String> groupsList = new JList<>(groupsModel);

    private final JTextField searchField = new JTextField();
    private final JTextArea annotationArea = new JTextArea();
    private final JTextArea reviewArea = new JTextArea();
    private final JLabel statusLabel = new JLabel(" Ready");

    private SearchCriteria currentSearchCriteria = SearchCriteria.empty();

    public MainFrame(Path dbPath) {
        setTitle("MyHomeLib Java");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1150, 780);
        setLocationRelativeTo(null);

        this.database = new Database(dbPath);
        this.libraryService = new LibraryService(database);
        this.searchPresetService = new SearchPresetService(database);

        buildUi();
        loadNavigationData();
        triggerFastSearch();
    }

    private void buildUi() {
        setJMenuBar(createMenuBar());

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(createToolBar(), BorderLayout.NORTH);

        JTabbedPane sidebarTabs = new JTabbedPane();
        sidebarTabs.addTab("Authors", new JScrollPane(authorsList));
        sidebarTabs.addTab("Series", new JScrollPane(seriesList));
        sidebarTabs.addTab("Genres", new JScrollPane(genresList));
        sidebarTabs.addTab("Groups", new JScrollPane(groupsList));
        sidebarTabs.setPreferredSize(new Dimension(240, 0));

        setupListSelectionListeners();

        booksTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        booksTable.setRowHeight(22);
        booksTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                int row = booksTable.getSelectedRow();
                if (row < 0) return;
                Book book = tableModel.bookAt(row);
                updateDetails(book);

                if (event.getClickCount() == 2) {
                    readBook(book);
                }
            }
        });

        setupTableContextMenu();

        JTabbedPane detailsTabs = new JTabbedPane();
        annotationArea.setEditable(false);
        annotationArea.setLineWrap(true);
        annotationArea.setWrapStyleWord(true);
        annotationArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        detailsTabs.addTab("Annotation", new JScrollPane(annotationArea));

        reviewArea.setLineWrap(true);
        reviewArea.setWrapStyleWord(true);
        reviewArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        detailsTabs.addTab("My Review", new JScrollPane(reviewArea));

        reviewArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { saveReview(); }
            @Override
            public void removeUpdate(DocumentEvent e) { saveReview(); }
            @Override
            public void changedUpdate(DocumentEvent e) { saveReview(); }

            private void saveReview() {
                int row = booksTable.getSelectedRow();
                if (row >= 0) {
                    Book book = tableModel.bookAt(row);
                    database.setReview(book.id(), reviewArea.getText().trim());
                }
            }
        });

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(booksTable), detailsTabs);
        rightSplit.setDividerLocation(420);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebarTabs, rightSplit);
        mainSplit.setDividerLocation(240);

        mainPanel.add(mainSplit, BorderLayout.CENTER);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.setBorder(BorderFactory.createEtchedBorder());
        statusPanel.add(statusLabel);
        mainPanel.add(statusPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private JMenuBar createMenuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem importFolderItem = new JMenuItem("Import Folder (FB2)...");
        importFolderItem.addActionListener(this::importFolder);
        JMenuItem importInpxItem = new JMenuItem("Import Archive Collection (.INPX)...");
        importInpxItem.addActionListener(this::importInpx);
        JMenuItem importGenresItem = new JMenuItem("Import Genres List (genres.list)...");
        importGenresItem.addActionListener(this::importGenres);
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));

        fileMenu.add(importFolderItem);
        fileMenu.add(importInpxItem);
        fileMenu.add(importGenresItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        JMenu viewMenu = new JMenu("View");
        JMenuItem statsItem = new JMenuItem("Statistics...");
        statsItem.addActionListener(e -> new StatisticsDialog(this, database.statistics()).setVisible(true));
        viewMenu.add(statsItem);

        bar.add(fileMenu);
        bar.add(viewMenu);
        return bar;
    }

    private JToolBar createToolBar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        searchField.setPreferredSize(new Dimension(280, 26));
        searchField.setMaximumSize(new Dimension(350, 26));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { triggerFastSearch(); }
            @Override
            public void removeUpdate(DocumentEvent e) { triggerFastSearch(); }
            @Override
            public void changedUpdate(DocumentEvent e) { triggerFastSearch(); }
        });

        JButton advSearchBtn = new JButton("Advanced Search");
        advSearchBtn.addActionListener(this::openAdvancedSearch);

        JButton savePresetBtn = new JButton("Save Preset");
        savePresetBtn.addActionListener(this::saveSearchPreset);

        JButton loadPresetBtn = new JButton("Presets...");
        loadPresetBtn.addActionListener(this::loadSearchPreset);

        JButton deletePresetBtn = new JButton("Delete Preset");
        deletePresetBtn.addActionListener(this::deleteSearchPreset);

        toolBar.add(new JLabel(" Quick Search: "));
        toolBar.add(searchField);
        toolBar.addSeparator();
        toolBar.add(advSearchBtn);
        toolBar.addSeparator();
        toolBar.add(savePresetBtn);
        toolBar.add(loadPresetBtn);
        toolBar.add(deletePresetBtn);

        return toolBar;
    }

    private void setupListSelectionListeners() {
        authorsList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && authorsList.getSelectedValue() != null) {
                clearOtherListsSelections(authorsList);
                String selected = authorsList.getSelectedValue();
                currentSearchCriteria = SearchCriteria.empty();
                tableModel.setBooks(database.searchAdvanced(new SearchCriteria(null, selected, null, null, null, null, null, null, null, null, null, null, null, null, null, null)));
                statusLabel.setText(" Found books by author: " + selected);
            }
        });

        seriesList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && seriesList.getSelectedValue() != null) {
                clearOtherListsSelections(seriesList);
                String selected = seriesList.getSelectedValue();
                currentSearchCriteria = SearchCriteria.empty();
                tableModel.setBooks(database.searchAdvanced(new SearchCriteria(null, null, null, selected, null, null, null, null, null, null, null, null, null, null, null, null)));
                statusLabel.setText(" Found books in series: " + selected);
            }
        });

        genresList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && genresList.getSelectedValue() != null) {
                clearOtherListsSelections(genresList);
                String selected = genresList.getSelectedValue();
                currentSearchCriteria = SearchCriteria.empty();
                tableModel.setBooks(database.searchAdvanced(new SearchCriteria(null, null, selected, null, null, null, null, null, null, null, null, null, null, null, null, null)));
                statusLabel.setText(" Found books in genre: " + selected);
            }
        });

        groupsList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && groupsList.getSelectedValue() != null) {
                clearOtherListsSelections(groupsList);
                String selected = groupsList.getSelectedValue();
                currentSearchCriteria = SearchCriteria.empty();
                tableModel.setBooks(database.searchAdvanced(new SearchCriteria(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, selected)));
                statusLabel.setText(" Group: " + selected);
            }
        });
    }

    private void clearOtherListsSelections(JList<?> activeList) {
        if (activeList != authorsList) authorsList.clearSelection();
        if (activeList != seriesList) seriesList.clearSelection();
        if (activeList != genresList) genresList.clearSelection();
        if (activeList != groupsList) groupsList.clearSelection();
    }

    private void setupTableContextMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem readItem = new JMenuItem("Read Book");
        readItem.addActionListener(e -> {
            int row = booksTable.getSelectedRow();
            if (row >= 0) readBook(tableModel.bookAt(row));
        });

        JMenuItem editItem = new JMenuItem("Edit Metadata...");
        editItem.addActionListener(e -> {
            int row = booksTable.getSelectedRow();
            if (row >= 0) editBook(tableModel.bookAt(row));
        });

        JMenuItem groupItem = new JMenuItem("Add to Group...");
        groupItem.addActionListener(this::addSelectedBookToGroup);

        JMenuItem exportItem = new JMenuItem("Export File...");
        exportItem.addActionListener(this::exportSelectedBook);

        menu.add(readItem);
        menu.add(editItem);
        menu.add(groupItem);
        menu.add(exportItem);

        booksTable.setComponentPopupMenu(menu);
    }

    private void loadNavigationData() {
        authorsModel.clear();
        database.listAuthors().forEach(authorsModel::addElement);

        seriesModel.clear();
        database.listSeries().forEach(seriesModel::addElement);

        genresModel.clear();
        database.listGenres().forEach(genresModel::addElement);

        groupsModel.clear();
        database.listGroups().forEach(groupsModel::addElement);
    }

    private void triggerFastSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            tableModel.setBooks(database.searchBooks("", 200));
            statusLabel.setText(" Showing top 200 items. Type to look up.");
        } else {
            List<Book> results = database.searchBooks(query, 500);
            tableModel.setBooks(results);
            statusLabel.setText(" Found match items: " + results.size());
        }
    }

    private void updateDetails(Book book) {
        annotationArea.setText(book.annotation() == null || book.annotation().isBlank() ? "[No summary available]" : book.annotation());
        annotationArea.setCaretPosition(0);
        reviewArea.setText(database.getReview(book.id()));
    }

    private void readBook(Book book) {
        ReaderDialog dialog = new ReaderDialog(this, book, libraryService);
        dialog.setVisible(true);
    }

    private void editBook(Book book) {
        BookEditDialog dialog = new BookEditDialog(this, book);
        dialog.setVisible(true);
        BookEdit edit = dialog.result();
        if (edit != null) {
            database.updateBook(book.id(), edit);
            triggerFastSearch();
            statusLabel.setText(" Book updated: " + edit.title());
        }
    }

    private void addSelectedBookToGroup(ActionEvent event) {
        int row = booksTable.getSelectedRow();
        if (row < 0) return;
        Book book = tableModel.bookAt(row);
        String name = JOptionPane.showInputDialog(this, "Enter group name:", "Add to Group", JOptionPane.QUESTION_MESSAGE);
        if (name != null && !name.isBlank()) {
            database.addBookToGroup(book.id(), name.trim());
            loadNavigationData();
            statusLabel.setText("Book placed into group: " + name);
        }
    }

    private void exportSelectedBook(ActionEvent event) {
        int row = booksTable.getSelectedRow();
        if (row < 0) return;
        Book book = tableModel.bookAt(row);

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(exportFileName(book)));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                libraryService.exportBook(book, chooser.getSelectedFile().toPath());
                JOptionPane.showMessageDialog(this, "Book successfully exported!", "Export", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Export failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void importFolder(ActionEvent event) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path path = chooser.getSelectedFile().toPath();
            statusLabel.setText(" Scanning folder contents...");
            new SwingWorker<ImportResult, Void>() {
                @Override
                protected ImportResult doInBackground() {
                    return libraryService.importFolder(path, statusLabel::setText);
                }
                @Override
                protected void done() {
                    try {
                        ImportResult res = get();
                        loadNavigationData();
                        triggerFastSearch();
                        JOptionPane.showMessageDialog(MainFrame.this, "Import completed! Added records: " + res.savedBooks(), "Import", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(MainFrame.this, "Error during process: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        }
    }

    private void importInpx(ActionEvent event) {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path path = chooser.getSelectedFile().toPath();
            statusLabel.setText(" Opening archive metadata index map...");
            new SwingWorker<ImportResult, Void>() {
                @Override
                protected ImportResult doInBackground() throws Exception {
                    return libraryService.importInpx(path, statusLabel::setText);
                }
                @Override
                protected void done() {
                    try {
                        ImportResult res = get();
                        loadNavigationData();
                        triggerFastSearch();
                        JOptionPane.showMessageDialog(MainFrame.this, "Indexed tracks synchronized: " + res.savedBooks(), "Import", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(MainFrame.this, "Failed index mapping process: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        }
    }

    private void importGenres(ActionEvent event) {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path path = chooser.getSelectedFile().toPath();
            try {
                int count = libraryService.importGenreList(path, "user");
                loadNavigationData();
                JOptionPane.showMessageDialog(this, "Genres directory structure entries generated: " + count, "Genres", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Parse failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void openAdvancedSearch(ActionEvent event) {
        AdvancedSearchDialog dialog = new AdvancedSearchDialog(this);
        dialog.setVisible(true);
        SearchCriteria criteria = dialog.result();
        if (criteria != null) {
            currentSearchCriteria = criteria;
            List<Book> books = database.searchAdvanced(criteria);
            tableModel.setBooks(books);
            statusLabel.setText(" Advanced search hits: " + books.size());
        }
    }

    private void saveSearchPreset(ActionEvent event) {
        if (currentSearchCriteria == null || currentSearchCriteria.equals(SearchCriteria.empty())) {
            JOptionPane.showMessageDialog(this, "Run an advanced search request before exporting filter configuration presets.", "Search Presets", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String name = JOptionPane.showInputDialog(this, "Assign filter configuration title:", "Save Search Criteria Preset", JOptionPane.QUESTION_MESSAGE);
        if (name != null && !name.isBlank()) {
            try {
                searchPresetService.savePreset(name, currentSearchCriteria);
                statusLabel.setText("Filter parameters cached under token: " + name);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void loadSearchPreset(ActionEvent event) {
        List<SearchPreset> presets = searchPresetService.presets();
        if (presets.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No saved presets found in your active collection directory configuration map.", "Search Presets", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        SearchPresetsDialog dialog = new SearchPresetsDialog(this, presets);
        dialog.setVisible(true);
        SearchPreset preset = dialog.selectedPreset();
        if (preset == null) return;

        currentSearchCriteria = preset.criteria();
        tableModel.setBooks(database.searchAdvanced(preset.criteria()));
        statusLabel.setText("Preset loaded: " + preset.name());
    }

    private void deleteSearchPreset(ActionEvent event) {
        List<SearchPreset> presets = searchPresetService.presets();
        if (presets.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No presets found.", "Search Presets", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        SearchPresetsDialog dialog = new SearchPresetsDialog(this, presets);
        dialog.setVisible(true);
        SearchPreset preset = dialog.selectedPreset();
        if (preset == null) return;
        int answer = JOptionPane.showConfirmDialog(this, "Delete preset \"" + preset.name() + "\" ?", "Delete Preset", JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) return;
        searchPresetService.deletePreset(preset.id());
        statusLabel.setText("Preset deleted: " + preset.name());
    }

    private static String exportFileName(Book book) {
        String source = book.hasArchiveEntry() ? book.archiveEntry() : book.fileName();
        String extension = ".fb2";
        int dot = source == null ? -1 : source.lastIndexOf('.');
        if (dot >= 0) extension = source.substring(dot);
        String title = book.title() == null || book.title().isBlank() ? "book" : book.title();
        return title.replaceAll("[<>:\"/\\\\|*?]", " ").trim() + extension;
    }
}