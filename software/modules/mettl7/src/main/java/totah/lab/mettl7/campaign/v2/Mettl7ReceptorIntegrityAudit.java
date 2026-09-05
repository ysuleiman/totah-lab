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
import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;
import totah.lab.mettl7.campaign.v2.ReceptorBackground.Paralog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Deterministic integrity audit and authoritative manifest for V2 receptors. */
public final class Mettl7ReceptorIntegrityAudit {
    private static final Pattern MUTATION = Pattern.compile("^([A-Z])(\\d+)([A-Z])$");
    private static final Set<String> BACKBONE = Set.of("N", "CA", "C", "O", "OXT", "H", "HA", "HA2", "HA3");
    private static final Set<String> S47Y_FAILURES = Set.of("B2", "B4", "B6", "B7");
    private static final Map<String,String> RESIDUES = Map.ofEntries(
            Map.entry("A","ALA"), Map.entry("C","CYS"), Map.entry("D","ASP"), Map.entry("E","GLU"),
            Map.entry("F","PHE"), Map.entry("G","GLY"), Map.entry("H","HIS"), Map.entry("I","ILE"),
            Map.entry("K","LYS"), Map.entry("L","LEU"), Map.entry("M","MET"), Map.entry("N","ASN"),
            Map.entry("P","PRO"), Map.entry("Q","GLN"), Map.entry("R","ARG"), Map.entry("S","SER"),
            Map.entry("T","THR"), Map.entry("V","VAL"), Map.entry("W","TRP"), Map.entry("Y","TYR"));

    private Mettl7ReceptorIntegrityAudit() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 2) throw new IllegalArgumentException("Usage: <repository-root> <receptor-directory>");
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        Path directory = Path.of(args[1]).toAbsolutePath().normalize();
        Path wtA = root.resolve("analysis/dcmb/controlled_campaign/prepared/7A_WT_SAM_BOUND.pdbqt");
        Path wtB = root.resolve("analysis/dcmb/controlled_campaign/prepared/7B_WT_SAM_BOUND.pdbqt");
        PdbqtReader reader = new PdbqtReader();
        Structure sourceA = PdbqtGaiaMapper.toStructure(reader.read(wtA));
        Structure sourceB = PdbqtGaiaMapper.toStructure(reader.read(wtB));
        List<Map<String,Object>> manifest = new ArrayList<>();
        for (ReceptorBackground background : Mettl7MechanisticMatrixV2Panel.receptors()) {
            Path prepared = directory.resolve(background.id() + "_SAM_BOUND.pdbqt");
            Path sourcePath = background.paralog() == Paralog.METTL7A ? wtA : wtB;
            Structure source = background.paralog() == Paralog.METTL7A ? sourceA : sourceB;
            Structure candidate = PdbqtGaiaMapper.toStructure(reader.read(prepared));
            List<MutationSpec> mutations = background.substitutions().stream().map(Mettl7ReceptorIntegrityAudit::parse).toList();
            boolean identities = correctIdentities(candidate, mutations);
            boolean backbone = backboneCoordinatesEqual(source, candidate);
            boolean nonTarget = nonTargetCoordinatesEqual(source, candidate, mutations);
            boolean sam = samCount(candidate) == 49 && samCoordinatesEqual(source, candidate);
            boolean typing = mutationTypingComplete(candidate, mutations);
            List<StericClashAnalysis.Clash> clashes = mutationClashes(candidate, mutations);
            boolean s47yFailure = S47Y_FAILURES.contains(background.id());
            boolean valid = !s47yFailure && identities && backbone && nonTarget && sam && typing && clashes.isEmpty();
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("receptor_id", background.id()); row.put("paralog", background.paralog().name());
            row.put("substitutions", background.substitutions()); row.put("status", valid ? "VALID" : "TECHNICAL_FAILURE");
            row.put("prepared_path", prepared.toString()); row.put("prepared_sha256", sha256(prepared));
            row.put("wild_type_source", sourcePath.toString()); row.put("wild_type_source_sha256", sha256(sourcePath));
            row.put("atom_count", candidate.getAtomCount()); row.put("sam_atom_count", samCount(candidate));
            row.put("sam_coordinates_unchanged", sam); row.put("backbone_coordinates_unchanged", backbone);
            row.put("non_target_coordinates_unchanged", nonTarget); row.put("substitution_identities_correct", identities);
            row.put("mutated_sidechain_typing_complete", typing);
            row.put("mutation_induced_severe_clash_count", clashes.size());
            row.put("mutation_induced_severe_clashes", clashes.stream().map(c -> Map.of(
                    "first", c.first().toString(), "second", c.second().toString(),
                    "distance_A", c.distance(), "overlap_A", c.overlapAmount())).toList());
            row.put("technical_failure_reason", s47yFailure ? "S47Y_STANDARD_ROTAMER_SEARCH_FAILED_FROZEN_INTEGRITY_GATE"
                    : valid ? null : "RECEPTOR_INTEGRITY_GATE_FAILED");
            if (s47yFailure) {
                row.put("s47y_validation", directory.resolve("s47y_integrity_selections.json").toString());
                row.put("s47y_candidate_metrics", directory.resolve("s47y_candidate_metrics.json").toString());
            }
            manifest.add(row);
        }
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
                .writeValue(directory.resolve("receptor_manifest.json").toFile(), manifest);
    }

    static List<StericClashAnalysis.Clash> mutationClashes(Structure structure, List<MutationSpec> mutations) {
        List<AtomReference> scope = new ArrayList<>();
        for (MutationSpec mutation : mutations) {
            LocatedResidue located = locate(structure, mutation.position(), mutation.replacement());
            for (Atom atom : located.residue().getAtoms()) if (!BACKBONE.contains(atom.getName()))
                scope.add(reference(located.chain(), located.residue(), atom));
        }
        if (scope.isEmpty()) return List.of();
        return StericClashAnalysis.findClashes(structure, scope, new StericClashAnalysis.Options(0.70));
    }

    private static boolean correctIdentities(Structure structure, List<MutationSpec> mutations) {
        try { for (MutationSpec mutation : mutations) locate(structure, mutation.position(), mutation.replacement()); return true; }
        catch (RuntimeException exception) { return false; }
    }
    private static boolean mutationTypingComplete(Structure structure, List<MutationSpec> mutations) {
        try {
            for (MutationSpec mutation : mutations) for (Atom atom : locate(structure, mutation.position(), mutation.replacement()).residue().getAtoms())
                if (!BACKBONE.contains(atom.getName()) && (atom.getAutoDockType()==null || atom.getAutoDockType().isBlank() || !Double.isFinite(atom.getCharge()))) return false;
            return true;
        } catch (RuntimeException exception) { return false; }
    }
    private static boolean backboneCoordinatesEqual(Structure a, Structure b) { return positions(a,(c,r,atom)->BACKBONE.contains(atom.getName())).equals(positions(b,(c,r,atom)->BACKBONE.contains(atom.getName()))); }
    private static boolean nonTargetCoordinatesEqual(Structure a, Structure b, List<MutationSpec> mutations) {
        Set<Integer> positions = mutations.stream().map(MutationSpec::position).collect(java.util.stream.Collectors.toSet());
        return positions(a,(c,r,atom)->!positions.contains(r.getNumber())).equals(positions(b,(c,r,atom)->!positions.contains(r.getNumber())));
    }
    private static boolean samCoordinatesEqual(Structure a, Structure b) { return positions(a,(c,r,atom)->r.getName().equalsIgnoreCase("SAM")).equals(positions(b,(c,r,atom)->r.getName().equalsIgnoreCase("SAM"))); }
    private static long samCount(Structure s) { return s.getChains().stream().flatMap(c->c.residues().stream()).filter(r->r.getName().equalsIgnoreCase("SAM")).mapToLong(Residue::getAtomCount).sum(); }
    private static Map<String,Point3D> positions(Structure s, AtomPredicate include) { Map<String,Point3D> result=new LinkedHashMap<>(); for(Chain c:s.getChains())for(Residue r:c.residues())for(Atom a:r.getAtoms())if(include.test(c.id(),r,a))result.put(c.id()+":"+r.getNumber()+":"+r.getInsertionCode()+":"+a.getName(),a.getPosition()); return result; }
    private static LocatedResidue locate(Structure s, int position, String residueName) { List<LocatedResidue> found=new ArrayList<>(); for(Chain c:s.getChains())for(Residue r:c.residues())if(r.getNumber()==position&&r.getName().equalsIgnoreCase(residueName))found.add(new LocatedResidue(c.id(),r)); if(found.size()!=1)throw new IllegalStateException("Expected one "+residueName+position+", found "+found.size()); return found.getFirst(); }
    private static AtomReference reference(String chain, Residue r, Atom a) { return new AtomReference(chain,r.getNumber(),r.getInsertionCode()==null?' ':r.getInsertionCode(),a.getName()); }
    private static MutationSpec parse(String value) { var m=MUTATION.matcher(value.trim().toUpperCase(Locale.ROOT)); if(!m.matches())throw new IllegalArgumentException(value); return new MutationSpec(Integer.parseInt(m.group(2)),RESIDUES.get(m.group(3))); }
    private static String sha256(Path path) throws IOException { try { MessageDigest d=MessageDigest.getInstance("SHA-256"); try(var in=Files.newInputStream(path)){byte[] b=new byte[8192];for(int n;(n=in.read(b))>=0;)if(n>0)d.update(b,0,n);} return HexFormat.of().formatHex(d.digest()); } catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);} }
    @FunctionalInterface private interface AtomPredicate { boolean test(String chain, Residue residue, Atom atom); }
    record MutationSpec(int position, String replacement) {}
    private record LocatedResidue(String chain, Residue residue) {}
}
