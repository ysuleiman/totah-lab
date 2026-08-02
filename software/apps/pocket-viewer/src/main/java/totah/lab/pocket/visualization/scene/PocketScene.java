package totah.lab.pocket.visualization.scene;

import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Owns the viewer's semantic layers so rendering and controls depend on layer
 * roles instead of the way each layer happens to be drawn.
 */
public final class PocketScene {
    private final Node root = new Node("pocket-scene");
    private final Node proteinContext = layer("protein-context");
    private final Node pocketSurface = layer("pocket-surface");
    private final Node liningResidues = layer("lining-residues");
    private final Node pocketCenter = layer("pocket-center");
    private final Node fullProteinAtoms = layer("full-protein-atoms");
    private final Node debugAlphaSpheres = layer("debug-alpha-spheres");
    private final Node pocketOpenings = layer("pocket-openings");
    private final Node selectedResidues = layer("selected-residues");
    private final Map<String, Node> residues = new LinkedHashMap<>();

    public Node root() {
        return root;
    }

    public Node proteinContext() {
        return proteinContext;
    }

    public Node pocketSurface() {
        return pocketSurface;
    }

    public Node liningResidues() {
        return liningResidues;
    }

    public Node pocketCenter() {
        return pocketCenter;
    }

    public Node fullProteinAtoms() {
        return fullProteinAtoms;
    }

    public Node debugAlphaSpheres() {
        return debugAlphaSpheres;
    }

    public Node pocketOpenings() {
        return pocketOpenings;
    }

    public Node selectedResidues() {
        return selectedResidues;
    }

    public Map<String, Node> residues() {
        return residues;
    }

    public static void setVisible(Spatial spatial, boolean visible) {
        spatial.setCullHint(visible
                ? Spatial.CullHint.Inherit
                : Spatial.CullHint.Always);
    }

    public void clearLayers() {
        proteinContext.detachAllChildren();
        pocketSurface.detachAllChildren();
        liningResidues.detachAllChildren();
        pocketCenter.detachAllChildren();
        fullProteinAtoms.detachAllChildren();
        debugAlphaSpheres.detachAllChildren();
        pocketOpenings.detachAllChildren();
        selectedResidues.detachAllChildren();
        residues.clear();
    }

    private Node layer(String name) {
        Node layer = new Node(name);
        root.attachChild(layer);
        return layer;
    }
}
