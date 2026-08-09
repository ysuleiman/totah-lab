package totah.lab.hephaestus.ligand;

import org.biojava.nbio.structure.chem.ChemCompProvider;
import totah.lab.hephaestus.ligand.operation.LigandAD4AtomTypingOperation;
import totah.lab.hephaestus.ligand.operation.LigandChargeAssignmentOperation;
import totah.lab.hephaestus.ligand.operation.LigandHydrogenationOperation;
import totah.lab.hephaestus.ligand.operation.LigandTopologyOperation;
import totah.lab.hephaestus.ligand.operation.LigandTorsionOperation;
import totah.lab.hephaestus.ligand.operation.SdfLigandTopologyOperation;
import totah.lab.hephaestus.model.PreparedLigand;
import totah.lab.hephaestus.preparation.OperationResult;
import totah.lab.hephaestus.model.PreparationIssue;
import totah.lab.hermes.file.sdf.SdfLigand;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DefaultLigandPreparer
        implements LigandPreparer {

    private final List<LigandPreparationOperation> operations;

    public DefaultLigandPreparer(
            List<LigandPreparationOperation> operations) {

        Objects.requireNonNull(operations, "operations");

        if (operations.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "operations must not contain null elements.");
        }

        this.operations = List.copyOf(operations);
    }

    public static DefaultLigandPreparer standard(ChemCompProvider chemCompProvider) {
        Objects.requireNonNull(chemCompProvider, "chemCompProvider");
        return new DefaultLigandPreparer(List.of(
                new LigandTopologyOperation(chemCompProvider),
                new LigandHydrogenationOperation(),
                new LigandChargeAssignmentOperation(),
                new LigandAD4AtomTypingOperation(),
                new LigandTorsionOperation()));
    }

    /**
     * Preparation pipeline for SDF input: topology comes from the parsed
     * bond table instead of the CCD. The SDF must carry explicit
     * hydrogens and 3D coordinates.
     */
    public static DefaultLigandPreparer sdf(SdfLigand source) {
        Objects.requireNonNull(source, "source");
        return new DefaultLigandPreparer(List.of(
                new SdfLigandTopologyOperation(source),
                new LigandHydrogenationOperation(),
                new LigandChargeAssignmentOperation(),
                new LigandAD4AtomTypingOperation(),
                new LigandTorsionOperation()));
    }

    @Override
    public LigandPreparationResult prepare(
            LigandPreparationRequest request) {

        Objects.requireNonNull(request, "request");
        rejectUnimplementedOptions(request.options());

        PreparedLigand current =
                PreparedLigand.of(request.ligand());

        List<PreparationIssue> issues = new ArrayList<>();

        for (LigandPreparationOperation operation : operations) {
            OperationResult<PreparedLigand> result;
            try {
                result = Objects.requireNonNull(
                        operation.apply(current, request.options()),
                        "operation returned null");
            } catch (UnsupportedLigandException exception) {
                throw exception;
            } catch (IllegalArgumentException | IllegalStateException exception) {
                throw unsupported(current, operation, exception);
            }

            current = result.value();
            issues.addAll(result.issues());

            if (result.hasFatalIssue()) {
                break;
            }
        }
        return new LigandPreparationResult(current, issues);
    }

    private void rejectUnimplementedOptions(LigandPreparationOptions options) {
        if (options.generateProtonationStates()) {
            throw new IllegalArgumentException(
                    "Ligand protonation-state generation is not implemented.");
        }
        if (options.generateTautomers()) {
            throw new IllegalArgumentException(
                    "Ligand tautomer generation is not implemented.");
        }
        if (options.generateConformers()) {
            throw new IllegalArgumentException(
                    "Ligand conformer generation is not implemented.");
        }
    }

    private UnsupportedLigandException unsupported(
            PreparedLigand ligand,
            LigandPreparationOperation operation,
            RuntimeException exception) {
        var sourceLigand = ligand.ligand();
        String componentId = sourceLigand.componentCode()
                .orElse(sourceLigand.id());
        String message = exception.getMessage() == null
                ? operation.getClass().getSimpleName() + " failed"
                : exception.getMessage();
        LigandUnsupportedReason reason;
        if (operation instanceof LigandTopologyOperation) {
            if (message.contains("missing heavy atoms=")
                    && !message.contains("missing heavy atoms=[]")) {
                reason = LigandUnsupportedReason.MISSING_HEAVY_ATOMS;
            } else if (message.contains("extra heavy atoms=")
                    && !message.contains("extra heavy atoms=[]")) {
                reason = LigandUnsupportedReason.EXTRA_HEAVY_ATOMS;
            } else {
                reason = LigandUnsupportedReason.INCOMPLETE_CCD;
            }
        } else if (operation instanceof LigandHydrogenationOperation) {
            reason = message.toLowerCase(java.util.Locale.ROOT).contains("valence")
                    ? LigandUnsupportedReason.INVALID_VALENCE
                    : LigandUnsupportedReason.UNUSABLE_HYDROGEN_REFERENCE_GEOMETRY;
        } else if (operation instanceof LigandChargeAssignmentOperation) {
            reason = LigandUnsupportedReason.UNSUPPORTED_ELEMENT_FOR_CHARGE;
        } else if (operation instanceof LigandAD4AtomTypingOperation) {
            reason = LigandUnsupportedReason.UNSUPPORTED_AD4_TYPE;
        } else if (operation instanceof LigandTorsionOperation) {
            reason = LigandUnsupportedReason.DISCONNECTED_GRAPH;
        } else {
            throw exception;
        }
        return new UnsupportedLigandException(componentId, reason, message, exception);
    }
}
