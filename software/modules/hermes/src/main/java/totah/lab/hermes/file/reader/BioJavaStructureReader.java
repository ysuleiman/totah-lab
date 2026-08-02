package totah.lab.hermes.file.reader;

import totah.lab.gaia.classification.ClassificationSource;
import totah.lab.gaia.classification.ResidueClassification;
import totah.lab.gaia.structure.Structure;

import org.biojava.nbio.structure.Group;
import org.biojava.nbio.structure.ResidueNumber;
import org.biojava.nbio.structure.chem.ChemComp;
import org.biojava.nbio.structure.chem.ChemCompGroupFactory;
import org.biojava.nbio.structure.chem.ChemCompProvider;
import org.biojava.nbio.structure.io.PDBFileReader;
import org.biojava.nbio.structure.io.cif.CifStructureConverter;
import totah.lab.gaia.classification.ResidueClassificationEvidence;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.structure.Residue;
import totah.lab.hermes.structure.StructureReaderOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class BioJavaStructureReader
        implements StructureReader {

    private static final Object CHEM_COMP_PROVIDER_LOCK =
            new Object();

    private final StructureReaderOptions options;

    public BioJavaStructureReader() {
        this(StructureReaderOptions.defaults());
    }

    public BioJavaStructureReader(
            StructureReaderOptions options) {

        this.options = Objects.requireNonNull(
                options,
                "options");
    }

    @Override
    public Structure read(Path path) throws IOException {
        validatePath(path);

        ChemCompProvider provider =
                ChemCompProviders.create(
                        options.onlineCcdLookup(),
                        options.ccdCacheDirectory());

        synchronized (CHEM_COMP_PROVIDER_LOCK) {
            ChemCompProvider previousProvider =
                    ChemCompGroupFactory.getChemCompProvider();

            ChemCompGroupFactory.setChemCompProvider(provider);
            ChemCompGroupFactory.clearCache();

            try {
                return readWithConfiguredProvider(path);
            } finally {
                ChemCompGroupFactory.setChemCompProvider(
                        previousProvider);

                ChemCompGroupFactory.clearCache();
            }
        }
    }

    private Structure readWithConfiguredProvider(
            Path path) throws IOException {

        org.biojava.nbio.structure.Structure bioStructure =
                readBioJavaStructure(path);

        return convertStructure(bioStructure, path);
    }

    Structure convertStructure(
            org.biojava.nbio.structure.Structure bioStructure,
            Path path) throws IOException {
        try {
            return convertStructureUnchecked(bioStructure, path);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new IOException(
                    "Failed to convert structure from " + path + ": "
                            + exception.getMessage(),
                    exception);
        }
    }

    private Structure convertStructureUnchecked(
            org.biojava.nbio.structure.Structure bioStructure,
            Path path) throws IOException {

        Map<String, List<Residue>> residuesByChain =
                new LinkedHashMap<>();

        Set<Group> convertedGroups = Collections.newSetFromMap(
                new IdentityHashMap<>());

        for (org.biojava.nbio.structure.Chain bioChain
                : bioStructure.getChains()) {

            String chainId = chainIdentifier(bioChain);
            List<Residue> residues = residuesByChain.computeIfAbsent(
                    chainId,
                    ignored -> new ArrayList<>());

            for (Group group : bioChain.getAtomGroups()) {
                if (convertedGroups.add(group)) {
                    residues.add(buildResidue(group));
                }
            }
        }

        List<Chain> chains = residuesByChain.entrySet()
                .stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(entry -> new Chain(
                        entry.getKey(),
                        entry.getValue()))
                .toList();

        Structure structure = new Structure(chains);

        if (structure.getResidueCount() == 0) {
            throw new IOException(
                    "No residues loaded from " + path);
        }

        return structure;
    }

    private org.biojava.nbio.structure.Structure
    readBioJavaStructure(Path path) throws IOException {

        String fileName = path
                .getFileName()
                .toString()
                .toLowerCase(Locale.ROOT);

        if (fileName.endsWith(".pdb")) {
            return new PDBFileReader()
                    .getStructure(path.toFile());
        }

        if (fileName.endsWith(".cif")
                || fileName.endsWith(".mmcif")) {

            return CifStructureConverter.fromPath(path);
        }

        throw new IOException(
                "Unsupported structure format: "
                        + fileName);
    }

    private Residue buildResidue(Group group) {
        Objects.requireNonNull(group, "group");

        ResidueNumber residueNumber =
                Objects.requireNonNull(
                        group.getResidueNumber(),
                        "group residue number");

        ResidueClassificationEvidence evidence =
                extractClassificationEvidence(group);

        return Residue.builder()
                .name(group.getPDBName().trim())
                .number(residueNumber.getSeqNum())
                .insertionCode(
                        normalizeInsertionCode(
                                residueNumber.getInsCode()))
                .classificationEvidence(
                        List.of(evidence))
                .atoms(buildAtoms(group))
                .build();
    }

    private ResidueClassificationEvidence extractClassificationEvidence(
            Group group) {

        Objects.requireNonNull(group, "group");

        ChemComp chemComp = group.getChemComp();

        if (chemComp == null || chemComp.isEmpty()) {
            return ResidueClassificationEvidence.of(
                    ResidueClassification.UNKNOWN,
                    ClassificationSource.BIOJAVA,
                    "ChemComp unavailable");
        }

        if (group.isWater()) {
            return ResidueClassificationEvidence.of(
                    ResidueClassification.WATER,
                    ClassificationSource.CCD);
        }

        if (chemComp.isStandard()) {
            return ResidueClassificationEvidence.of(
                    ResidueClassification.STANDARD_AMINO_ACID,
                    ClassificationSource.CCD);
        }

        if (group.isPolymeric()) {
            return ResidueClassificationEvidence.of(
                    ResidueClassification.POLYMER,
                    ClassificationSource.CCD);
        }

        return ResidueClassificationEvidence.of(
                ResidueClassification.HETERO,
                ClassificationSource.CCD,
                chemComp.getId());
    }

    private List<Atom> buildAtoms(Group group) {
        if (group.getAtoms() == null
                || group.getAtoms().isEmpty()) {

            return List.of();
        }

        return representativeAtoms(group)
                .stream()
                .map(this::buildAtom)
                .toList();
    }

    private Atom buildAtom(
            org.biojava.nbio.structure.Atom bioAtom) {

        org.biojava.nbio.structure.Element bioElement =
                bioAtom.getElement();

        return Atom.builder()
                .pdbSerial(bioAtom.getPDBserial())
                .name(bioAtom.getName().trim())
                .position(
                        new Point3D(
                                bioAtom.getX(),
                                bioAtom.getY(),
                                bioAtom.getZ()))
                .charge(0.0)
                .amberType(null)
                .occupancy(bioAtom.getOccupancy())
                .bFactor(bioAtom.getTempFactor())
                .element(
                        bioElement == null
                                ? null
                                : Element.fromSymbol(
                                bioElement.name()))
                .build();
    }

    private List<org.biojava.nbio.structure.Atom>
    representativeAtoms(Group group) {

        Map<String, AtomCandidate> candidates =
                new LinkedHashMap<>();

        int order = collectCandidates(
                candidates,
                group.getAtoms(),
                0);

        if (group.hasAltLoc()) {
            for (Group alternateGroup : group.getAltLocs()) {
                order = collectCandidates(
                        candidates,
                        alternateGroup.getAtoms(),
                        order);
            }
        }

        return candidates.values()
                .stream()
                .sorted(
                        Comparator.comparingInt(
                                AtomCandidate::order))
                .map(AtomCandidate::atom)
                .toList();
    }

    private int collectCandidates(
            Map<String, AtomCandidate> candidates,
            List<org.biojava.nbio.structure.Atom> atoms,
            int order) {

        if (atoms == null) {
            return order;
        }

        for (org.biojava.nbio.structure.Atom atom : atoms) {
            String atomName =
                    atom.getName() == null
                            ? ""
                            : atom.getName().trim();

            AtomCandidate candidate =
                    new AtomCandidate(atom, order++);

            AtomCandidate previous =
                    candidates.get(atomName);

            if (previous == null
                    || isBetterAltLoc(
                    candidate.atom(),
                    previous.atom())) {

                candidates.put(
                        atomName,
                        previous == null
                                ? candidate
                                : new AtomCandidate(
                                candidate.atom(),
                                previous.order()));
            }
        }

        return order;
    }

    private boolean isBetterAltLoc(
            org.biojava.nbio.structure.Atom candidate,
            org.biojava.nbio.structure.Atom current) {

        int occupancyComparison =
                Float.compare(
                        candidate.getOccupancy(),
                        current.getOccupancy());

        if (occupancyComparison != 0) {
            return occupancyComparison > 0;
        }

        return altLocRank(candidate.getAltLoc())
                > altLocRank(current.getAltLoc());
    }

    private int altLocRank(Character altLoc) {
        if (altLoc != null
                && Character.toUpperCase(altLoc) == 'A') {
            return 2;
        }

        if (altLoc == null
                || altLoc == ' '
                || altLoc == '\0') {
            return 1;
        }

        return 0;
    }

    private String chainIdentifier(
            org.biojava.nbio.structure.Chain chain) {

        String id = chain.getName();

        if (id == null || id.isBlank()) {
            id = chain.getId();
        }

        if (id == null || id.isBlank()) {
            throw new IllegalStateException(
                    "Encountered a chain without an identifier.");
        }

        return id.trim();
    }

    private Character normalizeInsertionCode(
            Character insertionCode) {

        if (insertionCode == null
                || Character.isWhitespace(insertionCode)
                || insertionCode == '\0') {

            return null;
        }

        return insertionCode;
    }

    private void validatePath(Path path)
            throws IOException {

        Objects.requireNonNull(path, "path");

        if (!Files.exists(path)) {
            throw new IOException(
                    "Structure file does not exist: " + path);
        }

        if (!Files.isRegularFile(path)) {
            throw new IOException(
                    "Structure path is not a regular file: "
                            + path);
        }

        if (!Files.isReadable(path)) {
            throw new IOException(
                    "Structure file is not readable: " + path);
        }
    }

    private record AtomCandidate(
            org.biojava.nbio.structure.Atom atom,
            int order) {
    }

    @Override
    public boolean supports(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }

        String fileName = path.getFileName()
                .toString()
                .toLowerCase(Locale.ROOT);

        return fileName.endsWith(".pdb")
                || fileName.endsWith(".cif")
                || fileName.endsWith(".mmcif");
    }
}
