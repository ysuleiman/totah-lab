package totah.lab.mettl7.surface;

import totah.lab.athena.interaction.Interaction;
import totah.lab.athena.interaction.InteractionProfile;
import totah.lab.athena.interaction.InteractionProfiler;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.pdb.reader.PdbReader;
import totah.lab.hermes.file.pdbqt.PdbqtAtom;
import totah.lab.hermes.file.pdbqt.PdbqtFile;
import totah.lab.hermes.file.pdbqt.PdbqtGaiaMapper;
import totah.lab.hermes.file.pdbqt.PdbqtModel;
import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;
import totah.lab.hermes.file.sdf.reader.SdfLigandReader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Overlays canonical Athena interactions on frozen METTL7 SurfDiff evidence. */
public final class Mettl7LigandDifferentialOverlayCli {
    private Mettl7LigandDifferentialOverlayCli() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: <regression fixture root> <surface output>");
        }
        run(Path.of(args[0]), Path.of(args[1]));
    }

    public static void run(Path fixtures, Path output) throws IOException {
        Files.createDirectories(output);
        Map<Integer, SurfaceEvidence> aVsB = readSurface(
                output.resolve("METTL7A_VS_METTL7B_residue_scores.csv"));
        Map<Integer, SurfaceEvidence> bVsA = readSurface(
                output.resolve("METTL7B_VS_METTL7A_residue_scores.csv"));
        Path result = output.resolve("LIGAND_INTERACTION_DIFFERENTIAL_OVERLAY.csv");
        try (BufferedWriter writer = Files.newBufferedWriter(result)) {
            writer.write("anchor,paralog,family,pose_file,mode,perception_degraded,protein_chain,protein_residue,protein_name,interaction_type,distance_A,primary_angle_deg,secondary_angle_deg,protein_atoms,ligand_atoms,corresponding_paralog,corresponding_residue,corresponding_name,rup,rus,rss,rsasa,exposure_weight,environment_status,evidence_layer\n");
            writeNetarsudil(fixtures, writer, aVsB, bVsA);
            writeDcmb(fixtures, writer, aVsB, bVsA);
        }
    }

    private static void writeNetarsudil(Path root, BufferedWriter writer,
            Map<Integer, SurfaceEvidence> aVsB, Map<Integer, SurfaceEvidence> bVsA)
            throws IOException {
        Path n = root.resolve("netarsudil");
        Structure ligandTopology = new SdfLigandReader().read(
                n.resolve("netarsudil_CID66599893_neutral.sdf")).structure();
        profileAndWrite("NETARSUDIL", "B", "B_FAMILY_5",
                n.resolve("7B_neutral_seed483271.pdbqt"), 5,
                new PdbReader().read(n.resolve("METTL7B_SAM_TOPOLOGY_COMPLETE_EXACT_COORDS.pdb")),
                transplantByUniqueAtomName(ligandTopology, model(
                        new PdbqtReader().read(n.resolve("7B_neutral_seed483271.pdbqt")), 5)),
                bVsA, writer);
        Path aPose = n.resolve("corrected_7A_lowest_strain_mode15.pdbqt");
        profileAndWrite("NETARSUDIL", "A", "A_ADMISSIBLE_CONTROL", aPose, 1,
                proteinOnly(PdbqtGaiaMapper.toStructure(new PdbqtReader().read(
                        n.resolve("METTL7A_SAM_receptor.pdbqt")))),
                transplantByUniqueAtomName(ligandTopology,
                        new PdbqtReader().read(aPose).firstModel()), aVsB, writer);
    }

    private static void writeDcmb(Path root, BufferedWriter writer,
            Map<Integer, SurfaceEvidence> aVsB, Map<Integer, SurfaceEvidence> bVsA)
            throws IOException {
        Path stage = root.resolve("stage12j");
        Structure receptorA = proteinOnly(PdbqtGaiaMapper.toStructure(
                new PdbqtReader().read(stage.resolve("7A_WT_SAM_BOUND.pdbqt"))));
        Structure receptorB = proteinOnly(PdbqtGaiaMapper.toStructure(
                new PdbqtReader().read(stage.resolve("7B_WT_SAM_BOUND.pdbqt"))));
        for (Map<String, String> row : readCsv(stage.resolve("family_results.csv"))) {
            String system = row.get("system");
            if (!Set.of("7A_WT", "7B_WT").contains(system)) continue;
            String paralog = system.substring(1, 2);
            String family = paralog + "_" + row.get("enantiomer") + row.get("family");
            Path pose = stage.resolve("raw").resolve(system + "_"
                    + row.get("enantiomer") + "_s" + row.get("representative_seed")
                    + ".pdbqt");
            int mode = Integer.parseInt(row.get("representative_mode"));
            Structure ligand = PdbqtGaiaMapper.toLigand(
                    model(new PdbqtReader().read(pose), mode), family).structure();
            profileAndWrite("DCMB", paralog, family, pose, mode,
                    "A".equals(paralog) ? receptorA : receptorB, ligand,
                    "A".equals(paralog) ? aVsB : bVsA, writer);
        }
    }

    private static void profileAndWrite(String anchor, String paralog, String family,
            Path pose, int mode, Structure receptor, Structure ligand,
            Map<Integer, SurfaceEvidence> surface, BufferedWriter writer) throws IOException {
        InteractionProfile profile = new InteractionProfiler().profile(receptor, ligand);
        for (Interaction interaction : profile.interactions()) {
            if (!"A".equals(interaction.residue().chainId())) continue;
            int number = interaction.residue().residueNumber();
            SurfaceEvidence evidence = surface.get(number);
            Residue proteinResidue = residue(receptor, number);
            String status = evidence == null ? "UNAVAILABLE"
                    : evidence.rup() == 0.0 ? "PRESERVED" : "ALTERED";
            writer.write(csv(anchor, paralog, family, pose.toString(), mode,
                    profile.anyPerceptionDegraded(), "A", number,
                    proteinResidue.getName(), interaction.type(),
                    interaction.distanceAngstroms(), interaction.primaryAngleDegrees(),
                    interaction.secondaryAngleDegrees(), atoms(interaction.proteinAtoms()),
                    atoms(interaction.ligandAtoms()), other(paralog),
                    evidence == null ? "" : evidence.subjectNumber(),
                    evidence == null ? "" : evidence.subjectName(),
                    evidence == null ? "" : evidence.rup(),
                    evidence == null ? "" : evidence.rus(),
                    evidence == null ? "" : evidence.rss(),
                    evidence == null ? "" : evidence.rsasa(),
                    evidence == null ? "" : evidence.exposureWeight(), status,
                    "PROTEIN_DIFFERENTIAL_EVIDENCE|LIGAND_INTERACTION_EVIDENCE"));
        }
    }

    private static Structure proteinOnly(Structure structure) {
        List<Chain> chains = new ArrayList<>();
        for (Chain chain : structure.getChains()) {
            List<Residue> residues = chain.residues().stream()
                    .filter(residue -> !"SAM".equals(residue.getName())).toList();
            if (!residues.isEmpty()) chains.add(new Chain(chain.id(), residues));
        }
        return new Structure(chains, structure.bonds(), structure.getConnectivityMetadata());
    }

    private static Structure transplantByUniqueAtomName(Structure source, PdbqtModel pose)
            throws IOException {
        Map<String, PdbqtAtom> byName = new LinkedHashMap<>();
        for (PdbqtAtom atom : pose.atoms()) {
            if (byName.put(atom.atomName(), atom) != null) {
                throw new IOException("duplicate pose atom name " + atom.atomName());
            }
        }
        List<Chain> chains = new ArrayList<>();
        Set<String> used = new java.util.LinkedHashSet<>();
        for (Chain chain : source.getChains()) {
            List<Residue> residues = new ArrayList<>();
            for (Residue residue : chain.residues()) {
                List<Atom> atoms = new ArrayList<>();
                for (Atom atom : residue.getAtoms()) {
                    PdbqtAtom posed = byName.get(atom.getName());
                    if (posed == null) throw new IOException("missing pose atom " + atom.getName());
                    used.add(atom.getName());
                    atoms.add(atom.toBuilder().position(posed.position()).build());
                }
                residues.add(residue.toBuilder().atoms(atoms).build());
            }
            chains.add(new Chain(chain.id(), residues));
        }
        if (!used.equals(byName.keySet())) throw new IOException("pose/topology atom mismatch");
        return new Structure(chains, source.bonds(), source.getConnectivityMetadata());
    }

    private static PdbqtModel model(PdbqtFile file, int number) throws IOException {
        return file.models().stream().filter(row -> row.modelNumber() == number)
                .findFirst().orElseThrow(() -> new IOException("missing model " + number));
    }

    private static Residue residue(Structure structure, int number) throws IOException {
        return structure.getChains().stream().flatMap(c -> c.residues().stream())
                .filter(r -> r.getNumber() == number && !"SAM".equals(r.getName()))
                .findFirst().orElseThrow(() -> new IOException("missing receptor residue " + number));
    }

    private static Map<Integer, SurfaceEvidence> readSurface(Path path) throws IOException {
        LinkedHashMap<Integer, SurfaceEvidence> result = new LinkedHashMap<>();
        for (Map<String, String> row : readCsv(path)) {
            int query = Integer.parseInt(row.get("query_number"));
            String subject = row.get("subject_number");
            result.put(query, new SurfaceEvidence(subject,
                    row.get("subject_name"), Double.parseDouble(row.get("rup")),
                    Double.parseDouble(row.get("rus")), Double.parseDouble(row.get("rss")),
                    Double.parseDouble(row.get("query_rsasa")),
                    Double.parseDouble(row.get("exposure_weight"))));
        }
        return Map.copyOf(result);
    }

    private static List<Map<String, String>> readCsv(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        String[] headers = lines.getFirst().split(",", -1);
        List<Map<String, String>> rows = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] fields = line.split(",", -1);
            LinkedHashMap<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < headers.length; i++) row.put(headers[i], fields[i]);
            rows.add(Map.copyOf(row));
        }
        return List.copyOf(rows);
    }

    private static String atoms(List<Atom> atoms) {
        return atoms.stream().map(Atom::getName).collect(Collectors.joining(";"));
    }

    private static String other(String paralog) { return "A".equals(paralog) ? "B" : "A"; }

    private static String csv(Object... values) {
        return java.util.Arrays.stream(values).map(value -> value == null ? "" : value.toString())
                .collect(Collectors.joining(",")) + System.lineSeparator();
    }

    private record SurfaceEvidence(String subjectNumber, String subjectName,
            double rup, double rus, double rss, double rsasa, double exposureWeight) {
    }
}
