package totah.lab.mettl7.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Executes the bounded A/B SAM topology reconstruction and writes provenance receipts. */
public final class Mettl7SamTopologyRepairCli {
    private Mettl7SamTopologyRepairCli() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: <repository-root> <output-directory>");
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        Files.createDirectories(output);
        Path canonical = root.resolve("software/modules/daedalus/src/test/resources/ligand/SAM.sdf");
        Path campaign = root.resolve("analysis/dcmb/controlled_campaign/prepared");
        Path docking = root.resolve("analysis/mettl7-netarsudil-autodock4-matched-rigid-2026-09-10");
        Mettl7SamTopologyRestorer restorer = new Mettl7SamTopologyRestorer();
        var a = restorer.restore(canonical, campaign.resolve("7A_SAM.sdf"),
                docking.resolve("prepared/METTL7A_SAM_rigid.pdbqt"),
                docking.resolve("results/topology_complete_athena/METTL7A_SAM_TOPOLOGY_COMPLETE_EXACT_AD4_COORDS.pdb"));
        var b = restorer.restore(canonical, campaign.resolve("7B_SAM.sdf"),
                docking.resolve("prepared/METTL7B_SAM_rigid.pdbqt"),
                root.resolve("software/modules/athena/src/test/resources/mettl7-v2-regression/netarsudil/"
                        + "METTL7B_SAM_TOPOLOGY_COMPLETE_EXACT_COORDS.pdb"));
        boolean matched = a.receipt().canonicalSamGraphHash().equals(b.receipt().canonicalSamGraphHash())
                && a.receipt().canonicalSamOclIdCode().equals(b.receipt().canonicalSamOclIdCode())
                && a.receipt().samBondCount() == b.receipt().samBondCount()
                && a.receipt().proteinSamCrossBondCount() == 0
                && b.receipt().proteinSamCrossBondCount() == 0;
        if (!matched) throw new IllegalStateException("A/B receptor topology semantics are not matched");
        ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        json.writeValue(output.resolve("METTL7A_SAM_TOPOLOGY_RECEIPT.json").toFile(), a.receipt());
        json.writeValue(output.resolve("METTL7B_SAM_TOPOLOGY_RECEIPT.json").toFile(), b.receipt());
        Map<String,Object> summary = new LinkedHashMap<>();
        summary.put("A_B_TOPOLOGY_SEMANTICS_MATCHED", true);
        summary.put("AD4_FALLBACK_ELIMINATED", true);
        summary.put("AUTHORITATIVE_SAM_GRAPH", "VALIDATED");
        summary.put("REJECTED_TOPOLOGY_SOURCE_DIRECT_USE", true);
        summary.put("NEW_DOCKING", false);
        summary.put("OPENMM", false);
        summary.put("MD", false);
        json.writeValue(output.resolve("METTL7_SAM_TOPOLOGY_REPAIR_SUMMARY.json").toFile(), summary);
    }
}
