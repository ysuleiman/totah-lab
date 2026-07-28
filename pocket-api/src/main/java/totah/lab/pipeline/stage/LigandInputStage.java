package totah.lab.pipeline.stage;

import org.biojava.nbio.structure.chem.ChemCompProvider;
import totah.lab.io.ChemCompProviders;
import totah.lab.io.StructureIO;
import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.pipeline.Stage;
import totah.lab.pipeline.cleanup.ClassifiedResidue;
import totah.lab.pipeline.cleanup.ResidueClassifier;
import totah.lab.pipeline.cleanup.ResidueDisposition;
import totah.lab.pipeline.cleanup.ResidueKind;
import totah.lab.pipeline.cleanup.ResidueRole;
import totah.lab.pipeline.cleanup.StructureCleanupResult;
import totah.lab.protein.Residue;
import totah.lab.protein.Structure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Loads independent ligand candidates from a PDB or mmCIF structure.
 */
public final class LigandInputStage implements Stage {

    private final ResidueClassifier classifier = new ResidueClassifier();

    @Override
    public void run(PipelineContext context) throws IOException {
        Objects.requireNonNull(context, "context is null");
        Path ligandPath = context.require(ContextKeys.LIGAND_PATH);
        validatePath(ligandPath);

        boolean onlineLookup = parseBoolean(
                context.get(ContextKeys.CCD_ONLINE_LOOKUP), false);
        Path cacheDirectory = onlineLookup
                ? ccdCacheDirectory(context)
                : null;
        ChemCompProvider provider = ChemCompProviders.create(
                onlineLookup, cacheDirectory);
        Structure structure = StructureIO.load(ligandPath, provider);
        if (structure.getResidues().isEmpty()) {
            throw new IllegalStateException(
                    "No ligand residues loaded from " + ligandPath);
        }

        List<ClassifiedResidue> candidates = structure.getResidues().stream()
                .map(this::classifyCandidate)
                .toList();
        context.put(ContextKeys.CHEM_COMP_PROVIDER, provider);
        context.put(ContextKeys.STRUCTURE_CLEANUP_RESULT,
                new StructureCleanupResult(
                        List.of(), candidates, List.of(), List.of(), List.of()));
    }

    private ClassifiedResidue classifyCandidate(Residue residue) {
        ResidueKind kind = classifier.classify(residue);
        return new ClassifiedResidue(
                residue,
                role(kind),
                ResidueDisposition.EXTRACT_AS_LIGAND,
                "independent ligand input classified as " + kind);
    }

    private ResidueRole role(ResidueKind kind) {
        return switch (kind) {
            case NON_POLYMER -> ResidueRole.LIGAND;
            case STANDARD_AMINO_ACID -> ResidueRole.STANDARD_AMINO_ACID;
            case MODIFIED_AMINO_ACID -> ResidueRole.MODIFIED_AMINO_ACID;
            case WATER -> ResidueRole.WATER;
            case ION_OR_METAL -> ResidueRole.METAL_OR_ION;
            case UNKNOWN -> ResidueRole.UNKNOWN;
        };
    }

    private Path ccdCacheDirectory(PipelineContext context) {
        Object configured = context.get(ContextKeys.CCD_CACHE_DIRECTORY);
        if (configured instanceof Path path) {
            return path;
        }
        if (configured != null && !configured.toString().isBlank()) {
            return Path.of(configured.toString());
        }
        return context.getWorkingDirectory().resolve(".ccd-cache");
    }

    private boolean parseBoolean(Object value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Boolean booleanValue) return booleanValue;
        return Boolean.parseBoolean(value.toString());
    }

    private void validatePath(Path path) throws IOException {
        Objects.requireNonNull(path, "ligandPath is null");
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IOException(
                    "Ligand structure file is not readable: " + path);
        }
        String name = path.getFileName().toString().toLowerCase();
        if (!name.endsWith(".pdb")
                && !name.endsWith(".cif")
                && !name.endsWith(".mmcif")) {
            throw new IllegalArgumentException(
                    "Unsupported ligand structure format: " + name
                            + "; supported formats are PDB and mmCIF");
        }
    }
}
