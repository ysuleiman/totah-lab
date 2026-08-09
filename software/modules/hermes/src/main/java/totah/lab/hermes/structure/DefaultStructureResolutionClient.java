package totah.lab.hermes.structure;

import totah.lab.hermes.http.RemoteEndpoints;
import totah.lab.hermes.uniprot.UniProtClient;
import totah.lab.hermes.uniprot.UniProtEntry;
import totah.lab.hermes.uniprot.UniProtException;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Default structure resolver backed by UniProt cross-references.
 *
 * <p>Experimental PDB structures are preferred over AlphaFold predictions.
 * The resolver does not download coordinates; it returns normalized structure
 * references that can subsequently be passed to the appropriate provider client.</p>
 */
public final class DefaultStructureResolutionClient
        implements StructureResolutionClient {

    private static final URI RCSB_DOWNLOAD_BASE_URI =
            RemoteEndpoints.uri("rcsb.download");

    private static final URI ALPHAFOLD_FILES_BASE_URI =
            RemoteEndpoints.uri("alphafold.files");

    private static final String ALPHAFOLD_MODEL_VERSION = "v6";

    private static final Comparator<ResolvedProteinStructure> PREFERENCE_ORDER =
            Comparator.comparingInt(DefaultStructureResolutionClient::preferenceRank)
                    .thenComparing(ResolvedProteinStructure::identifier);

    private final UniProtClient uniProtClient;

    public DefaultStructureResolutionClient(UniProtClient uniProtClient) {
        this.uniProtClient =
                Objects.requireNonNull(uniProtClient, "uniProtClient");
    }

    @Override
    public Optional<ResolvedProteinStructure> resolve(String uniProtAccession)
            throws StructureResolutionException, InterruptedException {

        return resolveAll(uniProtAccession).preferred();
    }

    @Override
    public ProteinStructureResolution resolveAll(String uniProtAccession)
            throws StructureResolutionException, InterruptedException {

        String normalized = normalizeUniProtAccession(uniProtAccession);
        UniProtEntry entry = fetchUniProtEntry(normalized);

        List<ResolvedProteinStructure> structures = new ArrayList<>();

        addExperimentalStructures(entry, structures);
        addAlphaFoldStructures(entry, structures);

        structures.sort(PREFERENCE_ORDER);

        return new ProteinStructureResolution(
                normalized,
                entry.accession(),
                structures
        );
    }

    private UniProtEntry fetchUniProtEntry(String accession)
            throws StructureResolutionException, InterruptedException {

        try {
            return uniProtClient.fetch(accession)
                    .orElseThrow(() -> new StructureResolutionException(
                            StructureResolutionFailure.PROTEIN_NOT_FOUND,
                            accession,
                            "No UniProt entry exists for accession " + accession
                    ));
        } catch (UniProtException e) {
            throw new StructureResolutionException(
                    StructureResolutionFailure.PROVIDER_FAILURE,
                    accession,
                    "Unable to retrieve UniProt entry " + accession,
                    e
            );
        }
    }

    private static void addExperimentalStructures(
            UniProtEntry entry,
            List<ResolvedProteinStructure> destination
    ) {
        for (String rawPdbId : safeList(entry.pdbIds())) {
            String pdbId = normalizePdbId(rawPdbId);

            destination.add(new ResolvedProteinStructure(
                    entry.accession(),
                    StructureSource.RCSB_PDB,
                    StructureKind.EXPERIMENTAL,
                    pdbId,
                    RCSB_DOWNLOAD_BASE_URI.resolve(pdbId + ".cif"),
                    StructureFormat.MMCIF
            ));
        }
    }

    private static void addAlphaFoldStructures(
            UniProtEntry entry,
            List<ResolvedProteinStructure> destination
    ) {
        for (String rawIdentifier : safeList(entry.alphaFoldIds())) {
            String accession = normalizeAlphaFoldAccession(rawIdentifier);

            String modelId =
                    "AF-" + accession + "-F1-model_" + ALPHAFOLD_MODEL_VERSION;

            destination.add(new ResolvedProteinStructure(
                    entry.accession(),
                    StructureSource.ALPHAFOLD_DB,
                    StructureKind.PREDICTED,
                    modelId,
                    ALPHAFOLD_FILES_BASE_URI.resolve(modelId + ".cif"),
                    StructureFormat.MMCIF
            ));
        }
    }

    private static int preferenceRank(ResolvedProteinStructure structure) {
        return switch (structure.kind()) {
            case EXPERIMENTAL -> 0;
            case PREDICTED -> 1;
        };
    }

    private static String normalizeUniProtAccession(String accession) {
        Objects.requireNonNull(accession, "uniProtAccession");

        String normalized =
                accession.trim().toUpperCase(Locale.ROOT);

        if (normalized.isBlank()) {
            throw new IllegalArgumentException(
                    "uniProtAccession must not be blank"
            );
        }

        if (!normalized.matches("[A-Z0-9]+(?:-[0-9]+)?")) {
            throw new IllegalArgumentException(
                    "Invalid UniProt accession: " + accession
            );
        }

        return normalized;
    }

    private static String normalizePdbId(String pdbId) {
        Objects.requireNonNull(pdbId, "pdbId");

        String normalized =
                pdbId.trim().toUpperCase(Locale.ROOT);

        if (!normalized.matches("[0-9][A-Z0-9]{3}")) {
            throw new IllegalArgumentException(
                    "Invalid PDB identifier returned by UniProt: " + pdbId
            );
        }

        return normalized;
    }

    private static String normalizeAlphaFoldAccession(String identifier) {
        Objects.requireNonNull(identifier, "alphaFoldIdentifier");

        String normalized =
                identifier.trim().toUpperCase(Locale.ROOT);

        if (normalized.startsWith("AF-")) {
            normalized = normalized.substring(3);
        }

        int fragmentIndex = normalized.indexOf("-F");
        if (fragmentIndex > 0) {
            normalized = normalized.substring(0, fragmentIndex);
        }

        if (!normalized.matches("[A-Z0-9]+(?:-[0-9]+)?")) {
            throw new IllegalArgumentException(
                    "Invalid AlphaFold identifier returned by UniProt: "
                            + identifier
            );
        }

        return normalized;
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }
}
