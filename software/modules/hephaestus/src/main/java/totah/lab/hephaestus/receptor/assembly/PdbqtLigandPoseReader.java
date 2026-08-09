package totah.lab.hephaestus.receptor.assembly;

import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.structure.Atom;
import totah.lab.hephaestus.model.PreparedLigand;
import totah.lab.hermes.file.pdbqt.PdbqtAtom;
import totah.lab.hermes.file.pdbqt.PdbqtModel;
import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;
import totah.lab.hermes.file.pdbqt.vina.VinaResultParser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Reads docked PDBQT models as chemistry-preserving ligand poses. */
public final class PdbqtLigandPoseReader {

    private final PdbqtReader reader;
    private final VinaResultParser vinaResultParser;

    public PdbqtLigandPoseReader() {
        this(new PdbqtReader(), new VinaResultParser());
    }

    PdbqtLigandPoseReader(
            PdbqtReader reader,
            VinaResultParser vinaResultParser) {

        this.reader = Objects.requireNonNull(reader, "reader");
        this.vinaResultParser = Objects.requireNonNull(
                vinaResultParser, "vinaResultParser");
    }

    public List<LigandPose> read(
            Path pdbqt,
            PreparedLigand preparedLigand,
            String runId) throws IOException {

        Objects.requireNonNull(pdbqt, "pdbqt");
        Objects.requireNonNull(preparedLigand, "preparedLigand");
        String normalizedRunId = requireNonBlank(runId, "runId");

        List<LigandPose> poses = new ArrayList<>();
        for (PdbqtModel model : reader.read(pdbqt).models()) {
            poses.add(map(model, preparedLigand, normalizedRunId));
        }
        return List.copyOf(poses);
    }

    LigandPose map(
            PdbqtModel model,
            PreparedLigand preparedLigand,
            String runId) {

        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(preparedLigand, "preparedLigand");
        String normalizedRunId = requireNonBlank(runId, "runId");

        List<Atom> preparedAtoms = preparedLigand.ligand().structure()
                .getChains().stream()
                .flatMap(chain -> chain.residues().stream())
                .flatMap(residue -> residue.getAtoms().stream())
                .toList();
        if (preparedAtoms.size() != model.atoms().size()) {
            throw new IllegalArgumentException(
                    "PDBQT model " + model.modelNumber()
                            + " atom count " + model.atoms().size()
                            + " does not match prepared ligand atom count "
                            + preparedAtoms.size());
        }

        for (int index = 0; index < preparedAtoms.size(); index++) {
            requireSameElement(
                    preparedAtoms.get(index),
                    model.atoms().get(index),
                    index,
                    model.modelNumber());
        }

        Map<String, String> provenance = new LinkedHashMap<>();
        provenance.put("docking-run", normalizedRunId);
        provenance.put("pdbqt-model", Integer.toString(model.modelNumber()));
        vinaResultParser.parse(model.remarks()).ifPresent(result -> {
            provenance.put(
                    "vina-affinity-kcal-per-mol",
                    Double.toString(result.affinity()));
            if (result.rmsdLowerBound() != null) {
                provenance.put(
                        "vina-rmsd-lower-bound",
                        Double.toString(result.rmsdLowerBound()));
            }
            if (result.rmsdUpperBound() != null) {
                provenance.put(
                        "vina-rmsd-upper-bound",
                        Double.toString(result.rmsdUpperBound()));
            }
        });

        return LigandPose.fromCoordinates(
                normalizedRunId + ":model-" + model.modelNumber(),
                preparedLigand,
                model.atoms().stream().map(PdbqtAtom::position).toList(),
                provenance);
    }

    private static void requireSameElement(
            Atom prepared,
            PdbqtAtom posed,
            int index,
            int modelNumber) {

        Element posedElement = Element.fromSymbol(posed.element());
        if (prepared.getElement() == null
                || prepared.getElement() != posedElement) {
            throw new IllegalArgumentException(
                    "PDBQT model " + modelNumber + " atom " + (index + 1)
                            + " element " + posed.element()
                            + " does not match prepared atom "
                            + prepared.getName() + " element "
                            + prepared.getElement());
        }
    }

    private static String requireNonBlank(
            String value,
            String fieldName) {

        Objects.requireNonNull(value, fieldName);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank");
        }
        return normalized;
    }
}
