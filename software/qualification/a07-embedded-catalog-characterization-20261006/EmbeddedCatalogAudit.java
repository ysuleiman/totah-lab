import com.actelion.research.chem.*;
import com.actelion.research.chem.ugly.PainsDetector;
import totah.lab.athena.design.backend.ocl.OclMolecularBackend;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Research characterization only: private catalog inspection is never a production adapter. */
public class EmbeddedCatalogAudit {
    static String hash(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
    public static void main(String[] args) throws Exception {
        var field = PainsDetector.class.getDeclaredField("PAINS"); field.setAccessible(true);
        var catalog = (String[][]) field.get(null);
        var backend = new OclMolecularBackend(); var target = backend.decodeStructure("SMILES", "C");
        var rows = new ArrayList<String>();
        rows.add("index\tlabel_sha256\tidcode_sha256\tsmarts_sha256\texact_query_roundtrip\tb00_compile_probe\tnote");
        int exact = 0, accepted = 0; var whole = new StringBuilder();
        for (int i = 0; i < catalog.length; i++) {
            var entry = catalog[i];
            for (String value : entry) whole.append(value.length()).append(':').append(value);
            var original = new IDCodeParser(false).getCompactMolecule(entry[0]);
            String smarts = IsomericSmilesCreator.createSmarts(original);
            boolean same = false; String note = "";
            try {
                var parsed = new StereoMolecule();
                var parser = new SmilesParser(SmilesParser.SMARTS_MODE_IS_SMARTS | SmilesParser.MODE_CREATE_SMARTS_WARNING);
                parser.parse(parsed, smarts);
                same = parser.getSmartsWarning().isEmpty() && new Canonizer(original).getIDCode().equals(new Canonizer(parsed).getIDCode());
                if (!same) note = "query representation changed or parser warning";
            } catch (Exception ex) { note = "query roundtrip rejected: " + ex.getClass().getSimpleName(); }
            if (same) exact++;
            String status;
            try { backend.match(smarts, target); status = "ACCEPTED"; accepted++; }
            catch (Exception ex) { status = "CHECKED_REJECTION"; note += "; " + ex.getClass().getSimpleName(); }
            rows.add(i+"\t"+hash(entry[1])+"\t"+hash(entry[0])+"\t"+hash(smarts)+"\t"+same+"\t"+status+"\t"+note);
        }
        Path output = Path.of(args[0]); Files.createDirectory(output);
        Files.write(output.resolve("rows.tsv"), rows);
        Files.writeString(output.resolve("summary.txt"), "entries="+catalog.length+"\norderedCatalogSha256="+hash(whole.toString())+"\nexactQueryRoundtrip="+exact+"\nb00CompilationAccepted="+accepted+"\nNo scientific qualification. Methane is only a valid target for exercising the existing query compiler.\n");
    }
}
