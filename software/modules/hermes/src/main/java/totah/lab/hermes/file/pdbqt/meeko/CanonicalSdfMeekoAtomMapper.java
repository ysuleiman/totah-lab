package totah.lab.hermes.file.pdbqt.meeko;

import com.actelion.research.chem.Canonizer;
import com.actelion.research.chem.MolfileParser;
import com.actelion.research.chem.Molecule;
import com.actelion.research.chem.SmilesParser;
import com.actelion.research.chem.StereoMolecule;
import totah.lab.hermes.file.pdbqt.PdbqtAtom;
import totah.lab.hermes.file.pdbqt.PdbqtModel;

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

/** Chemistry-canonical correspondence between a frozen SDF and Meeko pose topology. */
public final class CanonicalSdfMeekoAtomMapper {
    public static final String VERSION = "CANONICAL_SDF_MEEKO_ATOM_MAPPER_V1";

    public Receipt map(Path canonicalSdf, Path pdbqt, PdbqtModel model) throws IOException {
        Objects.requireNonNull(canonicalSdf); Objects.requireNonNull(pdbqt); Objects.requireNonNull(model);
        StereoMolecule sdf = new StereoMolecule();
        MolfileParser sdfParser = new MolfileParser(MolfileParser.MODE_KEEP_HYDROGEN_MAP);
        // RDKit V2000 exports in this campaign carry atom parity on wedge bonds,
        // while the legacy counts-line chiral flag is unset.
        sdfParser.setAssumeChiralTrue(true);
        if (!sdfParser.parse(sdf, canonicalSdf.toFile())) {
            return rejected(canonicalSdf, pdbqt, model, Status.CHEMISTRY_INCONSISTENT, "SDF_PARSE_FAILED");
        }
        MeekoResult metadata;
        StereoMolecule meeko;
        try {
            metadata = new MeekoResultParser().parse(model.remarks()).orElseThrow();
            meeko = new SmilesParser().parseMolecule(metadata.smiles());
        } catch (RuntimeException exception) {
            return rejected(canonicalSdf, pdbqt, model, Status.TOPOLOGY_ABSENT,
                    "MEEKO_TOPOLOGY_PARSE_FAILED: " + exception.getMessage());
        }
        sdf.ensureHelperArrays(Molecule.cHelperCIP);
        meeko.ensureHelperArrays(Molecule.cHelperCIP);

        Map<Integer, Integer> serialBySmiles = validateMeekoMap(metadata, model, meeko);
        validateHydrogenParents(metadata, model, serialBySmiles);
        HeavyGraph sdfHeavy = heavyGraph(sdf);
        Canonizer sdfCanon = new Canonizer(sdfHeavy.molecule(), Canonizer.CREATE_SYMMETRY_RANK);
        Canonizer meekoCanon = new Canonizer(meeko, Canonizer.CREATE_SYMMETRY_RANK);
        if (!sdfCanon.getIDCode().equals(meekoCanon.getIDCode())) {
            String noStereoSdf = new Canonizer(sdfHeavy.molecule(), Canonizer.NEGLECT_ANY_STEREO_INFORMATION).getIDCode();
            String noStereoMeeko = new Canonizer(meeko, Canonizer.NEGLECT_ANY_STEREO_INFORMATION).getIDCode();
            Status status = noStereoSdf.equals(noStereoMeeko)
                    ? Status.STEREOCHEMISTRY_INCOMPATIBLE : Status.CHEMISTRY_INCONSISTENT;
            return rejected(canonicalSdf, pdbqt, model, status,
                    status == Status.STEREOCHEMISTRY_INCOMPATIBLE ? "STEREOCHEMICAL_IDCODE_MISMATCH"
                            : "ELEMENT_CHARGE_CONNECTIVITY_BOND_ORDER_IDCODE_MISMATCH");
        }

        Map<Integer, List<Integer>> sdfByRank = groupByRank(sdfCanon, sdfHeavy.originalIndexByHeavyIndex());
        Map<Integer, List<Integer>> smilesByRank = groupByRank(meekoCanon, identity(meeko.getAtoms()));
        if (!sdfByRank.keySet().equals(smilesByRank.keySet())) {
            return rejected(canonicalSdf, pdbqt, model, Status.CHEMISTRY_INCONSISTENT,
                    "CANONICAL_ORBIT_SET_MISMATCH");
        }
        Map<Integer, List<Integer>> sdfToPose = new LinkedHashMap<>();
        Map<Integer, List<Integer>> poseToSdf = new LinkedHashMap<>();
        List<List<Integer>> symmetryGroups = new ArrayList<>();
        for (int rank : sdfByRank.keySet().stream().sorted().toList()) {
            List<Integer> sdfIndices = sdfByRank.get(rank).stream().sorted().toList();
            List<Integer> poseSerials = smilesByRank.get(rank).stream().map(serialBySmiles::get).sorted().toList();
            if (sdfIndices.size() != poseSerials.size()) {
                return rejected(canonicalSdf, pdbqt, model, Status.CHEMISTRY_INCONSISTENT,
                        "CANONICAL_ORBIT_CARDINALITY_MISMATCH");
            }
            for (int index : sdfIndices) sdfToPose.put(index, poseSerials);
            for (int serial : poseSerials) poseToSdf.put(serial, sdfIndices);
            if (sdfIndices.size() > 1) symmetryGroups.add(sdfIndices);
        }

        mapExplicitHydrogens(sdf, metadata, serialBySmiles, sdfToPose, poseToSdf, symmetryGroups);
        Status status = symmetryGroups.isEmpty() ? Status.UNIQUE_MAPPING : Status.SYMMETRY_EQUIVALENT_MAPPING;
        String stereo = sdfCanon.getIDCode().equals(meekoCanon.getIDCode())
                ? "STEREO_AWARE_OCL_IDCODE_MATCH" : "UNAVAILABLE";
        return receipt(canonicalSdf, pdbqt, model, sdfCanon.getIDCode(), sdfToPose, poseToSdf,
                symmetryGroups, stereo, status, "ALL_EXPLICIT_POSE_ATOMS_AND_IMPLICIT_HYDROGEN_CHEMISTRY_ACCOUNTED");
    }

    private static Map<Integer, Integer> validateMeekoMap(MeekoResult result, PdbqtModel model,
            StereoMolecule meeko) {
        Map<Integer, PdbqtAtom> atoms = new LinkedHashMap<>();
        model.atoms().forEach(atom -> atoms.put(atom.serial(), atom));
        Map<Integer, Integer> serialBySmiles = new LinkedHashMap<>();
        Set<Integer> serials = new LinkedHashSet<>();
        for (MeekoResult.IndexPair pair : result.smilesIndices()) {
            if (pair.first() < 1 || pair.first() > meeko.getAtoms()
                    || !serials.add(pair.second()) || serialBySmiles.put(pair.first(), pair.second()) != null)
                throw new IllegalArgumentException("invalid Meeko SMILES IDX bijection");
            PdbqtAtom atom = atoms.get(pair.second());
            if (atom == null || atom.hydrogen()
                    || meeko.getAtomicNo(pair.first() - 1) != atomicNumber(atom.element()))
                throw new IllegalArgumentException("Meeko SMILES IDX element mismatch");
        }
        if (serialBySmiles.size() != meeko.getAtoms())
            throw new IllegalArgumentException("incomplete Meeko SMILES IDX");
        return Map.copyOf(serialBySmiles);
    }

    private static void validateHydrogenParents(MeekoResult result, PdbqtModel model,
            Map<Integer, Integer> serialBySmiles) {
        Map<Integer, PdbqtAtom> atoms = new LinkedHashMap<>();
        for (PdbqtAtom atom : model.atoms()) {
            if (atoms.put(atom.serial(), atom) != null) {
                throw new IllegalArgumentException("duplicate PDBQT atom serial");
            }
        }
        Set<Integer> mappedHydrogens = new LinkedHashSet<>();
        for (MeekoResult.HydrogenParent parent : result.hydrogenParents()) {
            if (!serialBySmiles.containsKey(parent.parentAtom())) {
                throw new IllegalArgumentException("H PARENT references unknown SMILES atom");
            }
            PdbqtAtom hydrogen = atoms.get(parent.hydrogenAtom());
            if (hydrogen == null || !hydrogen.hydrogen()) {
                throw new IllegalArgumentException("H PARENT references a missing or non-hydrogen atom");
            }
            if (!mappedHydrogens.add(parent.hydrogenAtom())) {
                throw new IllegalArgumentException("duplicate H PARENT hydrogen mapping");
            }
        }
        Set<Integer> explicitHydrogens = model.atoms().stream().filter(PdbqtAtom::hydrogen)
                .map(PdbqtAtom::serial).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!mappedHydrogens.equals(explicitHydrogens)) {
            throw new IllegalArgumentException("H PARENT does not account for every explicit PDBQT hydrogen");
        }
    }

    private static void mapExplicitHydrogens(StereoMolecule sdf, MeekoResult metadata,
            Map<Integer, Integer> serialBySmiles, Map<Integer, List<Integer>> sdfToPose,
            Map<Integer, List<Integer>> poseToSdf, List<List<Integer>> symmetryGroups) {
        for (MeekoResult.HydrogenParent hp : metadata.hydrogenParents()) {
            int parentSerial = serialBySmiles.get(hp.parentAtom());
            List<Integer> parentSdf = poseToSdf.get(parentSerial);
            List<Integer> candidateHydrogens = new ArrayList<>();
            for (int parent : parentSdf) {
                for (int n = 0; n < sdf.getAllConnAtoms(parent); n++) {
                    int neighbor = sdf.getConnAtom(parent, n);
                    if (sdf.getAtomicNo(neighbor) == 1) candidateHydrogens.add(neighbor);
                }
            }
            candidateHydrogens = candidateHydrogens.stream().distinct().sorted().toList();
            if (candidateHydrogens.isEmpty()) throw new IllegalArgumentException("explicit hydrogen has no SDF parent");
            poseToSdf.put(hp.hydrogenAtom(), candidateHydrogens);
            for (int sdfH : candidateHydrogens) {
                List<Integer> old = sdfToPose.getOrDefault(sdfH, List.of());
                List<Integer> updated = new ArrayList<>(old); updated.add(hp.hydrogenAtom());
                sdfToPose.put(sdfH, updated.stream().distinct().sorted().toList());
            }
            if (candidateHydrogens.size() > 1) symmetryGroups.add(candidateHydrogens);
        }
    }

    private static HeavyGraph heavyGraph(StereoMolecule source) {
        StereoMolecule copy = new StereoMolecule(source);
        for (int atom = 0; atom < copy.getAllAtoms(); atom++) copy.setAtomMapNo(atom, atom + 1, false);
        copy.removeExplicitHydrogens(false); copy.ensureHelperArrays(Molecule.cHelperCIP);
        List<Integer> original = new ArrayList<>();
        for (int atom = 0; atom < copy.getAtoms(); atom++) original.add(copy.getAtomMapNo(atom) - 1);
        return new HeavyGraph(copy, original);
    }

    private static Map<Integer, List<Integer>> groupByRank(Canonizer canonizer, List<Integer> sourceIndices) {
        Map<Integer, List<Integer>> result = new LinkedHashMap<>();
        for (int atom = 0; atom < sourceIndices.size(); atom++)
            result.computeIfAbsent(canonizer.getSymmetryRank(atom), ignored -> new ArrayList<>()).add(sourceIndices.get(atom));
        return result;
    }
    private static List<Integer> identity(int count) { List<Integer> out=new ArrayList<>(); for(int i=0;i<count;i++)out.add(i+1); return out; }
    private static int atomicNumber(String element) { return switch (element.toUpperCase()) { case "H"->1;case "C","A"->6;case "N","NA"->7;case "O","OA"->8;case "F"->9;case "P"->15;case "S","SA"->16;case "CL"->17;case "BR"->35;case "I"->53;default->throw new IllegalArgumentException("unsupported element "+element);}; }

    private static Receipt rejected(Path sdf, Path pdbqt, PdbqtModel model, Status status, String reason) throws IOException {
        return receipt(sdf,pdbqt,model,"UNAVAILABLE",Map.of(),Map.of(),List.of(),"NOT_VALIDATED",status,reason);
    }
    private static Receipt receipt(Path sdf, Path pdbqt, PdbqtModel model, String idcode,
            Map<Integer,List<Integer>> forward, Map<Integer,List<Integer>> reverse,
            List<List<Integer>> groups, String stereo, Status status, String detail) throws IOException {
        String canonical = canonical(forward, reverse, groups, idcode, stereo, status, detail);
        return new Receipt(sha256(sdf), sha256(modelIdentity(model)), idcode,
                forward, reverse, groups, stereo, status, sha256(canonical), VERSION, detail);
    }
    private static String canonical(Map<Integer,List<Integer>> f,Map<Integer,List<Integer>>r,List<List<Integer>>g,String i,String s,Status status,String d){return i+"\n"+f+"\n"+r+"\n"+g+"\n"+s+"\n"+status+"\n"+d;}
    private static String modelIdentity(PdbqtModel model) {
        return model.modelNumber() + "\n" + model.remarks() + "\n" + model.atoms() + "\n"
                + model.rotatableBondSerials().stream().map(java.util.Arrays::toString).toList();
    }
    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                for (int read; (read = input.read(buffer)) >= 0; ) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String sha256(String value) { try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);} }

    private record HeavyGraph(StereoMolecule molecule,List<Integer> originalIndexByHeavyIndex){}
    public enum Status { UNIQUE_MAPPING, SYMMETRY_EQUIVALENT_MAPPING, AMBIGUOUS_NON_EQUIVALENT_MAPPING,
        CHEMISTRY_INCONSISTENT, STEREOCHEMISTRY_INCOMPATIBLE, TOPOLOGY_ABSENT }
    public record Receipt(String canonicalSdfSha256,String pdbqtModelIdentity,String oclIdCode,
            Map<Integer,List<Integer>> sdfIndexToMeekoSerialOrOrbit,
            Map<Integer,List<Integer>> meekoSerialToSdfIndexOrOrbit,List<List<Integer>> symmetryEquivalentGroups,
            String stereochemistryValidation,Status status,String mappingSha256,String implementationVersion,String detail){
        public Receipt { sdfIndexToMeekoSerialOrOrbit=Map.copyOf(sdfIndexToMeekoSerialOrOrbit);meekoSerialToSdfIndexOrOrbit=Map.copyOf(meekoSerialToSdfIndexOrOrbit);symmetryEquivalentGroups=symmetryEquivalentGroups.stream().map(List::copyOf).toList(); }
        public boolean successful(){return status==Status.UNIQUE_MAPPING||status==Status.SYMMETRY_EQUIVALENT_MAPPING;}
    }
}
