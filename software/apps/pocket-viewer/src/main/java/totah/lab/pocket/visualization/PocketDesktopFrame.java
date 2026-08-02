package totah.lab.pocket.visualization;

import com.formdev.flatlaf.FlatDarkLaf;
import com.jme3.math.ColorRGBA;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeCanvasContext;
import totah.lab.athena.pocket.geometry.PocketGeometry;
import totah.lab.athena.pocket.selection.PocketResidueSelection;
import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

final class PocketDesktopFrame extends JFrame {
    private final PocketViewerApp viewer;
    private Protein protein;
    private Pocket pocket;
    private List<Pocket> pockets;
    private final PocketResidueSelection residueSelection =
            new PocketResidueSelection();
    private List<LocatedResidue> visiblePocketResidues = List.of();
    private final DefaultTableModel pocketModel =
            readOnlyModel(
                    "Name", "Source", "Score",
                    "Druggability", "Residues");
    private final DefaultTableModel residueModel =
            readOnlyModel("Chain", "Residue", "Number", "Atoms");
    private final DefaultTableModel selectionModel =
            readOnlyModel("Chain", "Residue", "Number");
    private final JTable pocketTable = new JTable(pocketModel);
    private final JTable residueTable = new JTable(residueModel);
    private final TableRowSorter<DefaultTableModel> residueSorter =
            new TableRowSorter<>(residueModel);
    private final JTable selectionTable = new JTable(selectionModel);
    private final JTextField selectionSearch = new JTextField();
    private final JTextArea distanceSummary = new JTextArea(5, 24);
    private final JTextField atomColorName = new JTextField("SG", 4);
    private final JComboBox<String> distanceMode = new JComboBox<>(
            new String[]{"Minimum heavy atom", "Cα–Cα", "SG–SG"});
    private final List<LocatedResidue> selectedResidues = new ArrayList<>();
    private final JTextField residueSearch = new JTextField();
    private final JToggleButton.ToggleButtonModel pocketLabelsModel =
            new JToggleButton.ToggleButtonModel();
    private final JToggleButton.ToggleButtonModel proteinLabelsModel =
            new JToggleButton.ToggleButtonModel();
    private final JTextArea inspector = new JTextArea(5, 40);
    private final JLabel status = new JLabel("Ready");
    private final JTree projectTree = new JTree();
    private boolean refreshingTables;

    private PocketDesktopFrame(
            PocketViewerApp viewer,
            PocketDataset dataset,
            Pocket pocket,
            Component canvas) {
        super("Pocket Viewer — " + dataset.protein().id());
        this.viewer = viewer;
        this.protein = dataset.protein();
        this.pockets = dataset.pockets();
        this.pocket = pocket;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 720));
        setSize(1440, 900);
        setLocationByPlatform(true);
        setJMenuBar(createMenuBar());
        add(createToolbar(), BorderLayout.NORTH);
        add(createWorkspace(canvas), BorderLayout.CENTER);
        add(createStatusBar(), BorderLayout.SOUTH);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                viewer.stop();
            }
        });
        viewer.setSelectionListener(text ->
                SwingUtilities.invokeLater(() -> inspector.setText(text)));
        refreshDataset();
    }

    static void open(PocketDataset dataset, Pocket pocket) {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty(
                "com.apple.macos.application.appearance",
                "system");
        SwingUtilities.invokeLater(() -> {
            FlatDarkLaf.setup();

            PocketViewerApp viewer =
                    new PocketViewerApp(dataset, pocket);
            viewer.setEmbedded(true);
            AppSettings settings = new AppSettings(true);
            settings.setWidth(900);
            settings.setHeight(700);
            settings.setFrameRate(60);
            settings.setSamples(0);
            settings.setAudioRenderer(null);
            viewer.setSettings(settings);
            viewer.setShowSettings(false);
            viewer.createCanvas();

            JmeCanvasContext context =
                    (JmeCanvasContext) viewer.getContext();
            context.setSystemListener(viewer);
            Canvas canvas = context.getCanvas();
            canvas.setPreferredSize(new Dimension(900, 700));

            PocketDesktopFrame frame = new PocketDesktopFrame(
                    viewer, dataset, pocket, canvas);
            frame.setVisible(true);
            frame.toFront();
            frame.requestFocus();
            Thread renderThread = new Thread(
                    viewer::startCanvas,
                    "pocket-viewer-render");
            renderThread.start();
            canvas.requestFocusInWindow();
        });
    }

    private JMenuBar createMenuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu file = menu("File");
        JMenuItem open = new JMenuItem("Open Project Folder…");
        open.addActionListener(event -> openProject());
        JMenuItem close = new JMenuItem("Close Window");
        close.addActionListener(event -> dispose());
        file.add(open);
        file.addSeparator();
        file.add(close);
        bar.add(file);
        bar.add(menu("Edit"));
        bar.add(createViewMenu());
        bar.add(createSelectionMenu());
        bar.add(menu("Analysis"));
        bar.add(menu("Window"));
        bar.add(menu("Help"));
        return bar;
    }

    private JToolBar createToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        JButton open = new JButton("Open");
        open.addActionListener(event -> openProject());
        toolbar.add(open);
        toolbar.addSeparator();
        addLayerToggle(toolbar, "Backbone", "context", true);
        addLayerToggle(toolbar, "Surface", "surface", true);
        addLayerToggle(toolbar, "Pocket residues", "residues", true);
        addLayerToggle(toolbar, "All residues", "atoms", false);
        addLayerToggle(toolbar, "Alpha spheres", "spheres", false);
        addLayerToggle(toolbar, "Openings", "openings", false);
        JCheckBox pocketLabels =
                new JCheckBox("Pocket labels", false);
        pocketLabels.setModel(pocketLabelsModel);
        pocketLabels.addActionListener(event ->
                viewer.setPocketResidueLabelsVisible(
                        pocketLabels.isSelected()));
        toolbar.add(pocketLabels);
        toolbar.addSeparator();
        toolbar.add(new JLabel("Opacity "));
        JSlider opacity = new JSlider(8, 85, 34);
        opacity.setPreferredSize(new Dimension(120, 24));
        opacity.addChangeListener(event ->
                viewer.setSurfaceOpacity(opacity.getValue() / 100.0f));
        toolbar.add(opacity);
        JButton color = new JButton("Color…");
        color.addActionListener(event -> chooseSurfaceColor());
        toolbar.add(color);
        return toolbar;
    }

    private JPanel createWorkspace(Component canvas) {
        JSplitPane centerAndRight = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                createViewport(canvas),
                createDataBrowser());
        centerAndRight.setResizeWeight(0.78);
        centerAndRight.setDividerLocation(960);

        JSplitPane workspace = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(projectTree),
                centerAndRight);
        workspace.setResizeWeight(0.16);
        workspace.setDividerLocation(220);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(workspace);
        return panel;
    }

    private JPanel createViewport(Component canvas) {
        JPanel viewport = new JPanel(new BorderLayout());
        viewport.setBorder(BorderFactory.createTitledBorder("3D Viewport"));
        viewport.add(canvas, BorderLayout.CENTER);
        inspector.setEditable(false);
        inspector.setLineWrap(true);
        inspector.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        viewport.add(new JScrollPane(inspector), BorderLayout.SOUTH);
        return viewport;
    }

    private JTabbedPane createDataBrowser() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Pockets", new JScrollPane(pocketTable));
        tabs.addTab("Residues", createResidueBrowser());
        pocketTable.setSelectionMode(
                javax.swing.ListSelectionModel.SINGLE_SELECTION);
        residueTable.setSelectionMode(
                javax.swing.ListSelectionModel.SINGLE_SELECTION);
        residueTable.setRowSorter(residueSorter);
        pocketTable.getSelectionModel().addListSelectionListener(event -> {
            if (!refreshingTables && !event.getValueIsAdjusting()) {
                selectPocket(pocketTable.getSelectedRow());
            }
        });
        residueTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                int viewRow = residueTable.getSelectedRow();
                focusResidue(viewRow < 0
                        ? -1
                        : residueTable.convertRowIndexToModel(viewRow));
            }
        });
        residueTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    int viewRow = residueTable.getSelectedRow();
                    addPocketResidueToSelection(viewRow < 0
                            ? -1
                            : residueTable.convertRowIndexToModel(viewRow));
                }
            }
        });
        tabs.setPreferredSize(new Dimension(360, 600));
        tabs.addTab("Selection", createSelectionBrowser());
        return tabs;
    }

    private JPanel createSelectionBrowser() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        selectionSearch.putClientProperty(
                "JTextField.placeholderText",
                "Add residue (for example A:CYS202)");
        selectionSearch.addActionListener(event ->
                addSearchedResidue());
        JButton add = new JButton("Add");
        add.addActionListener(event -> addSearchedResidue());
        JPanel search = new JPanel(new BorderLayout(6, 0));
        search.setBorder(BorderFactory.createEmptyBorder(6, 6, 0, 6));
        search.add(selectionSearch, BorderLayout.CENTER);
        search.add(add, BorderLayout.EAST);
        panel.add(search, BorderLayout.NORTH);
        panel.add(new JScrollPane(selectionTable), BorderLayout.CENTER);

        JButton remove = new JButton("Remove");
        remove.addActionListener(event -> removeSelectedResidue());
        JButton clear = new JButton("Clear");
        clear.addActionListener(event -> {
            selectedResidues.clear();
            refreshSelection();
        });
        distanceMode.addActionListener(event -> updateDistances());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING));
        actions.add(remove);
        actions.add(clear);

        JPanel atomColors = new JPanel(
                new FlowLayout(FlowLayout.LEADING));
        atomColors.setBorder(BorderFactory.createTitledBorder(
                "Atom color override"));
        atomColors.add(new JLabel("Atom name"));
        atomColors.add(atomColorName);
        JButton atomColor = new JButton("Color…");
        atomColor.addActionListener(event -> chooseSelectedAtomColor());
        atomColors.add(atomColor);
        JButton resetAtomColors = new JButton("Reset colors");
        resetAtomColors.addActionListener(event ->
                viewer.clearSelectedAtomColors());
        atomColors.add(resetAtomColors);

        JPanel distances = new JPanel(
                new FlowLayout(FlowLayout.LEADING));
        distances.add(new JLabel("Distance"));
        distances.add(distanceMode);
        distanceSummary.setEditable(false);
        distanceSummary.setLineWrap(true);
        JPanel bottom = new JPanel(new BorderLayout());
        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(
                controls, BoxLayout.Y_AXIS));
        controls.add(atomColors);
        controls.add(actions);
        controls.add(distances);
        bottom.add(controls, BorderLayout.NORTH);
        bottom.add(new JScrollPane(distanceSummary), BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private void addSearchedResidue() {
        String query = selectionSearch.getText().strip();
        if (query.isEmpty()) {
            return;
        }
        String needle = query.replaceAll("[\\s:_-]", "")
                .toUpperCase();
        for (LocatedResidue residue : allResidues()) {
            String value = (
                    residue.getChain() + residue.getName()
                            + residue.getNumber())
                    .replaceAll("[\\s:_-]", "")
                    .toUpperCase();
            if (value.contains(needle)) {
                addSelectedResidue(residue);
                selectionSearch.setText("");
                return;
            }
        }
        status.setText("No structure residue matches “" + query + "”");
    }

    private void addPocketResidueToSelection(int row) {
        if (row >= 0 && row < visiblePocketResidues.size()) {
            addSelectedResidue(visiblePocketResidues.get(row));
        }
    }

    private void addSelectedResidue(LocatedResidue residue) {
        if (selectedResidues.stream().noneMatch(candidate ->
                residueKey(candidate).equals(residueKey(residue)))) {
            selectedResidues.add(residue);
            refreshSelection();
        }
        viewer.focusOnResidue(residue);
    }

    private void removeSelectedResidue() {
        int row = selectionTable.getSelectedRow();
        if (row >= 0 && row < selectedResidues.size()) {
            selectedResidues.remove(row);
            refreshSelection();
        }
    }

    private void refreshSelection() {
        selectionModel.setRowCount(0);
        for (LocatedResidue residue : selectedResidues) {
            selectionModel.addRow(new Object[]{
                    residue.getChain(),
                    residue.getName(),
                    residue.getNumber()
            });
        }
        viewer.setSelectedResidues(selectedResidues);
        updateDistances();
    }

    private void updateDistances() {
        StringBuilder text = new StringBuilder();
        for (int first = 0; first < selectedResidues.size(); first++) {
            for (int second = first + 1;
                    second < selectedResidues.size();
                    second++) {
                LocatedResidue left = selectedResidues.get(first);
                LocatedResidue right = selectedResidues.get(second);
                try {
                    double distance = switch (
                            distanceMode.getSelectedIndex()) {
                        case 1 -> PocketGeometry.calculateDistance(
                                left.residue(), "CA",
                                right.residue(), "CA");
                        case 2 -> PocketGeometry.calculateDistance(
                                left.residue(), "SG",
                                right.residue(), "SG");
                        default -> PocketGeometry.calculateDistance(
                                left.residue(), right.residue());
                    };
                    text.append(residueKey(left))
                            .append(" ↔ ")
                            .append(residueKey(right))
                            .append(": ")
                            .append("%.2f Å".formatted(distance))
                            .append('\n');
                } catch (IllegalArgumentException exception) {
                    text.append(residueKey(left))
                            .append(" ↔ ")
                            .append(residueKey(right))
                            .append(": unavailable\n");
                }
            }
        }
        distanceSummary.setText(text.toString());
    }

    private void chooseSelectedAtomColor() {
        String atomName = atomColorName.getText().strip();
        if (atomName.isEmpty()) {
            status.setText("Enter an atom name such as SG or CA");
            return;
        }
        java.awt.Color selected = JColorChooser.showDialog(
                this,
                "Color selected-residue atom " + atomName.toUpperCase(),
                java.awt.Color.YELLOW);
        if (selected == null) {
            return;
        }
        viewer.setSelectedAtomColor(
                atomName,
                new ColorRGBA(
                        selected.getRed() / 255.0f,
                        selected.getGreen() / 255.0f,
                        selected.getBlue() / 255.0f,
                        1.0f));
        status.setText(
                atomName.toUpperCase()
                        + " color applied to selected residues");
    }

    private JPanel createResidueBrowser() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        residueSearch.putClientProperty(
                "JTextField.placeholderText",
                "Search residue (for example A:ARG42)");
        residueSearch.addActionListener(event -> findResidue());
        residueSearch.getDocument().addDocumentListener(
                new DocumentListener() {
                    @Override
                    public void insertUpdate(DocumentEvent event) {
                        filterResidues();
                    }

                    @Override
                    public void removeUpdate(DocumentEvent event) {
                        filterResidues();
                    }

                    @Override
                    public void changedUpdate(DocumentEvent event) {
                        filterResidues();
                    }
                });
        JButton find = new JButton("Find");
        find.addActionListener(event -> findResidue());
        JPanel search = new JPanel(new BorderLayout(6, 0));
        search.setBorder(BorderFactory.createEmptyBorder(6, 6, 0, 6));
        search.add(residueSearch, BorderLayout.CENTER);
        search.add(find, BorderLayout.EAST);
        panel.add(search, BorderLayout.NORTH);
        panel.add(new JScrollPane(residueTable), BorderLayout.CENTER);

        JSlider labelSize = new JSlider(8, 36, 16);
        labelSize.addChangeListener(event ->
                viewer.setResidueLabelSize(labelSize.getValue()));
        JPanel labels = new JPanel(new BorderLayout(6, 0));
        labels.setBorder(BorderFactory.createEmptyBorder(0, 6, 6, 6));
        labels.add(new JLabel("Label size"), BorderLayout.WEST);
        labels.add(labelSize, BorderLayout.CENTER);
        panel.add(labels, BorderLayout.SOUTH);
        return panel;
    }

    private JMenu createSelectionMenu() {
        JMenu selection = menu("Selection");
        JMenuItem focus = new JMenuItem("Focus Selected Residue");
        focus.addActionListener(event -> {
            int viewRow = residueTable.getSelectedRow();
            focusResidue(viewRow < 0
                    ? -1
                    : residueTable.convertRowIndexToModel(viewRow));
        });
        selection.add(focus);
        return selection;
    }

    private JMenu createViewMenu() {
        JMenu view = menu("View");
        JCheckBoxMenuItem pocketLabels =
                new JCheckBoxMenuItem("Pocket Residue Labels");
        pocketLabels.setModel(pocketLabelsModel);
        pocketLabels.addActionListener(event ->
                viewer.setPocketResidueLabelsVisible(
                        pocketLabels.isSelected()));
        JCheckBoxMenuItem proteinLabels =
                new JCheckBoxMenuItem("Protein Residue Labels");
        proteinLabels.setModel(proteinLabelsModel);
        proteinLabels.addActionListener(event ->
                viewer.setProteinResidueLabelsVisible(
                        proteinLabels.isSelected()));
        view.add(pocketLabels);
        view.add(proteinLabels);
        return view;
    }

    private void findResidue() {
        String query = residueSearch.getText().strip();
        if (query.isEmpty() || pocket == null) {
            return;
        }
        String needle = query.replaceAll("[\\s:_-]", "")
                .toUpperCase();
        for (int modelRow = 0;
                modelRow < residueModel.getRowCount();
                modelRow++) {
            String value = (
                    residueModel.getValueAt(modelRow, 0).toString()
                    + residueModel.getValueAt(modelRow, 1)
                    + residueModel.getValueAt(modelRow, 2))
                    .replaceAll("[\\s:_-]", "")
                    .toUpperCase();
            if (value.contains(needle)) {
                int viewRow = residueTable.convertRowIndexToView(modelRow);
                residueTable.setRowSelectionInterval(viewRow, viewRow);
                residueTable.scrollRectToVisible(
                        residueTable.getCellRect(viewRow, 0, true));
                focusResidue(modelRow);
                status.setText("Focused " + value);
                return;
            }
        }
        status.setText("No pocket residue matches “" + query + "”");
    }

    private void filterResidues() {
        String query = residueSearch.getText().strip()
                .replaceAll("[\\s:_-]", "")
                .toUpperCase();
        if (query.isEmpty()) {
            residueSorter.setRowFilter(null);
            return;
        }
        residueSorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(
                    Entry<? extends DefaultTableModel, ? extends Integer>
                            entry) {
                String chain = entry.getStringValue(0).toUpperCase();
                String residue = entry.getStringValue(1).toUpperCase();
                String number = entry.getStringValue(2).toUpperCase();
                return (residue + number).startsWith(query)
                        || (chain + residue + number).startsWith(query);
            }
        });
    }

    private JPanel createStatusBar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        panel.add(status);
        JLabel help = new JLabel(
                "Drag to orbit  •  wheel to zoom  •  click atom to inspect",
                SwingConstants.RIGHT);
        panel.add(help, BorderLayout.EAST);
        return panel;
    }

    private void openProject() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open pocket-results folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            PocketDataset loaded = new PocketDatasetLoader().load(
                    chooser.getSelectedFile().toPath());
            protein = loaded.protein();
            pockets = loaded.pockets();
            pocket = PocketRanking.preferredPocket(pockets);
            selectedResidues.clear();
            refreshSelection();
            refreshDataset();
            if (pocket == null) {
                status.setText(pockets.size()
                        + " pockets loaded — none can be displayed");
            } else {
                viewer.loadDataset(loaded, pocket);
                status.setText(pocketStatus());
            }
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(
                    this,
                    exception.getMessage(),
                    "Could not open project",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void selectPocket(int row) {
        if (row < 0 || row >= pockets.size()) {
            return;
        }
        pocket = pockets.get(row);
        visiblePocketResidues = liningResidues(protein, pocket);
        viewer.loadDataset(new PocketDataset(protein, pockets), pocket);
        refreshResidues();
        status.setText(pocketStatus());
    }

    private void focusResidue(int row) {
        if (pocket == null || row < 0
                || row >= visiblePocketResidues.size()) {
            return;
        }
        viewer.focusOnResidue(visiblePocketResidues.get(row));
    }

    private void refreshDataset() {
        refreshingTables = true;
        DefaultMutableTreeNode root =
                new DefaultMutableTreeNode(protein.id());
        root.add(new DefaultMutableTreeNode(
                "Structure — "
                        + allResidues().size()
                        + " residues"));
        root.add(new DefaultMutableTreeNode(
                "Pockets — " + pockets.size()));
        projectTree.setModel(
                new javax.swing.tree.DefaultTreeModel(root));
        pocketModel.setRowCount(0);
        for (Pocket candidate : pockets) {
            pocketModel.addRow(new Object[]{
                    candidate.name(),
                    candidate.source(),
                    PocketRanking.rankingScore(candidate),
                    PocketRanking.druggabilityScore(candidate),
                    liningResidues(protein, candidate).size()
            });
        }
        refreshResidues();
        int selectedRow = pockets.indexOf(pocket);
        if (selectedRow >= 0) {
            pocketTable.setRowSelectionInterval(
                    selectedRow, selectedRow);
            pocketTable.scrollRectToVisible(
                    pocketTable.getCellRect(selectedRow, 0, true));
        } else {
            pocketTable.clearSelection();
        }
        refreshingTables = false;
    }

    private void refreshResidues() {
        residueModel.setRowCount(0);
        if (pocket == null) {
            visiblePocketResidues = List.of();
            return;
        }
        visiblePocketResidues = liningResidues(protein, pocket);
        for (LocatedResidue residue : visiblePocketResidues) {
            residueModel.addRow(new Object[]{
                    residue.getChain(),
                    residue.getName(),
                    residue.getNumber(),
                    residue.getAtomCount()
            });
        }
    }

    private String pocketStatus() {
        int unresolved = PocketGeometry.geometry(
                protein.structure(), pocket)
                .unresolvedResidues().size();
        return pocket.name() + " — "
                + visiblePocketResidues.size() + " lining residues"
                + (unresolved == 0
                ? ""
                : " — warning: " + unresolved
                        + " unresolved residue references");
    }

    private List<LocatedResidue> liningResidues(
            Protein protein,
            Pocket pocket) {
        return locate(residueSelection.liningResidues(
                protein.structure(), pocket, 4.0));
    }

    private static String residueKey(LocatedResidue residue) {
        return residue.getChain() + ":" + residue.getNumber();
    }

    private List<LocatedResidue> allResidues() {
        List<LocatedResidue> residues = new ArrayList<>();
        protein.structure().getChains().forEach(chain ->
                chain.residues().forEach(residue -> residues.add(
                        new LocatedResidue(
                                new ResidueId(
                                        chain.id(),
                                        residue.getNumber(),
                                        residue.getInsertionCode()),
                                residue))));
        return List.copyOf(residues);
    }

    private List<LocatedResidue> locate(List<Residue> residues) {
        java.util.Set<Residue> selected =
                java.util.Collections.newSetFromMap(
                        new java.util.IdentityHashMap<>());
        selected.addAll(residues);
        return allResidues().stream()
                .filter(located -> selected.contains(located.residue()))
                .toList();
    }

    private void addLayerToggle(
            JToolBar toolbar,
            String text,
            String layer,
            boolean selected) {
        JCheckBox toggle = new JCheckBox(text, selected);
        toggle.addActionListener(event ->
                viewer.setLayerVisible(layer, toggle.isSelected()));
        toolbar.add(toggle);
    }

    private void chooseSurfaceColor() {
        java.awt.Color selected = JColorChooser.showDialog(
                this,
                "Pocket surface color",
                new java.awt.Color(38, 115, 242));
        if (selected != null) {
            viewer.setSurfaceColor(new ColorRGBA(
                    selected.getRed() / 255.0f,
                    selected.getGreen() / 255.0f,
                    selected.getBlue() / 255.0f,
                    1.0f));
        }
    }

    private static JMenu menu(String name) {
        return new JMenu(name);
    }

    private static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }
}
