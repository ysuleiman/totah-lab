package totah.lab.mettl7.campaign.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import totah.lab.athena.clash.StericClashAnalysis;
import totah.lab.athena.ligand.contact.DefaultContactAnalyzer;
import totah.lab.athena.ligand.contact.LigandContact;
import totah.lab.athena.sasa.ShrakeRupleySasa;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.pdbqt.PdbqtGaiaMapper;
import totah.lab.hermes.file.pdbqt.PdbqtModel;
import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;
import totah.lab.hermes.file.pdbqt.vina.VinaResultParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rebuilds raw pose evidence from immutable completed run artifacts.
 * Output is pose-level and intentionally contains no biological conclusion.
 */
public final class Mettl7IncrementalPosePostProcessor {
    private static final Set<Integer> SECTOR_1 = Set.of(39, 40, 43, 47);
    private static final Set<Integer> SECTOR_2 = Set.of(144, 145, 149, 151, 173, 174, 175);
    private static final Set<Integer> SECTOR_3 = Set.of(195, 196, 198, 199, 200, 202, 203, 204, 205, 206, 207);
    private static final Set<Integer> SECTOR_4 = Set.of(228, 229, 231, 232, 234, 237);
    private static final List<Integer> DISTANCES = List.of(43, 47, 195, 199, 200, 202, 203);
    private static final String[] HEADER = {
            "run_id", "receptor_id", "species_id", "seed", "pose_model", "vina_affinity",
            "vina_rmsd_lb", "vina_rmsd_ub", "contacts_le_3p5", "contacts_le_4p0",
            "contacts_le_4p5", "shell_contacts_4p5_to_8p0", "sector_39_47",
            "sector_144_175", "sector_195_207", "sector_228_237", "residue_43_distance",
            "residue_47_distance", "residue_195_distance", "residue_199_distance",
            "residue_200_distance", "residue_202_distance", "residue_203_distance",
            "sam_contact_count_le_4p5", "protein_severe_clash_count", "sam_clash_pairs_lt_2p0",
            "ligand_sasa_isolated_a2", "ligand_sasa_bound_a2", "burial_fraction",
            "acceptor_mapping_status", "acceptor_atom", "acceptor_to_sam_methyl_distance_a",
            "acceptor_methyl_sam_sulfur_angle_deg", "productive_geometry_screen",
            "evidence_status", "partial_conclusion_authorized", "receipt_path", "poses_sha256"
    };

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final PdbqtReader reader = new PdbqtReader();
    private final VinaResultParser vina = new VinaResultParser();

    public static void main(String[] args) throws IOException {
        if (args.length != 4) throw new IllegalArgumentException(
                "Usage: <authoritative-ledger.csv> <runs-directory> <raw-pose.csv> <completeness.json>");
        new Mettl7IncrementalPosePostProcessor().process(
                Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
    }

    public Summary process(Path authoritativeLedger, Path runsDirectory,
                           Path rawPoseCsv, Path completenessJson) throws IOException {
        long expected = countLedgerRows(authoritativeLedger);
        Map<String, String> acceptorBySpecies = acceptorsFromLedger(authoritativeLedger);
        List<Path> receipts;
        try (var stream = Files.find(runsDirectory, 2,
                (path, attributes) -> attributes.isRegularFile() && path.getFileName().toString().equals("receipt.json"))) {
            receipts = stream.sorted().toList();
        }
        List<PoseRow> rows = new ArrayList<>();
        int completed = 0, valid = 0, failed = 0, invalid = 0;
        for (Path receiptPath : receipts) {
            JsonNode receipt;
            try { receipt = mapper.readTree(receiptPath.toFile()); }
            catch (IOException transientWrite) { invalid++; continue; }
            String status = receipt.path("status").asText("");
            if (!status.equals("COMPLETED_VALID")) {
                if (status.contains("FAIL")) failed++; else invalid++;
                continue;
            }
            completed++;
            Path poses = receiptPath.getParent().resolve("poses.pdbqt");
            Path receptor = Path.of(receipt.path("receptorPath").asText());
            if (!Files.isRegularFile(poses) || !Files.isRegularFile(receptor)
                    || !sha256(poses).equals(receipt.path("posesSha256").asText())
                    || !sha256(receptor).equals(receipt.path("receptorSha256").asText())) {
                invalid++;
                continue;
            }
            valid++;
            analyzeReceipt(receiptPath, receipt, receptor, poses, acceptorBySpecies, rows);
        }
        rows.sort(Comparator.comparing(PoseRow::runId).thenComparingInt(PoseRow::model));
        writeAtomic(rawPoseCsv, poseCsv(rows));
        Summary summary = new Summary(expected, receipts.size(), completed, valid, failed, invalid,
                Math.max(0, expected - completed - failed), rows.size(), false,
                "PARTIAL BIOLOGICAL CONCLUSIONS ARE FORBIDDEN");
        writeJsonAtomic(completenessJson, summary);
        return summary;
    }

    private void analyzeReceipt(Path receiptPath, JsonNode receipt, Path receptorPath,
                                Path posesPath, Map<String, String> acceptorBySpecies,
                                List<PoseRow> output) throws IOException {
        String runId = receipt.path("runId").asText();
        String[] identity = runId.split("__");
        String receptorId = identity.length > 0 ? identity[0] : "";
        String speciesId = identity.length > 1 ? identity[1] : "";
        int seed = receipt.path("seed").asInt();
        Structure receptor = PdbqtGaiaMapper.toStructure(reader.read(receptorPath));
        ReceptorParts parts = split(receptor);
        for (PdbqtModel model : reader.read(posesPath).models()) {
            var ligand = PdbqtGaiaMapper.toLigand(model, speciesId);
            List<LigandContact> contacts = new DefaultContactAnalyzer(4.5, 8.0)
                    .analyze(receptor, ligand);
            Map<Integer, Double> residueDistances = new LinkedHashMap<>();
            contacts.forEach(contact -> residueDistances.merge(contact.residue().residueNumber(),
                    contact.distance(), Math::min));
            List<LigandContact> proteinContacts = contacts.stream()
                    .filter(contact -> !parts.samResidues().contains(residueKey(contact))).toList();
            int samContacts = (int) contacts.stream()
                    .filter(contact -> parts.samResidues().contains(residueKey(contact))
                            && contact.distance() <= 4.5).count();
            Structure ligandForComplex = renameChain(ligand.structure(), "Z");
            Structure complex = combine(nearby(receptor, ligandForComplex, 10.0), ligandForComplex);
            int proteinClashes = crossClashes(complex, "Z", "L", 0.7);
            int samClashes = pairCountBelow(ligand.structure(), parts.sam(), 2.0);
            double isolated = ShrakeRupleySasa.calculate(ligand.structure()).totalArea();
            double bound = ligandSasa(complex, "Z");
            double burial = isolated == 0.0 ? Double.NaN : 1.0 - bound / isolated;
            Geometry geometry = geometry(model, parts.sam(), acceptorBySpecies.getOrDefault(speciesId, ""));
            var score = vina.parse(model.remarks()).orElse(null);
            output.add(new PoseRow(runId, receptorId, speciesId, seed, model.modelNumber(),
                    score == null ? null : score.affinity(), score == null ? null : score.rmsdLowerBound(),
                    score == null ? null : score.rmsdUpperBound(), ids(proteinContacts, 3.5),
                    ids(proteinContacts, 4.0), ids(proteinContacts, 4.5), shell(proteinContacts),
                    sector(proteinContacts, SECTOR_1), sector(proteinContacts, SECTOR_2),
                    sector(proteinContacts, SECTOR_3), sector(proteinContacts, SECTOR_4),
                    DISTANCES.stream().map(position -> residueDistances.get(position)).toList(), samContacts,
                    proteinClashes, samClashes, isolated, bound, burial, geometry,
                    receiptPath.toString(), receipt.path("posesSha256").asText()));
        }
    }

    private static ReceptorParts split(Structure receptor) {
        List<Chain> protein = new ArrayList<>(), sam = new ArrayList<>();
        List<String> samNumbers = new ArrayList<>();
        for (Chain chain : receptor.getChains()) {
            List<Residue> pr = new ArrayList<>(), sr = new ArrayList<>();
            for (Residue residue : chain.residues()) {
                if (residue.getName().equalsIgnoreCase("SAM")) { sr.add(residue); samNumbers.add(chain.id()+":"+residue.getNumber()); }
                else pr.add(residue);
            }
            if (!pr.isEmpty()) protein.add(new Chain(chain.id(), pr));
            if (!sr.isEmpty()) sam.add(new Chain("S" + chain.id(), sr));
        }
        return new ReceptorParts(new Structure(protein), new Structure(sam), Set.copyOf(samNumbers));
    }

    private static Structure combine(Structure receptor, Structure ligand) {
        List<Chain> chains = new ArrayList<>(receptor.getChains());
        chains.addAll(ligand.getChains());
        return new Structure(chains);
    }

    private static Structure renameChain(Structure structure, String id) {
        List<Residue> residues = structure.getChains().stream()
                .flatMap(chain -> chain.residues().stream()).toList();
        return new Structure(List.of(new Chain(id, residues)));
    }

    /** Atoms farther than this conservative bound cannot occlude ligand SASA. */
    private static Structure nearby(Structure receptor, Structure ligand, double cutoff) {
        List<Atom> ligandAtoms = heavy(ligand); List<Chain> chains = new ArrayList<>();
        for (Chain chain : receptor.getChains()) {
            List<Residue> residues = chain.residues().stream().filter(residue -> residue.getAtoms().stream()
                    .filter(Atom::isHeavyAtom).anyMatch(atom -> ligandAtoms.stream().anyMatch(ligandAtom ->
                            atom.getPosition().distance(ligandAtom.getPosition()) <= cutoff))).toList();
            if (!residues.isEmpty()) chains.add(new Chain(chain.id(), residues));
        }
        return new Structure(chains);
    }

    private static int crossClashes(Structure complex, String ligandChain,
                                    String excludedCofactorChain, double scale) {
        return (int) StericClashAnalysis.findClashes(complex,
                new StericClashAnalysis.Options(scale)).stream()
                .filter(clash -> clash.first().chainId().equals(ligandChain)
                        ^ clash.second().chainId().equals(ligandChain))
                .filter(clash -> !clash.first().chainId().equals(excludedCofactorChain)
                        && !clash.second().chainId().equals(excludedCofactorChain)).count();
    }

    private static int pairCountBelow(Structure ligand, Structure other, double cutoff) {
        List<Atom> a = heavy(ligand), b = heavy(other); int count = 0;
        for (Atom first : a) for (Atom second : b)
            if (first.getPosition().distance(second.getPosition()) < cutoff) count++;
        return count;
    }

    private static List<Atom> heavy(Structure structure) {
        return structure.getChains().stream().flatMap(c -> c.residues().stream())
                .flatMap(r -> r.getAtoms().stream()).filter(Atom::isHeavyAtom).toList();
    }

    private static double ligandSasa(Structure complex, String ligandChain) {
        return ShrakeRupleySasa.calculate(complex).areaByAtom().entrySet().stream()
                .filter(entry -> entry.getKey().chainId().equals(ligandChain))
                .mapToDouble(Map.Entry::getValue).sum();
    }

    private static Geometry geometry(PdbqtModel model, Structure sam, String acceptorDefinition) {
        String expectedElement = acceptorDefinition.strip().toUpperCase(Locale.ROOT).startsWith("S") ? "S"
                : acceptorDefinition.strip().toUpperCase(Locale.ROOT).startsWith("N") ? "N" : "";
        List<Atom> acceptors = model.heavyAtoms().stream().filter(atom -> {
            String type = atom.autodockType().toUpperCase(Locale.ROOT);
            return !expectedElement.isEmpty() && type.startsWith(expectedElement);
        }).map(atom -> PdbqtGaiaMapper.toLigand(new PdbqtModel(1, List.of(atom), null, List.of()), "x")
                .structure().getChains().getFirst().residues().getFirst().getAtoms().getFirst()).toList();
        Atom methyl = findSam(sam, "C9", "CE");
        Atom sulfur = findSam(sam, "S8", "SD");
        if (methyl == null || sulfur == null || acceptors.size() != 1) {
            return new Geometry(acceptors.isEmpty() ? "NO_CANDIDATE" : "AMBIGUOUS_CANDIDATES",
                    "", null, null, "NOT_CLASSIFIED");
        }
        Atom acceptor = acceptors.getFirst();
        double distance = acceptor.getPosition().distance(methyl.getPosition());
        double angle = angle(acceptor.getPosition(), methyl.getPosition(), sulfur.getPosition());
        boolean pass = distance >= 2.8 && distance <= 3.2 && angle >= 150.0;
        return new Geometry("UNIQUE_ELEMENT_CANDIDATE", acceptor.getName(), distance, angle,
                pass ? "GEOMETRY_PASS_CHEMISTRY_UNASSESSED" : "GEOMETRY_FAIL");
    }

    private static Atom findSam(Structure sam, String... names) {
        for (String name : names) for (Atom atom : heavy(sam)) if (atom.getName().equals(name)) return atom;
        return null;
    }

    private static double angle(Point3D a, Point3D b, Point3D c) {
        double ux=a.x()-b.x(), uy=a.y()-b.y(), uz=a.z()-b.z();
        double vx=c.x()-b.x(), vy=c.y()-b.y(), vz=c.z()-b.z();
        double cosine=(ux*vx+uy*vy+uz*vz)/(Math.sqrt(ux*ux+uy*uy+uz*uz)*Math.sqrt(vx*vx+vy*vy+vz*vz));
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cosine))));
    }

    private static String ids(List<LigandContact> contacts, double cutoff) {
        return contacts.stream().filter(c -> c.distance() <= cutoff).map(c -> Integer.toString(c.residue().residueNumber()))
                .distinct().sorted().collect(Collectors.joining(";"));
    }
    private static String residueKey(LigandContact contact) {
        return contact.residue().chainId() + ":" + contact.residue().residueNumber();
    }
    private static String shell(List<LigandContact> contacts) {
        return contacts.stream().filter(c -> c.distance() > 4.5).map(c -> Integer.toString(c.residue().residueNumber()))
                .distinct().sorted().collect(Collectors.joining(";"));
    }
    private static String sector(List<LigandContact> contacts, Set<Integer> sector) {
        return contacts.stream().filter(c -> c.distance() <= 4.5 && sector.contains(c.residue().residueNumber()))
                .map(c -> Integer.toString(c.residue().residueNumber())).distinct().sorted().collect(Collectors.joining(";"));
    }

    private static long countLedgerRows(Path path) throws IOException {
        try (var lines = Files.lines(path)) { return Math.max(0, lines.count() - 1); }
    }
    private static Map<String, String> acceptorsFromLedger(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        if (lines.isEmpty()) throw new IOException("Empty authoritative ledger");
        List<String> header = csvFields(lines.getFirst());
        int species = header.indexOf("species_id"), acceptor = header.indexOf("acceptor_atom");
        if (species < 0 || acceptor < 0) throw new IOException("Ledger lacks species_id/acceptor_atom");
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            List<String> fields = csvFields(line);
            if (fields.size() != header.size()) throw new IOException("Malformed ledger row");
            String previous = result.putIfAbsent(fields.get(species), fields.get(acceptor));
            if (previous != null && !previous.equals(fields.get(acceptor)))
                throw new IOException("Inconsistent acceptor mapping for " + fields.get(species));
        }
        return Map.copyOf(result);
    }
    private static List<String> csvFields(String line) throws IOException {
        List<String> fields = new ArrayList<>(); StringBuilder field = new StringBuilder(); boolean quoted = false;
        for (int i=0;i<line.length();i++) {
            char c=line.charAt(i);
            if (c=='\"') {
                if (quoted && i+1<line.length() && line.charAt(i+1)=='\"') { field.append('\"'); i++; }
                else quoted=!quoted;
            } else if (c==',' && !quoted) { fields.add(field.toString()); field.setLength(0); }
            else field.append(c);
        }
        if (quoted) throw new IOException("Unterminated quoted CSV field");
        fields.add(field.toString()); return fields;
    }
    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) { input.transferTo(new java.io.OutputStream() {
                @Override public void write(int value) { digest.update((byte)value); }
                @Override public void write(byte[] bytes, int off, int len) { digest.update(bytes, off, len); }
            }); }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static String poseCsv(List<PoseRow> rows) {
        StringBuilder out = new StringBuilder(String.join(",", HEADER)).append('\n');
        rows.forEach(row -> out.append(row.csv())); return out.toString();
    }
    private void writeJsonAtomic(Path target, Object value) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        mapper.writeValue(temporary.toFile(), value); move(temporary, target);
    }
    private static void writeAtomic(Path target, String content) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8); move(temporary, target);
    }
    private static void move(Path from, Path to) throws IOException {
        try { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING); }
    }
    private static String q(Object value) {
        if (value == null || value instanceof Double d && !Double.isFinite(d)) return "";
        String text = value.toString(); return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private record ReceptorParts(Structure protein, Structure sam, Set<String> samResidues) {}
    private record Geometry(String mapping, String atom, Double distance, Double angle, String screen) {}
    private record PoseRow(String runId,String receptorId,String speciesId,int seed,int model,Double affinity,
                           Double rmsdLb,Double rmsdUb,String c35,String c40,String c45,String shell,
                           String s1,String s2,String s3,String s4,List<Double> residueDistances,int samContacts,
                           int proteinClashes,int samClashes,double isolatedSasa,double boundSasa,double burial,
                           Geometry geometry,String receipt,String posesSha) {
        String csv() {
            List<Object> values=new ArrayList<>(List.of(runId,receptorId,speciesId,seed,model));
            values.add(affinity);values.add(rmsdLb);values.add(rmsdUb);values.addAll(List.of(c35,c40,c45,shell,s1,s2,s3,s4));
            values.addAll(residueDistances);values.addAll(List.of(samContacts,proteinClashes,samClashes,isolatedSasa,boundSasa,burial,
                    geometry.mapping,geometry.atom));values.add(geometry.distance);values.add(geometry.angle);values.addAll(List.of(geometry.screen,
                    "RAW_COMPUTATIONAL_EVIDENCE",false,receipt,posesSha));
            return values.stream().map(Mettl7IncrementalPosePostProcessor::q).collect(Collectors.joining(","))+"\n";
        }
    }
    public record Summary(long expectedRuns,int receiptsObserved,int completedRuns,int validRuns,int failedRuns,
                          int invalidRuns,long remainingRuns,int rawPoseRows,boolean biologicalConclusionAuthorized,
                          String interpretationBoundary) {}
}
