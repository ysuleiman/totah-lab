package totah.lab.pocket.visualization;

import com.jme3.app.SimpleApplication;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Line;
import com.jme3.scene.shape.Sphere;
import com.jme3.scene.shape.Torus;
import com.jme3.system.AppSettings;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Checkbox;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.DefaultRangedValueModel;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.Slider;
import com.simsilica.lemur.TextField;
import com.simsilica.lemur.core.VersionedReference;
import com.simsilica.lemur.style.BaseStyles;
import totah.lab.athena.pocket.geometry.PocketGeometry;
import totah.lab.athena.pocket.geometry.PocketGeometryBasis;
import totah.lab.athena.pocket.geometry.PocketGeometryResult;
import totah.lab.athena.pocket.selection.PocketResidueSelection;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.pocket.visualization.control.NativeFolderPicker;
import totah.lab.pocket.visualization.control.ResidueLabelController;
import totah.lab.pocket.visualization.control.ResidueFocusController;
import totah.lab.pocket.visualization.control.ScientificOrbitController;
import totah.lab.pocket.visualization.analysis.PocketOpening;
import totah.lab.pocket.visualization.analysis.PocketOpeningDetector;
import totah.lab.pocket.visualization.scene.PocketMeshBuilder;
import totah.lab.pocket.visualization.scene.PocketScene;
import totah.lab.pocket.visualization.surface.MarchingCubes;
import totah.lab.pocket.visualization.surface.PocketField;
import totah.lab.pocket.visualization.surface.PocketFieldBuilder;
import totah.lab.pocket.visualization.surface.TriangleMesh;

import java.util.ArrayList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class PocketViewerApp extends SimpleApplication {
    private static final String SELECT_MAPPING = "select-pocket-object";
    private static final int SPHERE_Z_SAMPLES = 12;
    private static final int SPHERE_RADIAL_SAMPLES = 18;
    private static final int RESIDUES_PER_PAGE = 6;
    private static final ColorRGBA POCKET_COLOR =
            new ColorRGBA(0.15f, 0.45f, 0.95f, 0.34f);
    private static final List<ColorRGBA> SELECTION_COLORS = List.of(
            new ColorRGBA(0.1f, 0.8f, 1.0f, 1.0f),
            new ColorRGBA(1.0f, 0.35f, 0.7f, 1.0f),
            new ColorRGBA(1.0f, 0.65f, 0.12f, 1.0f),
            new ColorRGBA(0.25f, 0.9f, 0.45f, 1.0f),
            new ColorRGBA(0.72f, 0.5f, 1.0f, 1.0f));

    private Protein protein;
    private Pocket pocket;
    private List<Pocket> pockets;
    private PocketGeometryResult pocketGeometry;
    private final PocketResidueSelection residueSelection =
            new PocketResidueSelection();
    private final PocketScene scene = new PocketScene();
    private Geometry selectedGeometry;
    private Vector2f selectionPressPosition;
    private Geometry pocketSurface;
    private ColorRGBA pocketColor = POCKET_COLOR.clone();
    private float surfaceOpacity = POCKET_COLOR.a;
    private Label selectionLabel;
    private VersionedReference<Double> opacityReference;
    private VersionedReference<Double> labelSizeReference;
    private VersionedReference<Double> residueListReference;
    private ResidueLabelController pocketResidueLabels;
    private ResidueLabelController proteinResidueLabels;
    private ResidueLabelController selectedResidueLabels;
    private List<LocatedResidue> selectedResidues = List.of();
    private final Map<String, ColorRGBA> selectedAtomColors =
            new java.util.HashMap<>();
    private boolean pocketResidueLabelsVisible;
    private boolean proteinResidueLabelsVisible;
    private float residueLabelSize = 16.0f;
    private Container residuePanel;
    private int residueListStart = -1;
    private Geometry cameraTarget;
    private ResidueFocusController residueFocus;
    private ScientificOrbitController orbitController;
    private Node controlsPanel;
    private Container pocketBrowserPanel;
    private Protein browsableProtein;
    private List<Pocket> browsablePockets = List.of();
    private int pocketBrowserPage;
    private boolean guiInitialized;
    private boolean embedded;
    private Consumer<String> selectionListener = ignored -> {
    };

    public PocketViewerApp(Protein protein, Pocket pocket) {
        this(new PocketDataset(protein, List.of(pocket)), pocket);
    }

    PocketViewerApp(PocketDataset dataset, Pocket pocket) {
        Objects.requireNonNull(dataset, "dataset");
        this.protein = dataset.protein();
        this.pockets = dataset.pockets();
        this.pocket = Objects.requireNonNull(pocket, "pocket");
        this.pocketGeometry = PocketGeometry.geometry(
                this.protein.structure(), this.pocket);
    }

    public void startViewer() {
        PocketDesktopFrame.open(new PocketDataset(protein, pockets), pocket);
    }

    @Override
    public void simpleInitApp() {
        setDisplayFps(false);
        setDisplayStatView(false);
        viewPort.setBackgroundColor(new ColorRGBA(
                0.025f, 0.035f, 0.055f, 1.0f));
        flyCam.setEnabled(false);

        rootNode.attachChild(scene.root());

        buildProteinContext();
        buildPocketSurface();
        buildLiningResidues();
        buildPocketCenter();
        buildPocketOpenings();
        buildDebugSpheres();
        residueFocus = new ResidueFocusController(
                scene.root(), assetManager);
        PocketScene.setVisible(scene.fullProteinAtoms(), false);
        PocketScene.setVisible(scene.debugAlphaSpheres(), false);
        PocketScene.setVisible(scene.pocketOpenings(), false);
        addLighting();
        configureCamera();
        configureSelection();
        if (embedded) {
            createResidueLabels();
        } else {
            configureControls();
            configureFileMenu();
            browsableProtein = protein;
            browsablePockets = pockets;
            configurePocketBrowser();
            createResidueLabels();
        }
    }

    private void createResidueLabels() {
        pocketResidueLabels = new ResidueLabelController(
                cam,
                guiNode,
                guiFont,
                liningResidues(),
                pocketGeometry.centroid());
        proteinResidueLabels = new ResidueLabelController(
                cam,
                guiNode,
                guiFont,
                nonPocketResidues(),
                pocketGeometry.centroid());
        pocketResidueLabels.setSize(residueLabelSize);
        proteinResidueLabels.setSize(residueLabelSize);
        pocketResidueLabels.setEnabled(pocketResidueLabelsVisible);
        proteinResidueLabels.setEnabled(proteinResidueLabelsVisible);
    }

    @Override
    public void simpleUpdate(float timePerFrame) {
        if (opacityReference != null && opacityReference.update()) {
            setPocketOpacity(opacityReference.get().floatValue());
        }
        if (labelSizeReference != null && labelSizeReference.update()) {
            float size = labelSizeReference.get().floatValue();
            pocketResidueLabels.setSize(size);
            proteinResidueLabels.setSize(size);
        }
        if (residueListReference != null && residueListReference.update()) {
            int start = (int) Math.round(residueListReference.get());
            if (start != residueListStart) {
                residueListStart = start;
                populateResidueControls();
            }
        }
        if (pocketResidueLabels != null) {
            pocketResidueLabels.update();
            proteinResidueLabels.update();
        }
        if (selectedResidueLabels != null) {
            selectedResidueLabels.update();
        }
        if (residueFocus != null) {
            residueFocus.update(timePerFrame);
        }
        if (orbitController != null) {
            orbitController.update();
        }
    }

    private void buildPocketSurface() {
        if (pocketGeometry.basis()
                != PocketGeometryBasis.ALPHA_SPHERES) {
            buildResidueDerivedSurface();
            return;
        }
        List<AlphaSphere> spheres = pocket.alphaSphereSet()
                .orElseThrow()
                .spheres();
        PocketField field = PocketFieldBuilder.fromAlphaSpheres(
                spheres, 0.4);
        TriangleMesh surface = MarchingCubes.extract(field, 0.0);
        pocketSurface = PocketMeshBuilder.createGeometry(
                surface, pocketGeometry.centroid(),
                assetManager, POCKET_COLOR);
        scene.pocketSurface().attachChild(pocketSurface);
    }

    private void buildResidueDerivedSurface() {
        Geometry envelope = createSphere(
                "Residue-derived pocket envelope",
                Vector3f.ZERO,
                1.0f,
                POCKET_COLOR,
                true);
        envelope.setLocalScale(
                (float) Math.max(0.1, pocketGeometry.bounds().width() / 2.0),
                (float) Math.max(0.1, pocketGeometry.bounds().height() / 2.0),
                (float) Math.max(0.1, pocketGeometry.bounds().depth() / 2.0));
        pocketSurface = envelope;
        scene.pocketSurface().attachChild(envelope);
    }

    private void buildProteinContext() {
        List<LocatedResidue> residues = allResidues();
        LocatedResidue previous = null;
        for (LocatedResidue residue : residues) {
            if (previous != null
                    && Objects.equals(previous.getChain(), residue.getChain())) {
                Point3D start = previous.getAlphaCarbonPosition();
                Point3D end = residue.getAlphaCarbonPosition();
                if (start != null && end != null && distance(start, end) < 5.0) {
                    Geometry trace = new Geometry(
                            "Backbone " + residueLabel(residue),
                            new Line(relative(start), relative(end)));
                    trace.setMaterial(unshaded(
                            new ColorRGBA(0.62f, 0.65f, 0.7f, 0.3f),
                            true));
                    trace.setQueueBucket(RenderQueue.Bucket.Transparent);
                    scene.proteinContext().attachChild(trace);
                }
            }
            previous = residue;
        }
    }

    private void buildLiningResidues() {
        for (LocatedResidue residue : liningResidues()) {
            Node residueNode = new Node(residueLabel(residue));
            List<Atom> heavyAtoms = residue.getAtoms().stream()
                    .filter(Atom::isHeavyAtom)
                    .toList();
            for (Atom atom : heavyAtoms) {
                String label = atom.getName() + " " + residueLabel(residue);
                Geometry geometry = createSphere(
                        label,
                        relative(atom.getPosition()),
                        0.24f,
                        elementColor(atom.getElement()),
                        false);
                geometry.setUserData("selectionLabel", label);
                addAtomMetadata(geometry, atom, residue);
                residueNode.attachChild(geometry);
            }
            addInferredBonds(residueNode, heavyAtoms);
            scene.residues().put(residueLabel(residue), residueNode);
            scene.liningResidues().attachChild(residueNode);
        }
    }

    private void rebuildSelectedResidues() {
        scene.selectedResidues().detachAllChildren();
        if (selectedResidueLabels != null) {
            selectedResidueLabels.dispose();
        }
        for (int residueIndex = 0;
                residueIndex < selectedResidues.size();
                residueIndex++) {
            LocatedResidue residue = selectedResidues.get(residueIndex);
            ColorRGBA accent = SELECTION_COLORS.get(
                    residueIndex % SELECTION_COLORS.size());
            Node residueNode = new Node(
                    "Selected " + residueLabel(residue));
            List<Atom> heavyAtoms = residue.getAtoms().stream()
                    .filter(Atom::isHeavyAtom)
                    .toList();
            for (Atom atom : heavyAtoms) {
                String label = atom.getName() + " "
                        + residueLabel(residue);
                ColorRGBA override = selectedAtomColors.get(
                        atom.getName().toUpperCase());
                Geometry geometry = createSphere(
                        label,
                        relative(atom.getPosition()),
                        0.34f,
                        override != null
                                ? override
                                : atom.getElement() == Element.C
                                ? accent
                                : elementColor(atom.getElement()),
                        false);
                geometry.setUserData("selectionLabel", label);
                addAtomMetadata(geometry, atom, residue);
                residueNode.attachChild(geometry);
            }
            addInferredBonds(residueNode, heavyAtoms, accent);
            scene.selectedResidues().attachChild(residueNode);
        }
        selectedResidueLabels = new ResidueLabelController(
                cam,
                guiNode,
                guiFont,
                selectedResidues,
                pocketGeometry.centroid());
        selectedResidueLabels.setSize(residueLabelSize);
        selectedResidueLabels.setColors(List.of(ColorRGBA.Black));
        selectedResidueLabels.setEnabled(true);
    }

    private void addInferredBonds(Node parent, List<Atom> atoms) {
        addInferredBonds(
                parent,
                atoms,
                new ColorRGBA(0.68f, 0.7f, 0.74f, 1.0f));
    }

    private void addInferredBonds(
            Node parent,
            List<Atom> atoms,
            ColorRGBA color) {
        for (int i = 0; i < atoms.size(); i++) {
            for (int j = i + 1; j < atoms.size(); j++) {
                Atom first = atoms.get(i);
                Atom second = atoms.get(j);
                double maximum = 1.25 * (
                        covalentRadius(first.getElement())
                                + covalentRadius(second.getElement()));
                double atomDistance = distance(
                        first.getPosition(), second.getPosition());
                if (atomDistance > 0.1 && atomDistance <= maximum) {
                    parent.attachChild(createBond(
                            relative(first.getPosition()),
                            relative(second.getPosition()),
                            color));
                }
            }
        }
    }

    private Geometry createBond(Vector3f start, Vector3f end) {
        return createBond(
                start,
                end,
                new ColorRGBA(0.68f, 0.7f, 0.74f, 1.0f));
    }

    private Geometry createBond(
            Vector3f start,
            Vector3f end,
            ColorRGBA color) {
        Vector3f direction = end.subtract(start);
        Geometry bond = new Geometry(
                "Residue bond",
                new Cylinder(8, 12, 0.10f, direction.length(), true));
        bond.setLocalTranslation(start.add(end).multLocal(0.5f));
        bond.setLocalRotation(new Quaternion().lookAt(
                direction.normalize(), Vector3f.UNIT_Y));
        bond.setMaterial(unshaded(color, false));
        return bond;
    }

    private void buildPocketCenter() {
        Geometry center = createSphere(
                "Pocket center",
                Vector3f.ZERO,
                0.55f,
                new ColorRGBA(1.0f, 0.55f, 0.05f, 1.0f),
                false);
        center.setUserData("selectionLabel", "Pocket center");
        scene.pocketCenter().attachChild(center);
    }

    private void buildDebugSpheres() {
        if (pocketGeometry.basis()
                != PocketGeometryBasis.ALPHA_SPHERES) {
            return;
        }
        for (AlphaSphere sphere : pocket.alphaSphereSet()
                .orElseThrow().spheres()) {
            String label = "Alpha sphere " + sphere.id();
            Geometry geometry = createSphere(
                    label,
                    relative(sphere.center()),
                    (float) Math.max(0.2, sphere.radius()),
                    new ColorRGBA(0.1f, 0.8f, 1.0f, 0.18f),
                    true);
            geometry.setUserData("selectionLabel", label);
            scene.debugAlphaSpheres().attachChild(geometry);
        }
    }

    private void buildPocketOpenings() {
        List<PocketOpening> openings = PocketOpeningDetector.detect(
                pocket, protein.structure(), 3);
        int index = 1;
        for (PocketOpening opening : openings) {
            float radius = (float) opening.radius();
            Geometry ring = new Geometry(
                    opening.kind() == PocketOpening.Kind.MOUTH
                            ? "Pocket mouth"
                            : "Secondary opening " + index,
                    new Torus(48, 12, 0.09f, radius));
            ColorRGBA color =
                    opening.kind() == PocketOpening.Kind.MOUTH
                            ? new ColorRGBA(1.0f, 0.55f, 0.12f, 1.0f)
                            : new ColorRGBA(0.2f, 0.9f, 0.65f, 1.0f);
            ring.setMaterial(unshaded(color, false));
            ring.setLocalTranslation(relative(opening.center()));
            Vector3f direction = new Vector3f(
                    (float) opening.direction().x(),
                    (float) opening.direction().y(),
                    (float) opening.direction().z());
            ring.setLocalRotation(
                    new Quaternion().lookAt(direction, Vector3f.UNIT_Y));
            ring.setUserData(
                    "selectionLabel",
                    "%s — radius %.2f Å, clearance %.2f Å".formatted(
                            opening.kind() == PocketOpening.Kind.MOUTH
                                    ? "Derived pocket mouth"
                                    : "Derived secondary opening " + index,
                            opening.radius(),
                            opening.clearance()));
            ring.setUserData("baseColor", color.clone());
            scene.pocketOpenings().attachChild(ring);
            index++;
        }
    }

    private void buildFullProteinAtoms() {
        if (scene.fullProteinAtoms().getQuantity() > 0) {
            return;
        }
        for (LocatedResidue residue : allResidues()) {
            for (Atom atom : residue.getAtoms()) {
                if (!atom.isHeavyAtom()) {
                    continue;
                }
                Geometry geometry = createSphere(
                        atom.getName() + " " + residueLabel(residue),
                        relative(atom.getPosition()),
                        0.18f,
                        elementColor(atom.getElement()).mult(0.65f),
                        false);
                geometry.setUserData(
                        "selectionLabel",
                        atom.getName() + " " + residueLabel(residue));
                addAtomMetadata(geometry, atom, residue);
                scene.fullProteinAtoms().attachChild(geometry);
            }
        }
    }

    private void configureControls() {
        if (!guiInitialized) {
            GuiGlobals.initialize(this);
            BaseStyles.loadGlassStyle();
            GuiGlobals.getInstance().getStyles().setDefaultStyle("glass");
            guiInitialized = true;
        }

        controlsPanel = new Node("dataset-controls");
        guiNode.attachChild(controlsPanel);

        Container display = new Container();
        display.addChild(new Label("DISPLAY"));
        display.addChild(new Label(
                pocket.name() + "  •  " + pocket.source()));
        addLayerCheckbox(
                display, "Protein backbone", scene.proteinContext(), true);
        addLayerCheckbox(
                display, "Pocket surface", scene.pocketSurface(), true);
        addLayerCheckbox(
                display, "Lining residues", scene.liningResidues(), true);
        addLayerCheckbox(
                display, "Pocket center", scene.pocketCenter(), true);

        Checkbox fullProtein = display.addChild(
                new Checkbox("All residues"));
        fullProtein.addClickCommands(source -> {
            if (fullProtein.isChecked()) {
                buildFullProteinAtoms();
            }
            PocketScene.setVisible(
                    scene.fullProteinAtoms(), fullProtein.isChecked());
        });

        addLayerCheckbox(display, "Alpha spheres (debug)",
                scene.debugAlphaSpheres(), false);
        display.addChild(new Label("Surface opacity"));
        DefaultRangedValueModel opacity =
                new DefaultRangedValueModel(0.08, 0.85, POCKET_COLOR.a);
        Slider opacitySlider = display.addChild(
                new Slider(opacity, Axis.X));
        opacitySlider.setPreferredSize(new Vector3f(220, 18, 0));
        opacityReference = opacity.createReference();
        display.addChild(new Label("Surface color"));
        Container colors = display.addChild(new Container());
        addColorButton(colors, "Blue",
                new ColorRGBA(0.15f, 0.45f, 0.95f, 1.0f), 0);
        addColorButton(colors, "Cyan",
                new ColorRGBA(0.05f, 0.8f, 0.85f, 1.0f), 1);
        addColorButton(colors, "Magenta",
                new ColorRGBA(0.8f, 0.2f, 0.75f, 1.0f), 2);
        display.setLocalTranslation(16, cam.getHeight() - 64, 1);
        controlsPanel.attachChild(display);

        Container residues = new Container();
        residues.addChild(new Label("RESIDUES"));
        Checkbox pocketLabels = residues.addChild(
                new Checkbox("Pocket residue labels"));
        pocketLabels.addClickCommands(source ->
                pocketResidueLabels.setEnabled(pocketLabels.isChecked()));
        Checkbox proteinLabels = residues.addChild(
                new Checkbox("Other protein residue labels"));
        proteinLabels.addClickCommands(source ->
                proteinResidueLabels.setEnabled(proteinLabels.isChecked()));
        residues.addChild(new Label("Label size"));
        DefaultRangedValueModel labelSize =
                new DefaultRangedValueModel(10.0, 32.0, 16.0);
        Slider labelSizeSlider = residues.addChild(
                new Slider(labelSize, Axis.X));
        labelSizeSlider.setPreferredSize(new Vector3f(370, 18, 0));
        labelSizeReference = labelSize.createReference();
        residues.addChild(new Label("Find residue"));
        Container searchRow = residues.addChild(new Container());
        TextField residueSearch =
                searchRow.addChild(new TextField(""), 0, 0);
        residueSearch.setSingleLine(true);
        residueSearch.setPreferredWidth(270.0f);
        Button find = searchRow.addChild(new Button("Find"), 1, 0);
        find.addClickCommands(source ->
                focusResidue(residueSearch.getText()));

        residues.addChild(new Label("Browse lining residues"));
        int maximumStart = Math.max(
                1, scene.residues().size() - RESIDUES_PER_PAGE);
        DefaultRangedValueModel residuePosition =
                new DefaultRangedValueModel(0, maximumStart, 0);
        Slider residueSlider = residues.addChild(
                new Slider(residuePosition, Axis.X));
        residueSlider.setDelta(1.0);
        residueSlider.setPreferredSize(new Vector3f(370, 18, 0));
        residueListReference = residuePosition.createReference();
        residuePanel = residues.addChild(new Container());
        residueListStart = 0;
        populateResidueControls();
        residues.setLocalTranslation(
                cam.getWidth() - 430,
                Math.max(320, cam.getHeight() - 440),
                1);
        controlsPanel.attachChild(residues);

        Container inspector = new Container();
        inspector.addChild(new Label("SELECTION"));
        selectionLabel = inspector.addChild(
                new Label("Selected: none"));
        inspector.addChild(new Label(
                "Click atom to inspect  •  drag to orbit  •  wheel to zoom"));
        inspector.setLocalTranslation(
                Math.max(280, cam.getWidth() * 0.28f),
                92,
                2);
        controlsPanel.attachChild(inspector);
    }

    private void configureFileMenu() {
        Container menu = new Container();
        Button file = menu.addChild(new Button("File"), 0, 0);
        menu.addChild(new Label("POCKET VIEWER 3D"), 1, 0);
        Container commands = menu.addChild(new Container(), 0, 1);
        commands.setCullHint(Spatial.CullHint.Always);
        Button open = commands.addChild(
                new Button("Open pocket-results folder…"));
        Button exit = commands.addChild(new Button("Exit"));
        file.addClickCommands(source ->
                commands.setCullHint(
                        commands.getCullHint() == Spatial.CullHint.Always
                                ? Spatial.CullHint.Inherit
                                : Spatial.CullHint.Always));
        open.addClickCommands(source -> {
            commands.setCullHint(Spatial.CullHint.Always);
            openPocketResults();
        });
        exit.addClickCommands(source -> stop());
        menu.setLocalTranslation(
                16,
                cam.getHeight() - 15,
                2);
        guiNode.attachChild(menu);
    }

    private void openPocketResults() {
        try {
            Path directory = NativeFolderPicker.pickFolder(null)
                    .orElse(null);
            if (directory == null) {
                selectionLabel.setText("Open cancelled");
                return;
            }
            selectionLabel.setText("Loading " + directory + "…");
            PocketDataset loaded = new PocketDatasetLoader().load(directory);
            if (loaded.pockets().isEmpty()) {
                selectionLabel.setText(
                        "No pockets found in " + directory);
                return;
            }
            showPocketList(loaded);
        } catch (IOException | RuntimeException exception) {
            selectionLabel.setText(
                    "Could not open folder: " + exception.getMessage());
        }
    }

    private void showPocketList(PocketDataset loaded) {
        browsableProtein = loaded.protein();
        browsablePockets = loaded.pockets();
        pocketBrowserPage = 0;
        rebuildPocketBrowser();
        selectionLabel.setText(
                loaded.pockets().size()
                        + " pockets loaded — select one on the right");
    }

    private void configurePocketBrowser() {
        pocketBrowserPanel = new Container();
        pocketBrowserPanel.setLocalTranslation(
                cam.getWidth() - 430,
                cam.getHeight() - 65,
                3);
        guiNode.attachChild(pocketBrowserPanel);
        rebuildPocketBrowser();
    }

    private void rebuildPocketBrowser() {
        if (pocketBrowserPanel == null || browsableProtein == null) {
            return;
        }
        pocketBrowserPanel.clearChildren();
        List<Pocket> pockets = browsablePockets;
        int pageSize = RESIDUES_PER_PAGE;
        int pages = Math.max(1, (pockets.size() + pageSize - 1) / pageSize);
        pocketBrowserPage = Math.min(pocketBrowserPage, pages - 1);

        pocketBrowserPanel.addChild(new Label(
                "Pockets — " + browsableProtein.id()));
        pocketBrowserPanel.addChild(new Label(
                "%d total  •  page %d/%d".formatted(
                        pockets.size(), pocketBrowserPage + 1, pages)));
        int start = pocketBrowserPage * pageSize;
        int end = Math.min(pockets.size(), start + pageSize);
        for (int index = start; index < end; index++) {
            Pocket candidate = pockets.get(index);
            boolean active = browsableProtein == protein
                    && candidate == pocket;
            double rankingScore = PocketRanking.rankingScore(candidate);
            String score = rankingScore == 0.0
                    ? "—" : "%.2f".formatted(rankingScore);
            Button row = pocketBrowserPanel.addChild(new Button(
                    "%s%d. %s  |  %s  |  score %s  |  %d residues"
                            .formatted(
                                    active ? "▶ " : "   ",
                                    index + 1,
                                    candidate.name(),
                                    candidate.source(),
                                    score,
                                    candidate.residues().size())));
            row.addClickCommands(source -> enqueue(() -> {
                replaceDataset(
                        new PocketDataset(
                                browsableProtein, browsablePockets),
                        candidate);
                return null;
            }));
        }

        Container navigation =
                pocketBrowserPanel.addChild(new Container());
        Button previous = navigation.addChild(
                new Button("Previous"), 0, 0);
        Button next = navigation.addChild(new Button("Next"), 1, 0);
        previous.addClickCommands(source -> {
            if (pocketBrowserPage > 0) {
                pocketBrowserPage--;
                rebuildPocketBrowser();
            }
        });
        next.addClickCommands(source -> {
            if (pocketBrowserPage + 1 < pages) {
                pocketBrowserPage++;
                rebuildPocketBrowser();
            }
        });
    }

    private void replaceDataset(
            PocketDataset loaded,
            Pocket loadedPocket) {
        pocketResidueLabels.dispose();
        proteinResidueLabels.dispose();
        if (controlsPanel != null) {
            controlsPanel.removeFromParent();
        }
        scene.clearLayers();

        protein = loaded.protein();
        pockets = loaded.pockets();
        pocket = loadedPocket;
        pocketGeometry = PocketGeometry.geometry(
                protein.structure(), pocket);
        browsableProtein = protein;
        browsablePockets = pockets;
        selectedGeometry = null;
        pocketSurface = null;
        residueListStart = -1;
        cameraTarget.setLocalTranslation(Vector3f.ZERO);

        buildProteinContext();
        buildPocketSurface();
        buildLiningResidues();
        buildPocketCenter();
        buildPocketOpenings();
        buildDebugSpheres();
        PocketScene.setVisible(scene.fullProteinAtoms(), false);
        PocketScene.setVisible(scene.debugAlphaSpheres(), false);
        PocketScene.setVisible(scene.pocketOpenings(), false);
        createResidueLabels();
        rebuildSelectedResidues();
        if (!embedded) {
            configureControls();
            int selectedIndex =
                    pockets.indexOf(loadedPocket);
            pocketBrowserPage = Math.max(0, selectedIndex / 8);
            rebuildPocketBrowser();
        }
    }

    private void populateResidueControls() {
        residuePanel.clearChildren();
        List<Map.Entry<String, Node>> entries =
                new ArrayList<>(scene.residues().entrySet());
        int pageSize = 8;
        int start = Math.min(
                Math.max(0, residueListStart),
                Math.max(0, entries.size() - pageSize));
        int end = Math.min(entries.size(), start + pageSize);
        for (int i = start; i < end; i++) {
            Map.Entry<String, Node> entry = entries.get(i);
            Checkbox residue = residuePanel.addChild(
                    new Checkbox(entry.getKey()));
            residue.setChecked(entry.getValue().getCullHint()
                    != Spatial.CullHint.Always);
            residue.addClickCommands(source ->
                    PocketScene.setVisible(
                            entry.getValue(), residue.isChecked()));
        }
    }

    private void focusResidue(String query) {
        String normalized = normalizeResidueQuery(query);
        if (normalized.isEmpty()) {
            selectionLabel.setText("Enter a residue name or number");
            return;
        }
        LocatedResidue match = allResidues().stream()
                .filter(residue -> residueMatches(residue, normalized))
                .findFirst()
                .orElse(null);
        if (match == null) {
            selectionLabel.setText(
                    "Residue not found: " + query.strip());
            return;
        }

        Point3D position = match.getAlphaCarbonPosition();
        if (position != null) {
            Vector3f focusPosition = relative(position);
            cameraTarget.setLocalTranslation(focusPosition);
            residueFocus.focus(focusPosition);
        }
        String label = residueLabel(match);
        Node residueNode = scene.residues().get(label);
        if (residueNode != null) {
            PocketScene.setVisible(residueNode, true);
            int index = new ArrayList<>(scene.residues().keySet())
                    .indexOf(label);
            if (index >= 0) {
                residueListStart = Math.max(0, index - 3);
                populateResidueControls();
            }
        }
        selectionLabel.setText(
                "Focused residue: %s (%d atoms)".formatted(
                        label, match.getAtomCount()));
    }

    private static boolean residueMatches(
            LocatedResidue residue,
            String query) {
        String chain = residue.getChain() == null
                ? ""
                : residue.getChain();
        String name = residue.getName() == null
                ? ""
                : residue.getName();
        String number = Integer.toString(residue.getNumber());
        return normalizeResidueQuery(name + number).equals(query)
                || normalizeResidueQuery(chain + number).equals(query)
                || normalizeResidueQuery(chain + name + number).equals(query)
                || number.equals(query);
    }

    private static String normalizeResidueQuery(String query) {
        return query == null
                ? ""
                : query.toUpperCase()
                        .replaceAll("[^A-Z0-9]", "");
    }

    private void addLayerCheckbox(
            Container panel,
            String text,
            Spatial layer,
            boolean checked) {
        Checkbox checkbox = panel.addChild(new Checkbox(text));
        checkbox.setChecked(checked);
        checkbox.addClickCommands(source ->
                PocketScene.setVisible(layer, checkbox.isChecked()));
    }

    private void addColorButton(
            Container panel,
            String text,
            ColorRGBA color,
            int column) {
        Button button = panel.addChild(new Button(text), column, 0);
        button.addClickCommands(source -> {
            pocketColor = color.clone();
            float alpha = opacityReference == null
                    ? POCKET_COLOR.a
                    : opacityReference.get().floatValue();
            setPocketOpacity(alpha);
        });
    }

    private void setPocketOpacity(float alpha) {
        if (pocketSurface == null) {
            return;
        }
        ColorRGBA color = pocketColor.clone();
        surfaceOpacity = alpha;
        color.a = alpha;
        pocketSurface.getMaterial().setColor("Diffuse", color);
        pocketSurface.getMaterial().setColor(
                "Ambient", color.mult(0.7f));
    }

    private void addLighting() {
        AmbientLight ambient = new AmbientLight();
        ambient.setColor(ColorRGBA.White.mult(0.65f));
        rootNode.addLight(ambient);
        DirectionalLight key = new DirectionalLight();
        key.setDirection(new Vector3f(-0.6f, -0.8f, -0.4f).normalizeLocal());
        key.setColor(ColorRGBA.White.mult(1.1f));
        rootNode.addLight(key);
    }

    private Geometry createSphere(
            String name,
            Vector3f position,
            float radius,
            ColorRGBA color,
            boolean transparent) {
        Geometry geometry = new Geometry(
                name,
                new Sphere(
                        SPHERE_Z_SAMPLES,
                        SPHERE_RADIAL_SAMPLES,
                        radius));
        geometry.setMaterial(unshaded(color, transparent));
        geometry.setLocalTranslation(position);
        geometry.setUserData("baseColor", color.clone());
        if (transparent) {
            geometry.setQueueBucket(RenderQueue.Bucket.Transparent);
        }
        return geometry;
    }

    private Material unshaded(ColorRGBA color, boolean transparent) {
        Material material = new Material(
                assetManager,
                "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", color);
        if (transparent) {
            material.getAdditionalRenderState().setBlendMode(
                    RenderState.BlendMode.Alpha);
            material.getAdditionalRenderState().setDepthWrite(false);
        }
        return material;
    }

    private void configureCamera() {
        cameraTarget = createSphere(
                "camera-target",
                Vector3f.ZERO,
                0.01f,
                ColorRGBA.BlackNoAlpha,
                true);
        cameraTarget.setCullHint(Spatial.CullHint.Always);
        rootNode.attachChild(cameraTarget);

        orbitController = new ScientificOrbitController(
                cam, cameraTarget, inputManager);
    }

    private void configureSelection() {
        inputManager.addMapping(
                SELECT_MAPPING,
                new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addListener(
                (ActionListener) (name, pressed, timePerFrame) -> {
                    if (!SELECT_MAPPING.equals(name)) {
                        return;
                    }
                    if (pressed) {
                        selectionPressPosition =
                                inputManager.getCursorPosition().clone();
                    } else if (selectionPressPosition != null
                            && selectionPressPosition.distanceSquared(
                                    inputManager.getCursorPosition()) <= 64.0f) {
                        selectAtCursor();
                    }
                },
                SELECT_MAPPING);
    }

    private void selectAtCursor() {
        Vector2f cursor = inputManager.getCursorPosition();
        Vector3f origin = cam.getWorldCoordinates(cursor, 0.0f);
        Vector3f direction = cam.getWorldCoordinates(cursor, 1.0f)
                .subtractLocal(origin)
                .normalizeLocal();
        CollisionResults results = new CollisionResults();
        scene.root().collideWith(new Ray(origin, direction), results);
        if (results.size() == 0) {
            setSelected(null);
            if (selectionLabel != null) {
                selectionLabel.setText("Selected: none");
            }
            selectionListener.accept("Selected: none");
            return;
        }
        Geometry geometry = closestAtom(results);
        if (geometry == null) {
            geometry = closestPickable(results);
        }
        if (geometry == null) {
            clearSelection();
            return;
        }
        setSelected(geometry);
        String label = geometry.getUserData("selectionLabel");
        if (selectionLabel != null) {
            selectionLabel.setText(selectionText(geometry, label));
        }
        selectionListener.accept(selectionText(geometry, label));
    }

    private void clearSelection() {
        setSelected(null);
        if (selectionLabel != null) {
            selectionLabel.setText("Selected: none");
        }
        selectionListener.accept("Selected: none");
    }

    private static Geometry closestAtom(CollisionResults results) {
        for (CollisionResult result : results) {
            Geometry geometry = result.getGeometry();
            if (geometry.getUserData("atomName") != null
                    && isPickable(geometry)) {
                return geometry;
            }
        }
        return null;
    }

    private static Geometry closestPickable(CollisionResults results) {
        for (CollisionResult result : results) {
            Geometry geometry = result.getGeometry();
            if (isPickable(geometry)) {
                return geometry;
            }
        }
        return null;
    }

    private static boolean isPickable(Spatial spatial) {
        Spatial current = spatial;
        while (current != null) {
            if (current.getCullHint() == Spatial.CullHint.Always) {
                return false;
            }
            current = current.getParent();
        }
        return true;
    }

    private static String selectionText(
            Geometry geometry,
            String selectionName) {
        String atomName = geometry.getUserData("atomName");
        if (atomName == null) {
            return "Selected: " + (
                    selectionName == null
                            ? geometry.getName()
                            : selectionName);
        }
        return """
                Selected: %s
                Element: %s   Partial charge: %s e
                Residue: %s   Position: %s
                """.formatted(
                atomName,
                geometry.getUserData("element"),
                geometry.getUserData("charge"),
                geometry.getUserData("residue"),
                geometry.getUserData("position")).stripTrailing();
    }

    private static void addAtomMetadata(
            Geometry geometry,
            Atom atom,
            LocatedResidue residue) {
        Element element = atom.getElement();
        Point3D position = atom.getPosition();
        geometry.setUserData("atomName", atom.getName());
        geometry.setUserData(
                "element",
                element == null ? "Unknown" : element.symbol());
        geometry.setUserData(
                "charge",
                "%.4f".formatted(atom.getCharge()));
        geometry.setUserData("residue", residueLabel(residue));
        geometry.setUserData(
                "position",
                "(%.2f, %.2f, %.2f) Å".formatted(
                        position.x(), position.y(), position.z()));
    }

    private void setSelected(Geometry geometry) {
        if (selectedGeometry != null) {
            ColorRGBA baseColor =
                    selectedGeometry.getUserData("baseColor");
            if (baseColor != null
                    && selectedGeometry.getMaterial().getParam("Color") != null) {
                selectedGeometry.getMaterial().setColor("Color", baseColor);
            }
        }
        selectedGeometry = geometry;
        if (selectedGeometry != null
                && selectedGeometry.getUserData("baseColor") != null
                && selectedGeometry.getMaterial().getParam("Color") != null) {
            selectedGeometry.getMaterial().setColor(
                    "Color", ColorRGBA.Yellow);
        }
    }

    private Vector3f relative(Point3D point) {
        Point3D center = pocketGeometry.centroid();
        return new Vector3f(
                (float) (point.x() - center.x()),
                (float) (point.y() - center.y()),
                (float) (point.z() - center.z()));
    }

    private static double distance(Point3D first, Point3D second) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double covalentRadius(Element element) {
        return element == null || element == Element.UNKNOWN
                ? 0.77
                : element.getCovalentRadius();
    }

    private static String residueLabel(LocatedResidue residue) {
        return "%s %s%d".formatted(
                residue.getChain(), residue.getName(), residue.getNumber());
    }

    private List<LocatedResidue> nonPocketResidues() {
        return allResidues().stream()
                .filter(residue ->
                        !scene.residues().containsKey(residueLabel(residue)))
                .toList();
    }

    private List<LocatedResidue> liningResidues() {
        return locate(residueSelection.liningResidues(
                protein.structure(), pocket, 4.0));
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

    static ColorRGBA elementColor(Element element) {
        if (element == null) {
            return new ColorRGBA(0.7f, 0.7f, 0.7f, 1.0f);
        }
        return switch (element) {
            case C -> new ColorRGBA(0.35f, 0.38f, 0.42f, 1.0f);
            case N -> new ColorRGBA(0.2f, 0.35f, 1.0f, 1.0f);
            case O -> new ColorRGBA(1.0f, 0.2f, 0.2f, 1.0f);
            case S -> new ColorRGBA(1.0f, 0.8f, 0.1f, 1.0f);
            case P -> new ColorRGBA(1.0f, 0.45f, 0.1f, 1.0f);
            case F, CL -> new ColorRGBA(0.2f, 0.85f, 0.25f, 1.0f);
            case BR -> new ColorRGBA(0.65f, 0.2f, 0.1f, 1.0f);
            case I -> new ColorRGBA(0.45f, 0.1f, 0.65f, 1.0f);
            case H -> ColorRGBA.White;
            default -> element.isMetal()
                    ? new ColorRGBA(0.65f, 0.5f, 0.85f, 1.0f)
                    : new ColorRGBA(0.7f, 0.7f, 0.7f, 1.0f);
        };
    }

    void setEmbedded(boolean embedded) {
        this.embedded = embedded;
    }

    void setSelectionListener(Consumer<String> listener) {
        selectionListener = Objects.requireNonNull(listener, "listener");
    }

    void loadDataset(PocketDataset loaded, Pocket loadedPocket) {
        enqueue(() -> {
            replaceDataset(loaded, loadedPocket);
            return null;
        });
    }

    void setLayerVisible(String layer, boolean visible) {
        enqueue(() -> {
            Spatial target = switch (layer) {
                case "context" -> scene.proteinContext();
                case "surface" -> scene.pocketSurface();
                case "residues" -> scene.liningResidues();
                case "atoms" -> scene.fullProteinAtoms();
                case "spheres" -> scene.debugAlphaSpheres();
                case "openings" -> scene.pocketOpenings();
                default -> throw new IllegalArgumentException(
                        "Unknown layer: " + layer);
            };
            if ("atoms".equals(layer) && visible) {
                buildFullProteinAtoms();
            }
            PocketScene.setVisible(target, visible);
            return null;
        });
    }

    void setSurfaceOpacity(float opacity) {
        enqueue(() -> {
            setPocketOpacity(opacity);
            return null;
        });
    }

    void setSurfaceColor(ColorRGBA color) {
        enqueue(() -> {
            pocketColor = color.clone();
            setPocketOpacity(surfaceOpacity);
            return null;
        });
    }

    void setPocketResidueLabelsVisible(boolean visible) {
        pocketResidueLabelsVisible = visible;
        enqueue(() -> {
            if (pocketResidueLabels != null) {
                pocketResidueLabels.setEnabled(visible);
            }
            return null;
        });
    }

    void setProteinResidueLabelsVisible(boolean visible) {
        proteinResidueLabelsVisible = visible;
        enqueue(() -> {
            if (proteinResidueLabels != null) {
                proteinResidueLabels.setEnabled(visible);
            }
            return null;
        });
    }

    void setResidueLabelSize(float size) {
        residueLabelSize = size;
        enqueue(() -> {
            if (pocketResidueLabels != null) {
                pocketResidueLabels.setSize(size);
            }
            if (proteinResidueLabels != null) {
                proteinResidueLabels.setSize(size);
            }
            if (selectedResidueLabels != null) {
                selectedResidueLabels.setSize(size);
            }
            return null;
        });
    }

    void setSelectedResidues(List<LocatedResidue> residues) {
        List<LocatedResidue> snapshot = List.copyOf(residues);
        enqueue(() -> {
            selectedResidues = snapshot;
            rebuildSelectedResidues();
            return null;
        });
    }

    void setSelectedAtomColor(String atomName, ColorRGBA color) {
        String normalized = atomName.strip().toUpperCase();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "Atom name must not be blank");
        }
        ColorRGBA snapshot = color.clone();
        enqueue(() -> {
            selectedAtomColors.put(normalized, snapshot);
            rebuildSelectedResidues();
            return null;
        });
    }

    void clearSelectedAtomColors() {
        enqueue(() -> {
            selectedAtomColors.clear();
            rebuildSelectedResidues();
            return null;
        });
    }

    void focusOnResidue(LocatedResidue residue) {
        enqueue(() -> {
            Point3D position = residue.getAlphaCarbonPosition();
            if (position != null) {
                Vector3f focus = relative(position);
                cameraTarget.setLocalTranslation(focus);
                residueFocus.focus(focus);
            }
            return null;
        });
    }
}
