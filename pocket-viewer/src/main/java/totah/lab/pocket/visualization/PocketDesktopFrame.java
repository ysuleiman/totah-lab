package totah.lab.pocket.visualization;

import com.formdev.flatlaf.FlatDarkLaf;
import com.jme3.math.ColorRGBA;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeCanvasContext;
import totah.lab.io.ProteinIO;
import totah.lab.pocket.Pocket;
import totah.lab.pocket.geometry.PocketGeometry;
import totah.lab.protein.Protein;
import totah.lab.protein.Residue;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JColorChooser;
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
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

final class PocketDesktopFrame extends JFrame {
    private final PocketViewerApp viewer;
    private Protein protein;
    private Pocket pocket;
    private List<Residue> visiblePocketResidues = List.of();
    private final DefaultTableModel pocketModel =
            readOnlyModel(
                    "Name", "Source", "Score",
                    "Druggability", "Residues");
    private final DefaultTableModel residueModel =
            readOnlyModel("Chain", "Residue", "Number", "Atoms");
    private final JTable pocketTable = new JTable(pocketModel);
    private final JTable residueTable = new JTable(residueModel);
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
            Protein protein,
            Pocket pocket,
            Component canvas) {
        super("Pocket Viewer — " + protein.getTargetId());
        this.viewer = viewer;
        this.protein = protein;
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

    static void open(Protein protein, Pocket pocket) {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty(
                "com.apple.macos.application.appearance",
                "system");
        SwingUtilities.invokeLater(() -> {
            FlatDarkLaf.setup();

            PocketViewerApp viewer =
                    new PocketViewerApp(protein, pocket);
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
                    viewer, protein, pocket, canvas);
            frame.setVisible(true);
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
        pocketTable.getSelectionModel().addListSelectionListener(event -> {
            if (!refreshingTables && !event.getValueIsAdjusting()) {
                selectPocket(pocketTable.getSelectedRow());
            }
        });
        residueTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                focusResidue(residueTable.getSelectedRow());
            }
        });
        tabs.setPreferredSize(new Dimension(360, 600));
        return tabs;
    }

    private JPanel createResidueBrowser() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        residueSearch.putClientProperty(
                "JTextField.placeholderText",
                "Search residue (for example A:ARG42)");
        residueSearch.addActionListener(event -> findResidue());
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
        focus.addActionListener(event ->
                focusResidue(residueTable.getSelectedRow()));
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
            Protein loaded = ProteinIO.load(
                    chooser.getSelectedFile().toPath());
            protein = loaded;
            pocket = PocketRanking.preferredPocket(loaded);
            refreshDataset();
            if (pocket == null) {
                status.setText(loaded.getPocketCount()
                        + " pockets loaded — none can be displayed");
            } else {
                viewer.loadDataset(protein, pocket);
                status.setText(pocket.getName() + " — "
                        + visiblePocketResidues.size()
                        + " lining residues");
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
        if (row < 0 || row >= protein.getPockets().size()) {
            return;
        }
        pocket = protein.getPockets().get(row);
        visiblePocketResidues = liningResidues(protein, pocket);
        viewer.loadDataset(protein, pocket);
        refreshResidues();
        status.setText(
                pocket.getName() + " — "
                        + visiblePocketResidues.size()
                        + " lining residues");
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
                new DefaultMutableTreeNode(protein.getTargetId());
        root.add(new DefaultMutableTreeNode(
                "Structure — "
                        + protein.getStructure().getResidues().size()
                        + " residues"));
        root.add(new DefaultMutableTreeNode(
                "Pockets — " + protein.getPocketCount()));
        projectTree.setModel(
                new javax.swing.tree.DefaultTreeModel(root));
        pocketModel.setRowCount(0);
        for (Pocket candidate : protein.getPockets()) {
            pocketModel.addRow(new Object[]{
                    candidate.getName(),
                    candidate.getSource(),
                    candidate.getScore(),
                    PocketRanking.druggabilityScore(candidate),
                    liningResidues(protein, candidate).size()
            });
        }
        refreshResidues();
        int selectedRow = protein.getPockets().indexOf(pocket);
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
        for (Residue residue : visiblePocketResidues) {
            residueModel.addRow(new Object[]{
                    residue.getChain(),
                    residue.getName(),
                    residue.getNumber(),
                    residue.getAtomCount()
            });
        }
    }

    private static List<Residue> liningResidues(
            Protein protein,
            Pocket pocket) {
        Map<String, Residue> residues = new LinkedHashMap<>();
        for (Residue residue : pocket.getResidues()) {
            residues.put(residueKey(residue), residue);
        }
        for (Residue residue : PocketGeometry.pocketNeighbors(
                protein.getStructure(), pocket, 4.0)) {
            residues.putIfAbsent(residueKey(residue), residue);
        }
        return List.copyOf(residues.values());
    }

    private static String residueKey(Residue residue) {
        return residue.getChain() + ":" + residue.getNumber();
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
