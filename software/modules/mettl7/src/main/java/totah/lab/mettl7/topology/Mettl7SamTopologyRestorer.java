package totah.lab.mettl7.topology;

import com.actelion.research.chem.Canonizer;
import com.actelion.research.chem.MolfileParser;
import com.actelion.research.chem.Molecule;
import com.actelion.research.chem.StereoMolecule;
import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.chemistry.ChemicalBond;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.ConnectivityMetadata;
import totah.lab.gaia.structure.ConnectivityProvenance;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.pdb.reader.PdbReader;
import totah.lab.hermes.file.pdbqt.PdbqtAtom;
import totah.lab.hermes.file.pdbqt.PdbqtFile;
import totah.lab.hermes.file.pdbqt.PdbqtGaiaMapper;
import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;
import totah.lab.hermes.file.sdf.SdfLigand;
import totah.lab.hermes.file.sdf.reader.SdfLigandReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * METTL7-specific, coordinate-preserving reconstruction of a receptor from
 * an independently validated protein topology and the canonical CCD SAM graph.
 * Coordinates are always taken from the supplied docking receptor PDBQT.
 */
public final class Mettl7SamTopologyRestorer {
    public static final String VERSION = "METTL7_SAM_TOPOLOGY_RESTORER_V1";
    public static final String REJECTED_TOPOLOGY_SOURCE_DIRECT_USE =
            "REJECTED_TOPOLOGY_SOURCE_DIRECT_USE";
    private static final double COORDINATE_MATCH_TOLERANCE_ANGSTROM = 0.0011;

    public RestoredReceptor restore(
            Path canonicalSamSdf,
            Path preparedSamSdf,
            Path dockingReceptorPdbqt,
            Path proteinTopologyPdb) throws IOException {
        Objects.requireNonNull(canonicalSamSdf, "canonicalSamSdf");
        Objects.requireNonNull(preparedSamSdf, "preparedSamSdf");
        Objects.requireNonNull(dockingReceptorPdbqt, "dockingReceptorPdbqt");
        Objects.requireNonNull(proteinTopologyPdb, "proteinTopologyPdb");

        SdfLigand canonical = new SdfLigandReader().readModel(canonicalSamSdf);
        SdfLigand prepared = new SdfLigandReader().readModel(preparedSamSdf);
        SamValidation chemistry = validateCanonicalSam(canonical);
        StereoValidation stereo = validateStereoIdentity(canonicalSamSdf, preparedSamSdf);
        Mapping sdfMapping = mapIsomorphicGraphs(canonical, prepared);

        PdbqtFile pdbqt = new PdbqtReader().read(dockingReceptorPdbqt);
        if (pdbqt.models().size() != 1) {
            throw new IOException("Docking receptor must contain exactly one model");
        }
        List<PdbqtAtom> receptorAtoms = pdbqt.models().getFirst().atoms();
        List<PdbqtAtom> targetSam = receptorAtoms.stream()
                .filter(Mettl7SamTopologyRestorer::isSam)
                .toList();
        if (targetSam.size() != prepared.atomCount()) {
            throw new IOException("Missing SAM atom: prepared=" + prepared.atomCount()
                    + " receptor=" + targetSam.size());
        }
        Map<Integer, PdbqtAtom> targetByPrepared = mapPreparedCoordinates(prepared, targetSam);

        Structure targetCoordinates = PdbqtGaiaMapper.toStructure(pdbqt);
        Structure proteinTopology = new PdbReader().read(proteinTopologyPdb);
        ProteinTransfer protein = transferProteinTopology(proteinTopology, targetCoordinates);
        SamTransfer sam = transferSam(canonical, sdfMapping, targetByPrepared);

        List<Chain> chains = new ArrayList<>(protein.chains());
        chains.add(new Chain(sam.chainId(), List.of(sam.residue())));
        List<Bond> bonds = new ArrayList<>(protein.bonds());
        bonds.addAll(sam.bonds());
        Structure restored = new Structure(chains, bonds,
                new ConnectivityMetadata(ConnectivityProvenance.EXPLICIT,
                        List.of("PROTEIN_TOPOLOGY_TRANSFERRED_BY_EXPLICIT_ATOM_IDENTITY",
                                "SAM_TOPOLOGY_TRANSFERRED_FROM_CANONICAL_CCD_GRAPH",
                                "ZERO_PROTEIN_SAM_CROSS_BONDS")));

        CoordinateProof coordinates = coordinateProof(targetCoordinates, restored, sdfMapping,
                targetByPrepared);
        if (coordinates.movedAtomCount() != 0 || coordinates.maximumDisplacementAngstrom() != 0.0
                || coordinates.rmsdAngstrom() != 0.0) {
            throw new IOException("Coordinate-preserving transfer failed");
        }
        String graphHash = topologyHash(restored);
        Receipt receipt = new Receipt(
                VERSION,
                sha256(canonicalSamSdf), sha256(preparedSamSdf),
                sha256(dockingReceptorPdbqt), sha256(proteinTopologyPdb),
                REJECTED_TOPOLOGY_SOURCE_DIRECT_USE,
                chemistry.atomCount(), chemistry.heavyAtomCount(), chemistry.explicitHydrogens(),
                chemistry.formalCharge(), chemistry.samGraphHash(), stereo.oclIdCode(), stereo.status(),
                sdfMapping.status(), sdfMapping.sourceToTarget(), sdfMapping.symmetryGroups(),
                sdfMapping.mappingHash(), protein.mappingHash(), protein.atomCount(),
                protein.bonds().size(), sam.bonds().size(), 0,
                coordinates.preCoordinateHash(), coordinates.postCoordinateHash(),
                coordinates.rmsdAngstrom(), coordinates.maximumDisplacementAngstrom(),
                coordinates.movedAtomCount(), graphHash,
                "SAM_COFACTOR_SEPARATE_FROM_PROTEIN",
                "SURFDIFF_PROTEIN_ONLY_REQUIRED");
        return new RestoredReceptor(restored, receipt);
    }

    private static StereoValidation validateStereoIdentity(Path canonical, Path prepared) throws IOException {
        StereoMolecule source = parseOclMolfile(canonical);
        StereoMolecule target = parseOclMolfile(prepared);
        source.ensureHelperArrays(Molecule.cHelperCIP);
        target.ensureHelperArrays(Molecule.cHelperCIP);
        String sourceId = new Canonizer(source).getIDCode();
        String targetId = new Canonizer(target).getIDCode();
        if (!sourceId.equals(targetId)) {
            String sourceNoStereo = new Canonizer(source, Canonizer.NEGLECT_ANY_STEREO_INFORMATION).getIDCode();
            String targetNoStereo = new Canonizer(target, Canonizer.NEGLECT_ANY_STEREO_INFORMATION).getIDCode();
            throw new IOException(sourceNoStereo.equals(targetNoStereo)
                    ? "SAM stereochemistry mismatch" : "SAM OCL chemical identity mismatch");
        }
        return new StereoValidation(sourceId, "STEREO_AWARE_OCL_IDCODE_MATCH");
    }

    private static StereoMolecule parseOclMolfile(Path path) throws IOException {
        StereoMolecule molecule = new StereoMolecule();
        MolfileParser parser = new MolfileParser(MolfileParser.MODE_KEEP_HYDROGEN_MAP);
        parser.setAssumeChiralTrue(true);
        if (!parser.parse(molecule, path.toFile())) throw new IOException("OCL failed to parse SAM molfile " + path);
        return molecule;
    }

    private static SamValidation validateCanonicalSam(SdfLigand sam) throws IOException {
        List<Atom> atoms = atoms(sam);
        if (atoms.size() != 49 || atoms.stream().filter(Atom::isHeavyAtom).count() != 27
                || sam.bonds().size() != 51 || sam.fragments().size() != 1) {
            throw new IOException("Canonical SAM count/connectivity mismatch");
        }
        int formalCharge = sam.formalCharges().stream().mapToInt(Integer::intValue).sum();
        if (formalCharge != 0) throw new IOException("Canonical SAM formal charge mismatch: " + formalCharge);
        List<Integer> sulfur = indicesOfElement(atoms, "S");
        if (sulfur.size() != 1 || sam.formalCharges().get(sulfur.getFirst()) != 1) {
            throw new IOException("Canonical SAM lacks one +1 sulfonium sulfur");
        }
        int s = sulfur.getFirst();
        List<Integer> sNeighbors = neighbors(sam, s);
        if (sNeighbors.size() != 3 || sNeighbors.stream().anyMatch(i -> !"C".equals(element(atoms.get(i))))) {
            throw new IOException("SAM sulfonium must have three carbon substituents");
        }
        long methylNeighbors = sNeighbors.stream().filter(i ->
                neighbors(sam, i).stream().filter(j -> "H".equals(element(atoms.get(j)))).count() == 3).count();
        if (methylNeighbors != 1) throw new IOException("SAM S-methyl connectivity is not unique");
        long negativeOxygen = indicesOfElement(atoms, "O").stream()
                .filter(i -> sam.formalCharges().get(i) == -1).count();
        if (negativeOxygen != 1) throw new IOException("SAM carboxylate/protonation state mismatch");
        validateBondTable(sam);
        return new SamValidation(atoms.size(), 27, 22, formalCharge, graphHash(sam));
    }

    private static void validateBondTable(SdfLigand ligand) throws IOException {
        Set<String> endpoints = new LinkedHashSet<>();
        for (ChemicalBond bond : ligand.bonds()) {
            String key = Math.min(bond.atomIndexA(), bond.atomIndexB()) + ":"
                    + Math.max(bond.atomIndexA(), bond.atomIndexB());
            if (!endpoints.add(key)) throw new IOException("Duplicate SAM bond " + key);
            if (bond.order() == BondOrder.UNKNOWN) throw new IOException("Unknown SAM bond order " + key);
        }
    }

    private static Mapping mapIsomorphicGraphs(SdfLigand source, SdfLigand target) throws IOException {
        if (source.atomCount() != target.atomCount() || source.bonds().size() != target.bonds().size()) {
            throw new IOException("SAM graph size mismatch");
        }
        List<String> sourceColors = refinedColors(source);
        List<String> targetColors = refinedColors(target);
        Map<String, List<Integer>> sourceGroups = groups(sourceColors);
        Map<String, List<Integer>> targetGroups = groups(targetColors);
        if (!sourceGroups.keySet().equals(targetGroups.keySet())) {
            throw new IOException("SAM element/charge/connectivity/bond-order mismatch");
        }
        Map<Integer, List<Integer>> candidates = new TreeMap<>();
        List<List<Integer>> symmetry = new ArrayList<>();
        for (String color : sourceGroups.keySet().stream().sorted().toList()) {
            List<Integer> left = sourceGroups.get(color).stream().sorted().toList();
            List<Integer> right = targetGroups.get(color).stream().sorted().toList();
            if (left.size() != right.size()) throw new IOException("SAM symmetry cardinality mismatch");
            if (left.size() > 1) symmetry.add(left);
            for (int sourceAtom : left) candidates.put(sourceAtom, right);
        }
        Map<Integer, Integer> mapping = findIsomorphism(source, target, candidates);
        validateMappedBonds(source, target, mapping);
        String canonical = mapping + "\n" + symmetry + "\n" + graphHash(source);
        return new Mapping(symmetry.isEmpty() ? "MAPPED_UNIQUE" : "SYMMETRY_EQUIVALENT",
                Map.copyOf(mapping), symmetry.stream().map(List::copyOf).toList(), sha256(canonical));
    }

    private static Map<Integer, Integer> findIsomorphism(SdfLigand source, SdfLigand target,
            Map<Integer, List<Integer>> candidates) throws IOException {
        List<Integer> order = candidates.keySet().stream()
                .sorted(Comparator.comparingInt((Integer atom) -> candidates.get(atom).size())
                        .thenComparing((Integer atom) -> -neighbors(source, atom).size())
                        .thenComparingInt(Integer::intValue))
                .toList();
        Map<Integer, Integer> mapping = new TreeMap<>();
        if (!extendIsomorphism(source, target, candidates, order, 0, mapping,
                new LinkedHashSet<>(), chemicalBondMap(source), chemicalBondMap(target))) {
            throw new IOException("No chemistry-consistent SAM graph isomorphism");
        }
        return Map.copyOf(mapping);
    }

    private static boolean extendIsomorphism(SdfLigand source, SdfLigand target,
            Map<Integer, List<Integer>> candidates, List<Integer> order, int cursor,
            Map<Integer, Integer> mapping, Set<Integer> used,
            Map<String, BondOrder> sourceBonds, Map<String, BondOrder> targetBonds) {
        if (cursor == order.size()) return true;
        int sourceAtom = order.get(cursor);
        for (int targetAtom : candidates.get(sourceAtom)) {
            if (used.contains(targetAtom)) continue;
            boolean consistent = true;
            for (Map.Entry<Integer, Integer> mapped : mapping.entrySet()) {
                BondOrder sourceOrder = sourceBonds.get(pair(sourceAtom, mapped.getKey()));
                BondOrder targetOrder = targetBonds.get(pair(targetAtom, mapped.getValue()));
                if (!Objects.equals(sourceOrder, targetOrder)) { consistent = false; break; }
            }
            if (!consistent) continue;
            mapping.put(sourceAtom, targetAtom); used.add(targetAtom);
            if (extendIsomorphism(source, target, candidates, order, cursor + 1,
                    mapping, used, sourceBonds, targetBonds)) return true;
            mapping.remove(sourceAtom); used.remove(targetAtom);
        }
        return false;
    }

    private static List<String> refinedColors(SdfLigand ligand) {
        List<Atom> atoms = atoms(ligand);
        List<String> colors = new ArrayList<>();
        for (int i = 0; i < atoms.size(); i++) {
            colors.add(element(atoms.get(i)) + ":" + ligand.formalCharges().get(i)
                    + ":" + neighbors(ligand, i).size());
        }
        for (int round = 0; round < atoms.size(); round++) {
            List<String> next = new ArrayList<>();
            for (int i = 0; i < atoms.size(); i++) {
                List<String> environment = new ArrayList<>();
                for (ChemicalBond bond : ligand.bonds()) {
                    BondOrder order = effectiveBondOrder(ligand, bond);
                    if (bond.atomIndexA() == i) environment.add(order + ":" + colors.get(bond.atomIndexB()));
                    if (bond.atomIndexB() == i) environment.add(order + ":" + colors.get(bond.atomIndexA()));
                }
                environment.sort(String::compareTo);
                next.add(colors.get(i) + "|" + environment);
            }
            List<String> compressed = compress(next);
            if (compressed.equals(colors)) break;
            colors = compressed;
        }
        return List.copyOf(colors);
    }

    private static List<String> compress(List<String> values) {
        Map<String, Integer> rank = new TreeMap<>();
        values.stream().distinct().sorted().forEach(v -> rank.put(v, rank.size()));
        return values.stream().map(v -> Integer.toString(rank.get(v))).toList();
    }

    private static Map<String, List<Integer>> groups(List<String> colors) {
        Map<String, List<Integer>> result = new LinkedHashMap<>();
        for (int i = 0; i < colors.size(); i++) result.computeIfAbsent(colors.get(i), ignored -> new ArrayList<>()).add(i);
        return result;
    }

    private static void validateMappedBonds(SdfLigand source, SdfLigand target,
            Map<Integer, Integer> mapping) throws IOException {
        Map<String, BondOrder> targetBonds = chemicalBondMap(target);
        for (ChemicalBond bond : source.bonds()) {
            int a = mapping.get(bond.atomIndexA());
            int b = mapping.get(bond.atomIndexB());
            BondOrder order = targetBonds.get(pair(a, b));
            if (order != effectiveBondOrder(source, bond)) throw new IOException("Mapped SAM bond-order mismatch");
        }
    }

    private static Map<Integer, PdbqtAtom> mapPreparedCoordinates(
            SdfLigand prepared, List<PdbqtAtom> target) throws IOException {
        Map<Integer, PdbqtAtom> result = new TreeMap<>();
        Set<Integer> used = new LinkedHashSet<>();
        List<Atom> preparedAtoms = atoms(prepared);
        for (int index = 0; index < preparedAtoms.size(); index++) {
            Atom source = preparedAtoms.get(index);
            List<PdbqtAtom> matches = target.stream()
                    .filter(atom -> element(source).equalsIgnoreCase(atom.element()))
                    .filter(atom -> distance(source, atom) <= COORDINATE_MATCH_TOLERANCE_ANGSTROM)
                    .filter(atom -> !used.contains(atom.serial()))
                    .toList();
            if (matches.size() != 1) throw new IOException("Prepared-SAM coordinate mapping "
                    + (matches.isEmpty() ? "missing" : "duplicate") + " at atom " + index);
            result.put(index, matches.getFirst());
            used.add(matches.getFirst().serial());
        }
        if (used.size() != target.size()) throw new IOException("Incomplete prepared-SAM coordinate mapping");
        return Map.copyOf(result);
    }

    private static ProteinTransfer transferProteinTopology(
            Structure topology, Structure target) throws IOException {
        Map<AtomReference, Atom> targetAtoms = atomMap(target, false);
        Map<AtomReference, Atom> topologyAtoms = atomMap(topology, false);
        if (!targetAtoms.keySet().equals(topologyAtoms.keySet())) {
            Set<AtomReference> missing = new LinkedHashSet<>(targetAtoms.keySet());
            missing.removeAll(topologyAtoms.keySet());
            Set<AtomReference> extra = new LinkedHashSet<>(topologyAtoms.keySet());
            extra.removeAll(targetAtoms.keySet());
            throw new IOException("Protein topology coverage mismatch missing=" + missing + " extra=" + extra);
        }
        List<Chain> chains = new ArrayList<>();
        for (Chain chain : target.getChains()) {
            List<Residue> residues = chain.residues().stream().filter(r -> !"SAM".equals(r.getName())).toList();
            if (!residues.isEmpty()) chains.add(new Chain(chain.id(), residues));
        }
        List<Bond> bonds = new ArrayList<>(topology.getBonds().stream()
                .filter(b -> topologyAtoms.containsKey(b.atom1()) && topologyAtoms.containsKey(b.atom2()))
                .toList());
        restoreDocumentedNTerminalHydrogenBonds(targetAtoms, bonds);
        Set<AtomReference> covered = new LinkedHashSet<>();
        bonds.forEach(b -> { covered.add(b.atom1()); covered.add(b.atom2()); });
        if (!covered.containsAll(targetAtoms.keySet())) {
            Set<AtomReference> missing = new LinkedHashSet<>(targetAtoms.keySet()); missing.removeAll(covered);
            throw new IOException("Protein atoms lack residue-local/peptide connectivity: " + missing);
        }
        String mappingHash = sha256(targetAtoms.keySet().stream().sorted().toList().toString());
        return new ProteinTransfer(List.copyOf(chains), List.copyOf(bonds), targetAtoms.size(), mappingHash);
    }

    /** Restores the three standard N-terminal N-H bonds omitted by the frozen template. */
    private static void restoreDocumentedNTerminalHydrogenBonds(
            Map<AtomReference, Atom> atoms, List<Bond> bonds) throws IOException {
        for (Map.Entry<AtomReference, Atom> entry : atoms.entrySet()) {
            AtomReference hydrogen = entry.getKey();
            if (!Set.of("H1", "H2", "H3").contains(hydrogen.atomName())) continue;
            AtomReference nitrogen = new AtomReference(hydrogen.chainId(), hydrogen.residueNumber(),
                    hydrogen.insertionCode(), "N");
            if (!atoms.containsKey(nitrogen)) throw new IOException("N-terminal hydrogen lacks residue N: " + hydrogen);
            boolean exists = bonds.stream().anyMatch(b ->
                    (b.atom1().equals(hydrogen) && b.atom2().equals(nitrogen))
                            || (b.atom2().equals(hydrogen) && b.atom1().equals(nitrogen)));
            if (!exists) bonds.add(new Bond(hydrogen, nitrogen, BondOrder.SINGLE));
        }
    }

    private static SamTransfer transferSam(SdfLigand canonical, Mapping sourceToPrepared,
            Map<Integer, PdbqtAtom> targetByPrepared) {
        List<Atom> sourceAtoms = atoms(canonical);
        List<Atom> transferred = new ArrayList<>();
        for (int sourceIndex = 0; sourceIndex < sourceAtoms.size(); sourceIndex++) {
            PdbqtAtom target = targetByPrepared.get(sourceToPrepared.sourceToTarget().get(sourceIndex));
            transferred.add(Atom.builder().pdbSerial(target.serial())
                    .name(sourceAtoms.get(sourceIndex).getName())
                    .position(target.position()).charge(target.partialCharge())
                    .occupancy(target.occupancy() == null ? 1.0 : target.occupancy())
                    .bFactor(target.temperatureFactor() == null ? 0.0 : target.temperatureFactor())
                    .element(sourceAtoms.get(sourceIndex).getElement())
                    .autoDockType(target.autodockType()).build());
        }
        String targetChainId = targetByPrepared.values().iterator().next().chainId();
        final String chainId = targetChainId == null || targetChainId.isBlank() ? "L" : targetChainId;
        int residueNumber = targetByPrepared.values().iterator().next().residueNumber();
        Residue residue = new Residue("SAM", residueNumber, transferred);
        List<Bond> bonds = canonical.bonds().stream().map(b -> new Bond(
                new AtomReference(chainId, residueNumber, ' ', transferred.get(b.atomIndexA()).getName()),
                new AtomReference(chainId, residueNumber, ' ', transferred.get(b.atomIndexB()).getName()),
                b.order())).toList();
        return new SamTransfer(chainId, residue, bonds);
    }

    private static CoordinateProof coordinateProof(Structure before, Structure after,
            Mapping sourceToPrepared, Map<Integer, PdbqtAtom> targetByPrepared) throws IOException {
        List<String> pre = new ArrayList<>();
        for (Map.Entry<AtomReference, Atom> entry : atomMap(before, false).entrySet())
            pre.add(entry.getKey() + "=" + coordinate(entry.getValue()));
        for (int source = 0; source < sourceToPrepared.sourceToTarget().size(); source++) {
            PdbqtAtom atom = targetByPrepared.get(sourceToPrepared.sourceToTarget().get(source));
            pre.add("SAM:" + source + "=" + coordinate(atom));
        }
        List<String> post = new ArrayList<>();
        for (Map.Entry<AtomReference, Atom> entry : atomMap(after, true).entrySet())
            post.add(("SAM".equals(after.residue(new totah.lab.gaia.structure.ResidueId(
                    entry.getKey().chainId(), entry.getKey().residueNumber(), entry.getKey().insertionCode())).getName())
                    ? "SAM:" + samIndex(after, entry.getKey()) : entry.getKey().toString()) + "=" + coordinate(entry.getValue()));
        pre.sort(String::compareTo); post.sort(String::compareTo);
        if (!pre.equals(post)) throw new IOException("Coordinate identity hash domain mismatch");
        String hash = sha256(String.join("\n", pre));
        return new CoordinateProof(hash, hash, 0.0, 0.0, 0);
    }

    private static int samIndex(Structure structure, AtomReference reference) {
        Residue residue = structure.residue(new totah.lab.gaia.structure.ResidueId(
                reference.chainId(), reference.residueNumber(), reference.insertionCode()));
        for (int i = 0; i < residue.getAtoms().size(); i++) if (residue.getAtoms().get(i).getName().equals(reference.atomName())) return i;
        throw new IllegalStateException("SAM atom not found");
    }

    private static Map<AtomReference, Atom> atomMap(Structure structure, boolean includeSam) throws IOException {
        Map<AtomReference, Atom> result = new TreeMap<>();
        for (Chain chain : structure.getChains()) for (Residue residue : chain.residues()) {
            if (!includeSam && "SAM".equals(residue.getName())) continue;
            for (Atom atom : residue.getAtoms()) {
                AtomReference reference = new AtomReference(chain.id(), residue.getNumber(),
                        residue.getInsertionCode() == null ? ' ' : residue.getInsertionCode(), atom.getName());
                if (result.put(reference, atom) != null) throw new IOException("Duplicate atom identity " + reference);
            }
        }
        return result;
    }

    private static String topologyHash(Structure structure) {
        List<String> atoms = new ArrayList<>();
        for (Chain c : structure.getChains()) for (Residue r : c.residues()) for (Atom a : r.getAtoms())
            atoms.add(new AtomReference(c.id(), r.getNumber(), r.getInsertionCode() == null ? ' ' : r.getInsertionCode(), a.getName())
                    + ":" + element(a));
        atoms.sort(String::compareTo);
        List<String> bonds = structure.getBonds().stream().map(Object::toString).sorted().toList();
        return sha256(atoms + "\n" + bonds);
    }

    private static String graphHash(SdfLigand ligand) {
        List<String> labels = new ArrayList<>();
        List<Atom> atoms = atoms(ligand);
        List<String> colors = refinedColors(ligand);
        for (int i = 0; i < atoms.size(); i++) labels.add(colors.get(i) + ":" + element(atoms.get(i)) + ":" + ligand.formalCharges().get(i));
        labels.sort(String::compareTo);
        List<String> edges = ligand.bonds().stream().map(b -> {
            String a = colors.get(b.atomIndexA()), c = colors.get(b.atomIndexB());
            return (a.compareTo(c) <= 0 ? a + ":" + c : c + ":" + a) + ":" + effectiveBondOrder(ligand, b);
        }).sorted().toList();
        return sha256(labels + "\n" + edges);
    }

    private static Map<String, BondOrder> chemicalBondMap(SdfLigand ligand) {
        Map<String, BondOrder> map = new LinkedHashMap<>();
        ligand.bonds().forEach(b -> map.put(pair(b.atomIndexA(), b.atomIndexB()), effectiveBondOrder(ligand, b)));
        return map;
    }

    /** Normalizes alternate Kekule representations only for bonds proven to lie in a ring. */
    private static BondOrder effectiveBondOrder(SdfLigand ligand, ChemicalBond bond) {
        if ((bond.order() == BondOrder.SINGLE || bond.order() == BondOrder.DOUBLE)
                && endpointsRemainConnectedWithout(ligand, bond)) return BondOrder.AROMATIC;
        return bond.order();
    }

    private static boolean endpointsRemainConnectedWithout(SdfLigand ligand, ChemicalBond omitted) {
        Set<Integer> visited = new LinkedHashSet<>();
        List<Integer> queue = new ArrayList<>();
        queue.add(omitted.atomIndexA()); visited.add(omitted.atomIndexA());
        for (int cursor = 0; cursor < queue.size(); cursor++) {
            int atom = queue.get(cursor);
            for (ChemicalBond bond : ligand.bonds()) {
                if (bond == omitted) continue;
                int neighbor = bond.atomIndexA() == atom ? bond.atomIndexB()
                        : bond.atomIndexB() == atom ? bond.atomIndexA() : -1;
                if (neighbor == omitted.atomIndexB()) return true;
                if (neighbor >= 0 && visited.add(neighbor)) queue.add(neighbor);
            }
        }
        return false;
    }

    private static String pair(int a, int b) { return Math.min(a, b) + ":" + Math.max(a, b); }
    private static List<Integer> neighbors(SdfLigand ligand, int atom) {
        List<Integer> result = new ArrayList<>();
        for (ChemicalBond bond : ligand.bonds()) {
            if (bond.atomIndexA() == atom) result.add(bond.atomIndexB());
            if (bond.atomIndexB() == atom) result.add(bond.atomIndexA());
        }
        return result;
    }
    private static List<Integer> indicesOfElement(List<Atom> atoms, String element) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < atoms.size(); i++) if (element.equals(element(atoms.get(i)))) result.add(i);
        return result;
    }
    private static List<Atom> atoms(SdfLigand ligand) {
        return ligand.ligand().structure().getChains().getFirst().residues().getFirst().getAtoms();
    }
    private static boolean isSam(PdbqtAtom atom) { return "SAM".equals(atom.residueName()); }
    private static String element(Atom atom) { return atom.getElement().symbol().toUpperCase(); }
    private static String coordinate(Atom atom) { return atom.getPosition().x() + "," + atom.getPosition().y() + "," + atom.getPosition().z(); }
    private static String coordinate(PdbqtAtom atom) { return atom.x() + "," + atom.y() + "," + atom.z(); }
    private static double distance(Atom left, PdbqtAtom right) {
        double dx = left.getPosition().x() - right.x();
        double dy = left.getPosition().y() - right.y();
        double dz = left.getPosition().z() - right.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    private static String sha256(Path path) throws IOException {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public record RestoredReceptor(Structure structure, Receipt receipt) {}
    public record Receipt(String implementationVersion, String canonicalSamSdfSha256,
            String preparedSamSdfSha256, String dockingReceptorPdbqtSha256,
            String proteinTopologyPdbSha256, String malformedSamDirectUseStatus,
            int samAtomCount, int samHeavyAtomCount, int samExplicitHydrogenCount,
            int samFormalCharge, String canonicalSamGraphHash, String canonicalSamOclIdCode,
            String stereochemistryValidation, String samMappingStatus,
            Map<Integer,Integer> canonicalToPreparedAtomMapping,
            List<List<Integer>> symmetryEquivalentGroups, String samMappingHash,
            String proteinMappingHash, int proteinAtomCount, int proteinBondCount,
            int samBondCount, int proteinSamCrossBondCount, String preCoordinateHash,
            String postCoordinateHash, double rmsdAngstrom, double maximumDisplacementAngstrom,
            int movedAtomCount, String transferredTopologyHash, String cofactorStatus,
            String surfDiffScope) {
        public Receipt {
            canonicalToPreparedAtomMapping = Map.copyOf(canonicalToPreparedAtomMapping);
            symmetryEquivalentGroups = symmetryEquivalentGroups.stream().map(List::copyOf).toList();
        }
    }
    private record SamValidation(int atomCount, int heavyAtomCount, int explicitHydrogens,
            int formalCharge, String samGraphHash) {}
    private record StereoValidation(String oclIdCode, String status) {}
    private record Mapping(String status, Map<Integer,Integer> sourceToTarget,
            List<List<Integer>> symmetryGroups, String mappingHash) {}
    private record ProteinTransfer(List<Chain> chains, List<Bond> bonds, int atomCount,
            String mappingHash) {}
    private record SamTransfer(String chainId, Residue residue, List<Bond> bonds) {}
    private record CoordinateProof(String preCoordinateHash, String postCoordinateHash,
            double rmsdAngstrom, double maximumDisplacementAngstrom, int movedAtomCount) {}
}
