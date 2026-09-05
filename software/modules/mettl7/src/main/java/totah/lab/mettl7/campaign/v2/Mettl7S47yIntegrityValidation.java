package totah.lab.mettl7.campaign.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import totah.lab.athena.clash.StericClashAnalysis;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.pdbqt.PdbqtGaiaMapper;
import totah.lab.hermes.file.pdbqt.PdbqtWriteOptions;
import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;
import totah.lab.hermes.file.pdbqt.writer.PdbqtWriter;
import totah.lab.proteus.protein.mutation.rotamer.Rotamer;
import totah.lab.proteus.protein.mutation.rotamer.RotamerEvaluator;
import totah.lab.proteus.protein.mutation.rotamer.RotamerLibrary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Time-boxed, deterministic standard-rotamer integrity validation for B S47Y. */
public final class Mettl7S47yIntegrityValidation {
    private static final Set<String> FIXED_TYR_ATOMS = Set.of("N", "CA", "C", "O", "OXT", "H", "HA", "HA2", "HA3", "CB");
    private static final List<String> TARGETS = List.of("B2", "B4", "B6", "B7");

    private Mettl7S47yIntegrityValidation() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Usage: <receptor-directory>");
        Path receptorDirectory = Path.of(args[0]).toAbsolutePath().normalize();
        Path candidateDirectory = receptorDirectory.resolve("s47y_candidates");
        Files.createDirectories(candidateDirectory);
        PdbqtReader reader = new PdbqtReader();
        PdbqtWriter writer = new PdbqtWriter();
        List<Map<String, Object>> all = new ArrayList<>();
        List<Map<String, Object>> selections = new ArrayList<>();
        for (String target : TARGETS) {
            Path input = receptorDirectory.resolve(target + "_SAM_BOUND.pdbqt");
            Structure source = PdbqtGaiaMapper.toStructure(reader.read(input));
            LocatedResidue tyr = locateTyr47(source);
            List<Rotamer> rotamers = new RotamerLibrary().rotamers("TYR", null);
            List<Candidate> candidates = new ArrayList<>();
            for (Rotamer rotamer : rotamers) {
              for (double chi2 : List.of(Math.PI / 2.0, -Math.PI / 2.0)) {
                double targetAngle = rotamer.firstChiOrZero();
                Structure candidate = rotateTyr(source, tyr, targetAngle - Math.PI, chi2);
                LocatedResidue candidateTyr = locateTyr47(candidate);
                List<AtomReference> scope = candidateTyr.residue().getAtoms().stream()
                        .filter(a -> !isBackbone(a.getName()))
                        .map(a -> reference(candidateTyr.chainId(), candidateTyr.residue(), a)).toList();
                var clashes = StericClashAnalysis.findClashes(candidate, scope,
                        new StericClashAnalysis.Options(0.70));
                double score = new RotamerEvaluator().score(candidate,
                        new totah.lab.gaia.structure.ResidueId(candidateTyr.chainId(), 47,
                                candidateTyr.residue().getInsertionCode()),
                        candidateTyr.residue().getAtoms());
                boolean unchanged = outsideTargetCoordinatesEqual(source, candidate, tyr.chainId());
                boolean samValid = samCount(candidate) == 49 && samCoordinatesEqual(source, candidate);
                boolean pass = clashes.isEmpty() && unchanged && samValid;
                String candidateId = rotamer.id() + (chi2 > 0 ? "_chi2-plus90" : "_chi2-minus90");
                Path output = candidateDirectory.resolve(target + "_" + candidateId + ".pdbqt");
                writer.write(PdbqtGaiaMapper.fromStructure(candidate), output, PdbqtWriteOptions.defaults());
                Candidate result = new Candidate(candidateId, score, clashes.size(),
                        clashes.stream().mapToDouble(StericClashAnalysis.Clash::overlapAmount).max().orElse(0.0),
                        unchanged, samValid, pass, output, sha256(output));
                candidates.add(result);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("receptor_id", target); row.put("rotamer", result.rotamer());
                row.put("steric_score", result.stericScore()); row.put("severe_clash_count", result.severeClashes());
                row.put("maximum_overlap_A", result.maximumOverlap()); row.put("outside_target_coordinates_unchanged", result.outsideTargetUnchanged());
                row.put("severe_clash_pairs", clashes.stream().map(c -> Map.of(
                        "first", c.first().toString(), "second", c.second().toString(),
                        "distance_A", c.distance(), "overlap_A", c.overlapAmount())).toList());
                row.put("sam_49_atoms_and_coordinates_unchanged", result.samValid()); row.put("pass", result.pass());
                row.put("candidate", result.path().toString()); row.put("sha256", result.sha256());
                all.add(Map.copyOf(row));
              }
            }
            Candidate selected = candidates.stream().filter(Candidate::pass)
                    .min(Comparator.comparingDouble(Candidate::stericScore)).orElse(null);
            Map<String, Object> selection = new LinkedHashMap<>();
            selection.put("receptor_id", target);
            selection.put("status", selected == null ? "TECHNICAL_FAILURE" : "PASS");
            selection.put("selected_rotamer", selected == null ? null : selected.rotamer());
            selection.put("selected_candidate", selected == null ? null : selected.path().toString());
            selection.put("selected_sha256", selected == null ? null : selected.sha256());
            selections.add(selection);
        }
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(receptorDirectory.resolve("s47y_candidate_metrics.json").toFile(), all);
        mapper.writeValue(receptorDirectory.resolve("s47y_integrity_selections.json").toFile(), selections);
    }

    static Structure rotateTyr(Structure structure, LocatedResidue target, double chi1Delta, double chi2Delta) {
        return rotateAromatic(structure, target.chainId(), target.residue(), chi1Delta, chi2Delta);
    }

    static Structure rotateAromatic(Structure structure, String targetChain, Residue targetResidue,
                                    double chi1Delta, double chi2Delta) {
        Atom ca = atom(targetResidue, "CA");
        Atom cb = atom(targetResidue, "CB");
        Map<String, Point3D> moved = new LinkedHashMap<>();
        for (Atom atom : targetResidue.getAtoms()) {
            Point3D position = atom.getPosition();
            if (!FIXED_TYR_ATOMS.contains(atom.getName())) {
                position = rotateAroundAxis(position, ca.getPosition(), cb.getPosition(), chi1Delta);
            }
            moved.put(atom.getName(), position);
        }
        Point3D movedCb = moved.get("CB"), movedCg = moved.get("CG");
        for (String name : List.of("CD1", "CD2", "CE1", "CE2", "CZ", "OH")) {
            Point3D position = moved.get(name);
            if (position != null) moved.put(name, rotateAroundAxis(position, movedCb, movedCg, chi2Delta));
        }
        List<Chain> chains = new ArrayList<>();
        for (Chain chain : structure.getChains()) {
            List<Residue> residues = new ArrayList<>();
            for (Residue residue : chain.residues()) {
                if (chain.id().equals(targetChain) && residue.getNumber() == targetResidue.getNumber()
                        && java.util.Objects.equals(residue.getInsertionCode(), targetResidue.getInsertionCode())) {
                    List<Atom> atoms = new ArrayList<>();
                    for (Atom atom : residue.getAtoms()) {
                        if (FIXED_TYR_ATOMS.contains(atom.getName())) atoms.add(atom);
                        else atoms.add(atom.toBuilder().position(moved.get(atom.getName())).build());
                    }
                    residues.add(new Residue(residue.getName(), residue.getNumber(), residue.getInsertionCode(), residue.getClassificationEvidence(), atoms));
                } else residues.add(residue);
            }
            chains.add(new Chain(chain.id(), residues));
        }
        return new Structure(chains, structure.bonds(), structure.getConnectivityMetadata());
    }

    private static Point3D rotateAroundAxis(Point3D p, Point3D origin, Point3D axisPoint, double radians) {
        double ux=axisPoint.x()-origin.x(), uy=axisPoint.y()-origin.y(), uz=axisPoint.z()-origin.z();
        double length=Math.sqrt(ux*ux+uy*uy+uz*uz); ux/=length; uy/=length; uz/=length;
        double c=Math.cos(radians), s=Math.sin(radians);
        double x=p.x()-origin.x(), y=p.y()-origin.y(), z=p.z()-origin.z();
        double dot=ux*x+uy*y+uz*z;
        return new Point3D(origin.x()+x*c+(uy*z-uz*y)*s+ux*dot*(1-c),
                origin.y()+y*c+(uz*x-ux*z)*s+uy*dot*(1-c),
                origin.z()+z*c+(ux*y-uy*x)*s+uz*dot*(1-c));
    }

    private static LocatedResidue locateTyr47(Structure s) {
        List<LocatedResidue> found = new ArrayList<>();
        for (Chain c : s.getChains()) for (Residue r : c.residues())
            if (r.getNumber()==47 && r.getName().equalsIgnoreCase("TYR")) found.add(new LocatedResidue(c.id(), r));
        if (found.size()!=1) throw new IllegalStateException("Expected one TYR47, found "+found.size());
        return found.getFirst();
    }
    private static Atom atom(Residue r, String name) { return r.getAtoms().stream().filter(a->a.getName().equals(name)).findFirst().orElseThrow(); }
    private static boolean isBackbone(String name) { return Set.of("N","CA","C","O","OXT","H","HA","HA2","HA3").contains(name); }
    private static AtomReference reference(String chain, Residue r, Atom a) { return new AtomReference(chain,r.getNumber(),r.getInsertionCode()==null?' ':r.getInsertionCode(),a.getName()); }
    private static long samCount(Structure s) { return s.getChains().stream().flatMap(c->c.residues().stream()).filter(r->r.getName().equalsIgnoreCase("SAM")).mapToLong(Residue::getAtomCount).sum(); }
    private static boolean outsideTargetCoordinatesEqual(Structure a, Structure b, String targetChain) { return coordinatesEqual(a,b,(c,r)->!(c.equals(targetChain)&&r.getNumber()==47)); }
    private static boolean samCoordinatesEqual(Structure a, Structure b) { return coordinatesEqual(a,b,(c,r)->r.getName().equalsIgnoreCase("SAM")); }
    private static boolean coordinatesEqual(Structure a, Structure b, ResiduePredicate include) {
        Map<String,Point3D> left=positions(a,include), right=positions(b,include); return left.equals(right);
    }
    private static Map<String,Point3D> positions(Structure s, ResiduePredicate include) { Map<String,Point3D> m=new LinkedHashMap<>(); for(Chain c:s.getChains())for(Residue r:c.residues())if(include.test(c.id(),r))for(Atom a:r.getAtoms())m.put(c.id()+":"+r.getNumber()+":"+r.getInsertionCode()+":"+a.getName(),a.getPosition()); return m; }
    private static String sha256(Path path) throws IOException { try { MessageDigest d=MessageDigest.getInstance("SHA-256"); try(var in=Files.newInputStream(path)){byte[] b=new byte[8192];for(int n;(n=in.read(b))>=0;)if(n>0)d.update(b,0,n);} return HexFormat.of().formatHex(d.digest()); } catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);} }
    @FunctionalInterface private interface ResiduePredicate { boolean test(String chain, Residue residue); }
    record LocatedResidue(String chainId, Residue residue) {}
    private record Candidate(String rotamer,double stericScore,int severeClashes,double maximumOverlap,boolean outsideTargetUnchanged,boolean samValid,boolean pass,Path path,String sha256) {}
}
