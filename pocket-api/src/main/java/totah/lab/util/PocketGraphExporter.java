package totah.lab.util;

import totah.lab.protein.Atom;
import totah.lab.protein.Pocket;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PocketGraphExporter {

    private PocketGraphExporter() {}

    /**
     * Translates a fully resolved Pocket into a pure, numerical feature graph
     * matrix structure optimized for Graph Neural Network training payloads.
     */
    public static Map<String, Object> exportToGraphJson(Pocket pocket) {
        Map<String, Object> graph = new HashMap<>();

        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        // 1. Resolve your heavy domain objects via your custom resolver magic
        List<Residue> residues = pocket.getResidues();
        List<Atom> allPocketAtoms = new ArrayList<>();

        // Flatten the structural hierarchy to an atomic-graph layout
        for (Residue res : residues) {
            if (res.getAtoms() != null) {
                allPocketAtoms.addAll(res.getAtoms());
            }
        }

        // 2. Build Atom Nodes (Extracting physical features)
        for (int i = 0; i < allPocketAtoms.size(); i++) {
            Atom atom = allPocketAtoms.get(i);
            Point3D pos = atom.getPosition();

            Map<String, Object> nodeFeatures = new HashMap<>();
            nodeFeatures.put("node_id", i);
            nodeFeatures.put("atom_name", atom.getName());
            nodeFeatures.put("element", atom.getElement() != null ? atom.getElement().toString() : "UNKNOWN");
            nodeFeatures.put("b_factor", atom.getBFactor());
            nodeFeatures.put("occupancy", atom.getOccupancy());
            nodeFeatures.put("coords", List.of(pos.x(), pos.y(), pos.z()));

            nodes.add(nodeFeatures);
        }

        // 3. Compute Spatial Inter-Atom Edges (Distance matrix thresholding)
        double interactionThreshold = 4.5; // Angstroms
        for (int i = 0; i < allPocketAtoms.size(); i++) {
            for (int j = i + 1; j < allPocketAtoms.size(); j++) {
                Point3D posA = allPocketAtoms.get(i).getPosition();
                Point3D posB = allPocketAtoms.get(j).getPosition();

                // Calculate Euclidean spatial distance
                double dx = posA.x() - posB.x();
                double dy = posA.y() - posB.y();
                double dz = posA.z() - posB.z();
                double distance = Math.sqrt(dx*dx + dy*dy + dz*dz);

                if (distance <= interactionThreshold) {
                    Map<String, Object> edge = new HashMap<>();
                    edge.put("source", i);
                    edge.put("target", j);
                    edge.put("distance", distance);
                    edges.add(edge);
                }
            }
        }

        // 4. Assemble Global Target Attributes
        Map<String, Object> globalMetadata = new HashMap<>();
        globalMetadata.put("p2rank_score", pocket.getScore());
        globalMetadata.put("experimental_affinity", pocket.getAttributes().getOrDefault("experimental_pKd", 0.0));

        graph.put("metadata", globalMetadata);
        graph.put("nodes", nodes);
        graph.put("edges", edges);

        return graph;
    }
}
