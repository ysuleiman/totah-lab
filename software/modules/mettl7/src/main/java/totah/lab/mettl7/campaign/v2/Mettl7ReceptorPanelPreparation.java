package totah.lab.mettl7.campaign.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import totah.lab.mettl7.campaign.v2.ReceptorBackground.Paralog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Command-line preparation of the 16-receptor SAM-bound campaign panel. */
public final class Mettl7ReceptorPanelPreparation {
    private Mettl7ReceptorPanelPreparation() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: <repository-root> <output-directory>");
        }
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        Files.createDirectories(output);
        Path sourceA = root.resolve("analysis/dcmb/controlled_campaign/prepared/7A_WT_SAM_BOUND.pdbqt");
        Path sourceB = root.resolve("analysis/dcmb/controlled_campaign/prepared/7B_WT_SAM_BOUND.pdbqt");
        Mettl7ReceptorPanelBuilder builder = new Mettl7ReceptorPanelBuilder();
        List<Map<String, Object>> receipts = new ArrayList<>();
        for (ReceptorBackground receptor : Mettl7MechanisticMatrixV2Panel.receptors()) {
            Path source = receptor.paralog() == Paralog.METTL7A ? sourceA : sourceB;
            Path target = output.resolve(receptor.id() + "_SAM_BOUND.pdbqt");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("receptor_id", receptor.id());
            row.put("paralog", receptor.paralog().name());
            row.put("substitutions", receptor.substitutions());
            row.put("source", source.toString());
            row.put("source_sha256", sha256(source));
            if (receptor.substitutions().isEmpty()) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                row.put("construction", "BYTE_IDENTICAL_WT_COPY");
            } else {
                var receipt = builder.build(source, receptor, target);
                row.put("construction", "PROTEUS_FIXED_BACKBONE_LEAST_CLASH_ROTAMER");
                row.put("source_atom_count", receipt.sourceAtomCount());
                row.put("output_atom_count", receipt.outputAtomCount());
                row.put("sam_atom_count", receipt.samAtomCount());
                row.put("mutation_receipts", receipt.mutations());
            }
            row.put("output", target.toString());
            row.put("output_sha256", sha256(target));
            receipts.add(Map.copyOf(row));
        }
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(output.resolve("receptor_build_receipts.json").toFile(), receipts);
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                for (int read; (read = input.read(buffer)) >= 0;) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
