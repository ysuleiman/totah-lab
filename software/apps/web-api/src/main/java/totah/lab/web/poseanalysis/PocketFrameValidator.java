package totah.lab.web.poseanalysis;

import totah.lab.athena.pocket.compare.KabschRigidPointAligner;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.pdbqt.PdbqtGaiaMapper;
import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;
import totah.lab.hermes.file.pdb.reader.PdbReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates the coordinate-frame compatibility between the receptor
 * artifact a docking run used and the structure artifact a pocket's DB
 * rows were generated from, BEFORE any sphere-derived metric is
 * computed. Same accession or same sequence is never treated as
 * evidence of a shared frame — only artifact identity (SHA-256 of the
 * files) or an explicit rigid transform validated by a CA Kabsch fit
 * ({@link KabschRigidPointAligner}, the same aligner the pose
 * comparators use) is.
 *
 * <p>Validation thresholds, all calibration-fixed and documented here:
 * at least {@value #MIN_MATCHED_CA_PAIRS} matched CA pairs for a
 * robust fit; post-fit RMSD at most
 * {@value #MAX_VALIDATED_RMSD_ANGSTROMS} A for
 * {@link CoordinateCompatibility#VALIDATED_TRANSFORM}; a fitted
 * transform within {@value #IDENTITY_TRANSLATION_ANGSTROMS} A of the
 * identity counts as same-frame (spheres used as-is). Anything else is
 * {@link CoordinateCompatibility#INCOMPATIBLE}; a pocket-side
 * structure that cannot be loaded is
 * {@link CoordinateCompatibility#UNKNOWN}.</p>
 */
final class PocketFrameValidator {

    static final int MIN_MATCHED_CA_PAIRS = 30;
    static final double MAX_VALIDATED_RMSD_ANGSTROMS = 2.0;
    static final double IDENTITY_TRANSLATION_ANGSTROMS = 0.5;
    static final double IDENTITY_RMSD_ANGSTROMS = 1.0;

    private static final String METHOD =
            "Kabsch rigid CA fit (athena KabschRigidPointAligner)";
    private static final Pattern MODEL_VERSION =
            Pattern.compile("model_(\\w+)");
    private static final Pattern REMARK_NAME =
            Pattern.compile("^REMARK\\s+Name\\s*=\\s*(\\S+)");

    private final Path artifactRoot;
    private final PdbReader structureReader =
            new PdbReader();
    private final PdbqtReader pdbqtReader = new PdbqtReader();

    PocketFrameValidator(Path artifactRoot) {
        this.artifactRoot = Objects.requireNonNull(
                artifactRoot, "artifactRoot");
    }

    /**
     * Evaluates the compatibility between the run's docked receptor
     * (structure + artifact file) and the pocket-side structure
     * artifact row ({@code null} when the structure has no artifact —
     * compatibility is then UNKNOWN).
     */
    CoordinateProvenance evaluate(
            PoseRunProjection run,
            Structure receptor,
            Path receptorPdbqt,
            StructureArtifactProjection pocketArtifact
    ) {
        return evaluate(
                run.getReceptorArtifactId(),
                "chemflow receptor artifact",
                run.getStructureId(),
                receptor,
                receptorPdbqt,
                pocketArtifact
        );
    }

    /**
     * As {@link #evaluate}, for a receptor loaded from an explicit
     * file (for example an externally produced DiffDock directory)
     * instead of a persisted run's artifact.
     */
    CoordinateProvenance evaluate(
            String receptorArtifactId,
            String receptorSource,
            Long structureId,
            Structure receptor,
            Path receptorFile,
            StructureArtifactProjection pocketArtifact
    ) {
        StructureArtifactRef receptorRef = new StructureArtifactRef(
                receptorArtifactId,
                pocketArtifact == null
                        ? null
                        : pocketArtifact.getSourceAccession(),
                receptorSource,
                null,
                sha256(receptorFile)
        );

        if (pocketArtifact == null) {
            return new CoordinateProvenance(
                    receptorRef,
                    null,
                    CoordinateCompatibility.UNKNOWN,
                    null,
                    false,
                    "structure " + structureId + " has no"
                            + " structure artifact; pocket-row frame"
                            + " cannot be validated"
            );
        }

        Path pocketPath = resolve(
                pocketArtifact.getArtifactStorageLocation());
        StructureArtifactRef pocketRef = new StructureArtifactRef(
                String.valueOf(pocketArtifact.getArtifactId()),
                pocketArtifact.getSourceAccession(),
                pocketArtifact.getStructureSource(),
                modelVersion(pocketArtifact.getSourceAccession()),
                sha256(pocketPath)
        );

        if (receptorRef.sha256() != null
                && receptorRef.sha256().equals(pocketRef.sha256())) {
            return new CoordinateProvenance(
                    receptorRef,
                    pocketRef,
                    CoordinateCompatibility.IDENTICAL_ARTIFACT,
                    null,
                    true,
                    "receptor artifact and pocket structure artifact"
                            + " have identical content (sha256 "
                            + shortHash(receptorRef.sha256()) + ")"
            );
        }

        Structure pocketStructure;
        try {
            pocketStructure = readStructure(pocketPath);
        } catch (IOException | RuntimeException exception) {
            return new CoordinateProvenance(
                    receptorRef,
                    pocketRef,
                    CoordinateCompatibility.UNKNOWN,
                    null,
                    false,
                    "pocket structure artifact "
                            + pocketRef.artifactId() + " ("
                            + pocketArtifact.getArtifactStorageLocation()
                            + ") cannot be loaded: "
                            + exception.getMessage()
            );
        }

        return fit(receptorRef, pocketRef, receptor, pocketStructure,
                receptorFile);
    }

    private CoordinateProvenance fit(
            StructureArtifactRef receptorRef,
            StructureArtifactRef pocketRef,
            Structure receptor,
            Structure pocketStructure,
            Path receptorPdbqt
    ) {
        List<Point3D> receptorPoints = new ArrayList<>();
        List<Point3D> pocketPoints = new ArrayList<>();
        matchedCalphas(receptor, pocketStructure,
                receptorPoints, pocketPoints);

        if (receptorPoints.size() < MIN_MATCHED_CA_PAIRS) {
            return new CoordinateProvenance(
                    receptorRef,
                    pocketRef,
                    CoordinateCompatibility.INCOMPATIBLE,
                    new StructureTransform(
                            pocketRef.artifactId(),
                            receptorRef.artifactId(),
                            null,
                            receptorPoints.size(),
                            Double.NaN,
                            METHOD,
                            "REJECTED_TOO_FEW_PAIRS"
                    ),
                    false,
                    "only " + receptorPoints.size() + " matched CA"
                            + " pairs (< " + MIN_MATCHED_CA_PAIRS
                            + "); frames cannot be validated"
            );
        }

        RigidTransform transform = new KabschRigidPointAligner()
                .align(pocketPoints, receptorPoints);
        double rmsd = rmsd(
                transform.apply(pocketPoints), receptorPoints);
        StructureTransform record = new StructureTransform(
                pocketRef.artifactId(),
                receptorRef.artifactId(),
                transform,
                receptorPoints.size(),
                rmsd,
                METHOD,
                rmsd <= MAX_VALIDATED_RMSD_ANGSTROMS
                        ? "VALIDATED"
                        : "REJECTED_RMSD"
        );

        if (rmsd > MAX_VALIDATED_RMSD_ANGSTROMS) {
            return new CoordinateProvenance(
                    receptorRef,
                    pocketRef,
                    CoordinateCompatibility.INCOMPATIBLE,
                    record,
                    false,
                    "INVALID_MIXED_FRAME: receptor artifact "
                            + receptorRef.artifactId()
                            + sourceReference(receptorPdbqt)
                            + " and pocket structure artifact "
                            + pocketRef.artifactId() + " ("
                            + describe(pocketRef)
                            + ") are in different coordinate frames"
                            + " (CA fit RMSD " + format(rmsd)
                            + " A over " + record.matchedPairs()
                            + " pairs exceeds "
                            + MAX_VALIDATED_RMSD_ANGSTROMS
                            + " A); sphere metrics NOT_AVAILABLE;"
                            + " contact-residue coverage is"
                            + " frame-independent and still computed"
            );
        }

        boolean identity = transform.isIdentity(
                IDENTITY_TRANSLATION_ANGSTROMS)
                && rmsd <= IDENTITY_RMSD_ANGSTROMS;
        return new CoordinateProvenance(
                receptorRef,
                pocketRef,
                CoordinateCompatibility.VALIDATED_TRANSFORM,
                record,
                true,
                identity
                        ? "different artifacts, same frame within"
                        + " tolerance (CA fit RMSD " + format(rmsd)
                        + " A over " + record.matchedPairs()
                        + " pairs); sphere metrics AVAILABLE"
                        : "VALIDATED_TRANSFORM: pocket spheres moved"
                        + " into the receptor frame (CA fit RMSD "
                        + format(rmsd) + " A, translation "
                        + format(transform.translation().distance(
                                new Point3D(0.0, 0.0, 0.0)))
                        + " A, over " + record.matchedPairs()
                        + " pairs); sphere metrics AVAILABLE"
                        + " (transformed)"
        );
    }

    /**
     * Whether the validated transform moves coordinates (false when
     * the fit is within identity tolerance — spheres are used as-is).
     */
    static boolean requiresTransform(CoordinateProvenance provenance) {
        if (provenance.transform() == null
                || provenance.transform().transform() == null) {
            return false;
        }
        return !provenance.transform().transform()
                .isIdentity(IDENTITY_TRANSLATION_ANGSTROMS);
    }

    /**
     * Matched CA positions of the two structures, paired by chain and
     * residue number in sorted order.
     */
    private static void matchedCalphas(
            Structure receptor,
            Structure pocketStructure,
            List<Point3D> receptorPoints,
            List<Point3D> pocketPoints
    ) {
        Map<String, Point3D> pocketCalphas = calphas(pocketStructure);
        Map<String, Point3D> receptorCalphas = calphas(receptor);
        List<String> common = new ArrayList<>(receptorCalphas.keySet());
        common.retainAll(pocketCalphas.keySet());
        common.sort(null);
        for (String key : common) {
            receptorPoints.add(receptorCalphas.get(key));
            pocketPoints.add(pocketCalphas.get(key));
        }
    }

    private static Map<String, Point3D> calphas(Structure structure) {
        Map<String, Point3D> positions = new HashMap<>();
        for (Chain chain : structure.getChains()) {
            for (Residue residue : chain.residues()) {
                residue.findAtom("CA").ifPresent(atom ->
                        positions.putIfAbsent(
                                chain.id() + ":"
                                        + residue.getNumber(),
                                atom.getPosition()));
            }
        }
        return positions;
    }

    private Structure readStructure(Path path) throws IOException {
        String name = path.getFileName().toString()
                .toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdbqt")) {
            return PdbqtGaiaMapper.toStructure(pdbqtReader.read(path));
        }
        return structureReader.read(path);
    }

    private Path resolve(String storageLocation) {
        Path path = Path.of(storageLocation);
        return path.isAbsolute()
                ? path
                : artifactRoot.resolve(storageLocation);
    }

    /** Package-private: content hash for artifact matching. */
    static String sha256(Path path) {
        try {
            return HexFormat.of().formatHex(MessageDigest
                    .getInstance("SHA-256")
                    .digest(Files.readAllBytes(path)));
        } catch (IOException | NoSuchAlgorithmException exception) {
            return null;
        }
    }

    private static String modelVersion(String accession) {
        if (accession == null) {
            return null;
        }
        Matcher matcher = MODEL_VERSION.matcher(accession);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** The chemflow REMARK source chain of a receptor PDBQT, when
     *  recorded (for example the source artifact of a legacy model). */
    private static String sourceReference(Path receptorPdbqt) {
        try (var lines = Files.lines(receptorPdbqt)) {
            return lines.limit(10)
                    .map(REMARK_NAME::matcher)
                    .filter(Matcher::matches)
                    .map(matcher -> {
                        String name = matcher.group(1);
                        int slash = name.lastIndexOf('/');
                        return slash < 0
                                ? name
                                : name.substring(slash + 1);
                    })
                    .findFirst()
                    .map(name -> " (source artifact " + name + ")")
                    .orElse("");
        } catch (IOException exception) {
            return "";
        }
    }

    private static String describe(StructureArtifactRef ref) {
        StringBuilder description = new StringBuilder();
        if (ref.accession() != null) {
            description.append(ref.accession());
        }
        if (ref.modelVersion() != null) {
            description.append(" model ").append(ref.modelVersion());
        }
        return description.toString();
    }

    private static String shortHash(String sha256) {
        return sha256 == null
                ? "unavailable"
                : sha256.substring(0, 12) + "…";
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static double rmsd(
            List<Point3D> moved,
            List<Point3D> fixed
    ) {
        double sum = 0.0;
        for (int index = 0; index < fixed.size(); index++) {
            sum += fixed.get(index)
                    .distanceSquared(moved.get(index));
        }
        return Math.sqrt(sum / fixed.size());
    }
}
