package totah.lab.util;

import totah.lab.protein.Atom;
import totah.lab.pocket.Pocket;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TensorFlowDatasetPacker {

    private TensorFlowDatasetPacker() {}

    /**
     * Single-Argument Multi-Row Dataset Packer.
     * Extracts structural pockets, links them to loaded chemical assays,
     * and compiles a comprehensive tensor matrix for TensorFlow.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> packMultiRowDataset(Map<String, Object> analyticalPayload) {
        Map<String, Object> masterDataset = new HashMap<>();
        List<Map<String, Object>> datasetRows = new ArrayList<>();

        // 1. Safe extraction of baseline features from the analytical footprint map
        boolean has7bTandem = (boolean) analyticalPayload.getOrDefault("mettl7b_has_cys_cys_tandem", true);

        // 2. Extract structural pockets and chemical libraries passed down through the map
        List<Pocket> pockets = (List<Pocket>) analyticalPayload.getOrDefault("pockets_list", List.of());
        List<Map<String, Object>> chemicalAssays = (List<Map<String, Object>>) analyticalPayload.getOrDefault("chemical_assays", List.of());

        // 3. Multi-Row Core Generation Loop
        for (Pocket pocket : pockets) {
            List<Residue> residues = pocket.getResidues();
            List<Atom> allAtoms = new ArrayList<>();

            for (Residue res : residues) {
                if (res.getAtoms() == null) continue;
                allAtoms.addAll(res.getAtoms());
            }

            // Compile dense 3D structural coordinate arrays
            List<List<Double>> coordMatrix = new ArrayList<>();
            List<List<Double>> elementMatrix = new ArrayList<>();
            for (Atom atom : allAtoms) {
                Point3D pos = atom.getPosition();
                coordMatrix.add(List.of(pos.x(), pos.y(), pos.z()));

                // One-Hot Element Matrix Encoding: [Carbon, Nitrogen, Oxygen, Sulfur, Other]
                String el = atom.getElement() != null ? atom.getElement().toString().toUpperCase() : "C";
                if (el.startsWith("C"))      elementMatrix.add(List.of(1.0, 0.0, 0.0, 0.0, 0.0));
                else if (el.startsWith("N")) elementMatrix.add(List.of(0.0, 1.0, 0.0, 0.0, 0.0));
                else if (el.startsWith("O")) elementMatrix.add(List.of(0.0, 0.0, 1.0, 0.0, 0.0));
                else if (el.startsWith("S")) elementMatrix.add(List.of(0.0, 0.0, 0.0, 1.0, 0.0));
                else                         elementMatrix.add(List.of(0.0, 0.0, 0.0, 0.0, 1.0));
            }

            // 4. Pair this explicit structural geometry matrix with every compound experiment
            for (Map<String, Object> assay : chemicalAssays) {
                Map<String, Object> trainingRow = new HashMap<>();

                trainingRow.put("is_mettl7b", has7bTandem ? 1.0 : 0.0);
                trainingRow.put("pocket_atom_count", allAtoms.size());
                trainingRow.put("pocket_coordinates", coordMatrix);
                trainingRow.put("pocket_elements", elementMatrix);

                trainingRow.put("ligand_smiles", assay.get("smiles_string"));
                trainingRow.put("ligand_fingerprint", assay.get("fingerprint_bits"));
                trainingRow.put("experimental_affinity_pKd", assay.get("affinity_score"));

                datasetRows.add(trainingRow);
            }
        }

        masterDataset.put("dataset_size", datasetRows.size());
        masterDataset.put("samples", datasetRows);
        return masterDataset;
    }
}
