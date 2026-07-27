package totah.lab.pipeline.stage;

import totah.lab.docking.flex.FlexResidueSelector;
import totah.lab.docking.flex.FlexTorsionTreeBuilder;
import totah.lab.docking.torsion.TorsionTree;
import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.pipeline.Stage;
import totah.lab.protein.Atom;
import totah.lab.protein.Residue;
import totah.lab.protein.Topology;
import totah.lab.structure.io.pdbqt.FlexPDBQTWriter;
import totah.lab.structure.io.pdbqt.RigidPDBQTWriter;
import totah.lab.topology.AutoDockType;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Exports the prepared receptor as PDBQT.
 *
 * Rigid-only (no flex_residues in context): a single prepared_receptor.pdbqt
 * with plain ATOM records.
 *
 * With flex_residues ("A:123" entries): follows Meeko/prepare_flexreceptor
 * conventions - the rigid file keeps only the backbone atoms (N, C, O, OXT
 * and the amide hydrogens) of flex residues, and a prepared_flex.pdbqt holds
 * one BEGIN_RES/ROOT/BRANCH/ENDBRANCH/END_RES torsion tree per flex residue.
 * Each flex tree is rooted at CA (the side-chain anchor, held fixed relative
 * to the receptor frame) with one BRANCH per rotatable chi bond from
 * SideChainRotamers. No TORSDOF record is written anywhere: Vina 1.2 aborts
 * with "Unknown or inappropriate tag found in flex residue" on TORSDOF in the
 * flex file (verified against the binary), and Meeko omits it for the same
 * reason. The rigid file carries plain ATOM records only.
 */
public class PdbqtExporterStage implements Stage {

    private final FlexResidueSelector flexResidueSelector = new FlexResidueSelector();
    private final FlexTorsionTreeBuilder flexTorsionTreeBuilder = new FlexTorsionTreeBuilder();

    @Override
    @SuppressWarnings("unchecked")
    public void run(PipelineContext context) throws Exception {
        List<Residue> residues = (List<Residue>) context.get(ContextKeys.PROTEIN_RESIDUES);
        Objects.requireNonNull(residues, "No prepared protein residues found in context.");
        if (residues.isEmpty()) {
            throw new IllegalStateException("No protein_residues in context. Run AD4AtomTypingStage first.");
        }
        context.require(ContextKeys.AD4_ATOM_TYPING_REPORT);
        validatePreparedForPdbqt(residues);

        Path runDirectory = context.getRunDirectory();
        Objects.requireNonNull(runDirectory, "Missing runDirectory execution path inside context.");

        List<String> flexEntries = (List<String>) context.get(ContextKeys.FLEX_RESIDUES);
        Map<String, Residue> flexResidues = flexResidueSelector.resolve(residues, flexEntries);

        Path outputPdbqtFile = runDirectory.resolve("prepared_receptor.pdbqt");
        Files.createDirectories(outputPdbqtFile.getParent());
        if (flexResidues.isEmpty()) {
            try(BufferedWriter bw = Files.newBufferedWriter(outputPdbqtFile);
                RigidPDBQTWriter writer = new RigidPDBQTWriter(bw)) {
                writer.write(residues);
                context.put(ContextKeys.RECEPTOR_PDBQT, outputPdbqtFile.toString());
                context.put(ContextKeys.OUTPUT_PDBQT_PATH, outputPdbqtFile.toString());
                context.put(ContextKeys.PDBQT_EXPORT_REPORT,
                        new PdbqtExportReport(residues.size(), atomCount(residues), 0,
                                outputPdbqtFile.toString(), null));
            }
            return;
        }

        // Flex mode needs the bond graph to walk side chains
        Topology topology = context.require(ContextKeys.PROTEIN_TOPOLOGY);

        // Flat atom indexing matches the topology (residue iteration order)
        Map<Residue, Integer> flatBase = new LinkedHashMap<>();
        int base = 0;
        for (Residue residue : residues) {
            flatBase.put(residue, base);
            base += residue.getAtoms().size();
        }

        Map<Residue, TorsionTree> flexTrees = new LinkedHashMap<>();
        for (Residue residue : flexResidues.values()) {
            flexTrees.put(residue, flexTorsionTreeBuilder.build(residue, flatBase.get(residue), topology));
        }

        try (BufferedWriter bw = Files.newBufferedWriter(outputPdbqtFile);
             RigidPDBQTWriter writer = new RigidPDBQTWriter(bw)) {
            for (Residue residue : residues) {
                TorsionTree tree = flexTrees.get(residue);
                for (int i = 0; i < residue.getAtoms().size(); i++) {
                    // Flex side-chain atoms leave the rigid file; backbone stays
                    if (tree != null && tree.containsAtom(i)){
                        continue;
                    }
                    Atom atom = residue.getAtoms().get(i);
                    writer.write(residue, atom);
                }
            }
        }

        context.put(ContextKeys.RECEPTOR_PDBQT, outputPdbqtFile.toString());
        context.put(ContextKeys.OUTPUT_PDBQT_PATH, outputPdbqtFile.toString());

        Path flexPdbqtFile = runDirectory.resolve("prepared_flex.pdbqt");
        try(BufferedWriter bw = Files.newBufferedWriter(flexPdbqtFile);
            FlexPDBQTWriter writer = new FlexPDBQTWriter(bw)){
            writer.write(flexTrees);
        }
        context.put(ContextKeys.FLEX_PDBQT, flexPdbqtFile.toString());
        context.put(ContextKeys.FLEX_PDBQT_PATH, flexPdbqtFile.toString());
        context.put(ContextKeys.PDBQT_EXPORT_REPORT,
                new PdbqtExportReport(residues.size(), atomCount(residues), flexResidues.size(),
                        outputPdbqtFile.toString(), flexPdbqtFile.toString()));
    }

    private void validatePreparedForPdbqt(List<Residue> residues) {
        for (Residue residue : residues) {
            for (Atom atom : residue.getAtoms()) {
                if (!Double.isFinite(atom.getCharge())) {
                    throw new IllegalStateException("Non-finite charge on " + atom.getName()
                            + " in " + residue.getName() + " " + residue.getChain() + ":" + residue.getNumber());
                }
                String ad4Type = atom.getAutoDockType();
                if (ad4Type == null || ad4Type.isBlank() || !isLegalAd4Type(ad4Type)) {
                    throw new IllegalStateException("Missing or illegal AutoDock4 type on " + atom.getName()
                            + " in " + residue.getName() + " " + residue.getChain() + ":" + residue.getNumber());
                }
            }
        }
    }

    private boolean isLegalAd4Type(String type) {
        for (AutoDockType value : AutoDockType.values()) {
            if (value.getSymbol().equals(type)) return true;
        }
        return false;
    }

    private int atomCount(List<Residue> residues) {
        return residues.stream().mapToInt(Residue::getAtomCount).sum();
    }
}
