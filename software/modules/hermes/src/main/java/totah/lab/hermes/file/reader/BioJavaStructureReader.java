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
import totah.lab.gaia.structure.AlternateLocationProvenance;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.ConnectivityMetadata;
import totah.lab.gaia.structure.ConnectivityProvenance;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.chemistry.BondOrder;
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
        Map<org.biojava.nbio.structure.Atom, AtomReference> atomReferences =
                new IdentityHashMap<>();
        List<org.biojava.nbio.structure.Atom> orderedAtoms = new ArrayList<>();

        for (org.biojava.nbio.structure.Chain bioChain
                : bioStructure.getChains()) {

            String chainId = chainIdentifier(bioChain);
            List<Residue> residues = residuesByChain.computeIfAbsent(
                    chainId,
                    ignored -> new ArrayList<>());

            for (Group group : bioChain.getAtomGroups()) {
                if (convertedGroups.add(group)) {
                    List<org.biojava.nbio.structure.Atom> selectedAtoms =
                            representativeAtoms(group);
                    residues.add(buildResidue(group, selectedAtoms));
                    ResidueNumber number = Objects.requireNonNull(
                            group.getResidueNumber(), "group residue number");
                    char insertionCode = number.getInsCode() == null
                            ? ' '
                            : number.getInsCode();
                    for (org.biojava.nbio.structure.Atom atom : selectedAtoms) {
                        atomReferences.put(atom, new AtomReference(
                                chainId, number.getSeqNum(), insertionCode, atom.getName()));
                        orderedAtoms.add(atom);
                    }
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

        ConnectivityImport connectivity = importConnectivity(orderedAtoms, atomReferences);
        if (Files.isRegularFile(path)
                && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdb")) {
            connectivity = importPdbConect(path, orderedAtoms, atomReferences, connectivity);
        }
        Structure structure = new Structure(
                chains, connectivity.bonds(), connectivity.metadata());

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

    private Residue buildResidue(
            Group group,
            List<org.biojava.nbio.structure.Atom> selectedAtoms) {
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
                .atoms(buildAtoms(group, selectedAtoms))
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

    private List<Atom> buildAtoms(
            Group group,
            List<org.biojava.nbio.structure.Atom> selectedAtoms) {
        if (selectedAtoms.isEmpty()) {

            return List.of();
        }

        return selectedAtoms.stream()
                .map(atom -> buildAtom(atom, group.hasAltLoc()))
                .toList();
    }

    private Atom buildAtom(
            org.biojava.nbio.structure.Atom bioAtom,
            boolean alternativesPresent) {

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
                .alternateLocationProvenance(
                        new AlternateLocationProvenance(
                                normalizeAlternateLocation(bioAtom.getAltLoc()),
                                alternativesPresent))
                .element(
                        bioElement == null
                                ? null
                                : Element.fromSymbol(
                                bioElement.name()))
                .build();
    }

    private char normalizeAlternateLocation(Character alternateLocation) {
        return alternateLocation == null ? ' ' : alternateLocation;
    }

    private ConnectivityImport importConnectivity(
            List<org.biojava.nbio.structure.Atom> orderedAtoms,
            Map<org.biojava.nbio.structure.Atom, AtomReference> atomReferences) {
        Set<org.biojava.nbio.structure.Bond> visited =
                Collections.newSetFromMap(new IdentityHashMap<>());
        Map<String, Bond> imported = new LinkedHashMap<>();
        List<String> diagnostics = new ArrayList<>();
        int sourceBondCount = 0;
        int unmappedBondCount = 0;

        // Iterate in structure insertion order so the bond list is deterministic.
        for (org.biojava.nbio.structure.Atom atom : orderedAtoms) {
            List<org.biojava.nbio.structure.Bond> sourceBonds = atom.getBonds();
            if (sourceBonds == null) continue;
            for (org.biojava.nbio.structure.Bond sourceBond : sourceBonds) {
                if (sourceBond == null || !visited.add(sourceBond)) continue;
                sourceBondCount++;
                AtomReference atom1 = atomReferences.get(sourceBond.getAtomA());
                AtomReference atom2 = atomReferences.get(sourceBond.getAtomB());
                if (atom1 == null || atom2 == null) {
                    unmappedBondCount++;
                    diagnostics.add("Unmapped BioJava bond endpoint: " + sourceBond);
                    continue;
                }
                Bond bond = new Bond(
                        atom1, atom2, mapBondOrder(sourceBond.getBondOrder(), diagnostics));
                String endpointKey = bond.atom1() + "|" + bond.atom2();
                Bond previous = imported.putIfAbsent(endpointKey, bond);
                if (previous != null) {
                    diagnostics.add("Duplicate source bond: " + bond);
                    if (previous.order() != bond.order()) {
                        diagnostics.add("Conflicting source bond order retained as UNKNOWN: " + bond);
                        imported.put(endpointKey,
                                new Bond(bond.atom1(), bond.atom2(), BondOrder.UNKNOWN));
                    }
                }
            }
        }

        ConnectivityProvenance provenance;
        if (sourceBondCount == 0) {
            provenance = ConnectivityProvenance.ABSENT;
        } else if (unmappedBondCount > 0) {
            provenance = ConnectivityProvenance.PARTIAL;
            diagnostics.add("Connectivity import is partial: " + unmappedBondCount
                    + " source bond(s) could not be mapped.");
        } else {
            provenance = ConnectivityProvenance.EXPLICIT;
        }
        return new ConnectivityImport(
                List.copyOf(imported.values()),
                new ConnectivityMetadata(provenance, diagnostics));
    }

    private ConnectivityImport importPdbConect(
            Path path,
            List<org.biojava.nbio.structure.Atom> orderedAtoms,
            Map<org.biojava.nbio.structure.Atom, AtomReference> atomReferences,
            ConnectivityImport existing) throws IOException {
        Map<Integer, AtomReference> bySerial = new LinkedHashMap<>();
        for (org.biojava.nbio.structure.Atom atom : orderedAtoms) {
            bySerial.putIfAbsent(atom.getPDBserial(), atomReferences.get(atom));
        }
        Map<String, Bond> imported = new LinkedHashMap<>();
        for (Bond bond : existing.bonds()) {
            imported.put(bond.atom1() + "|" + bond.atom2(), bond);
        }
        List<String> diagnostics = new ArrayList<>(existing.metadata().diagnostics());
        int records = 0;
        int unmapped = 0;
        for (String line : Files.readAllLines(path)) {
            if (!line.startsWith("CONECT")) continue;
            records++;
            String[] fields = line.substring(6).trim().split("\\s+");
            if (fields.length < 2) continue;
            Integer sourceSerial = parseSerial(fields[0], diagnostics);
            if (sourceSerial == null) continue;
            for (int index = 1; index < fields.length; index++) {
                Integer targetSerial = parseSerial(fields[index], diagnostics);
                if (targetSerial == null || sourceSerial.equals(targetSerial)) continue;
                AtomReference atom1 = bySerial.get(sourceSerial);
                AtomReference atom2 = bySerial.get(targetSerial);
                if (atom1 == null || atom2 == null) {
                    unmapped++;
                    diagnostics.add("Unmapped PDB CONECT endpoint: "
                            + sourceSerial + "-" + targetSerial);
                    continue;
                }
                Bond bond = new Bond(atom1, atom2, BondOrder.UNKNOWN);
                imported.putIfAbsent(bond.atom1() + "|" + bond.atom2(), bond);
            }
        }
        if (records == 0) return existing;
        // A partial earlier import stays partial even when every CONECT maps.
        ConnectivityProvenance provenance =
                existing.metadata().provenance() == ConnectivityProvenance.PARTIAL
                        || unmapped > 0
                ? ConnectivityProvenance.PARTIAL
                : ConnectivityProvenance.EXPLICIT;
        if (unmapped > 0) {
            diagnostics.add("PDB CONECT import is partial: " + unmapped
                    + " endpoint pair(s) could not be mapped.");
        }
        return new ConnectivityImport(
                List.copyOf(imported.values()),
                new ConnectivityMetadata(provenance, diagnostics));
    }

    private Integer parseSerial(String value, List<String> diagnostics) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            diagnostics.add("Malformed PDB CONECT serial: " + value);
            return null;
        }
    }

    private BondOrder mapBondOrder(int sourceOrder, List<String> diagnostics) {
        return switch (sourceOrder) {
            case 1 -> BondOrder.SINGLE;
            case 2 -> BondOrder.DOUBLE;
            case 3 -> BondOrder.TRIPLE;
            case 4 -> BondOrder.AROMATIC;
            default -> {
                diagnostics.add("Unsupported BioJava bond order " + sourceOrder
                        + " preserved as UNKNOWN.");
                yield BondOrder.UNKNOWN;
            }
        };
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

    private record ConnectivityImport(
            List<Bond> bonds,
            ConnectivityMetadata metadata) {
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
