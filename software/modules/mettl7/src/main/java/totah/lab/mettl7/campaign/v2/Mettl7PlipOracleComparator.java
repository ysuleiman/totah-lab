package totah.lab.mettl7.campaign.v2;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Count-level targeted comparison against PLIP 3.0.1 XML oracle output. */
public final class Mettl7PlipOracleComparator {
    private static final List<String> TYPES = List.of("hbond", "salt", "hydrophobic",
            "pi_parallel", "pi_t", "pi_cation", "halogen");
    private Mettl7PlipOracleComparator() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException(
                "Usage: <athena-pose.csv> <plip-results-root> <comparison.csv>");
        compare(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
    }

    static void compare(Path athenaCsv, Path plipRoot, Path output) throws Exception {
        List<String> lines = Files.readAllLines(athenaCsv);
        List<String> header = csv(lines.getFirst());
        Map<String, Map<String, Integer>> athena = new LinkedHashMap<>();
        Map<String, Integer> hydrophobicRaw = new LinkedHashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            List<String> row = csv(line);
            if (!row.get(header.indexOf("pose_model")).equals("1")) continue;
            String run = row.get(header.indexOf("run_id"));
            Map<String, Integer> counts = new LinkedHashMap<>();
            Map<String, String> columns = Map.of("hbond", "athena_hbond_count",
                    "salt", "athena_salt_bridge_count", "hydrophobic", "athena_hydrophobic_refined_count",
                    "pi_parallel", "athena_pi_parallel_count", "pi_t", "athena_pi_t_count",
                    "pi_cation", "athena_pi_cation_count", "halogen", "athena_halogen_bond_count");
            for (String type : TYPES) counts.put(type,
                    Integer.parseInt(row.get(header.indexOf(columns.get(type)))));
            athena.put(run, counts);
            hydrophobicRaw.put(run, Integer.parseInt(
                    row.get(header.indexOf("athena_hydrophobic_raw_count"))));
        }
        StringBuilder out = new StringBuilder(
                "oracle_semantics,run_id,type,athena_raw_count,athena_refined_count,plip_count,count_match\n");
        for (Map.Entry<String, Map<String, Integer>> entry : athena.entrySet()) {
            Path xml = plipRoot.resolve(entry.getKey()).resolve(entry.getKey() + "_report.xml");
            if (!Files.isRegularFile(xml)) {
                xml = plipRoot.resolve(entry.getKey()).resolve(entry.getKey() + ".xml");
            }
            Map<String, Integer> plip = plipCounts(xml);
            for (String type : TYPES) out.append("PLIP_COUNT_ORACLE_COMPARISON,").append(entry.getKey()).append(',').append(type).append(',')
                    .append(type.equals("hydrophobic") ? hydrophobicRaw.get(entry.getKey())
                            : entry.getValue().get(type)).append(',')
                    .append(entry.getValue().get(type)).append(',').append(plip.get(type)).append(',')
                    .append(entry.getValue().get(type).equals(plip.get(type))).append('\n');
        }
        Files.writeString(output, out, StandardCharsets.UTF_8);
    }

    private static Map<String, Integer> plipCounts(Path xml) throws Exception {
        var document = SecurePlipXml.parse(xml);
        Element site = null;
        NodeList sites = document.getElementsByTagName("bindingsite");
        for (int i = 0; i < sites.getLength(); i++) {
            Element candidate = (Element) sites.item(i);
            if (text(candidate, "hetid").equals("LIG")) { site = candidate; break; }
        }
        if (site == null) throw new IOException("PLIP XML has no LIG binding site: " + xml);
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("hbond", site.getElementsByTagName("hydrogen_bond").getLength());
        result.put("salt", site.getElementsByTagName("salt_bridge").getLength());
        result.put("hydrophobic", site.getElementsByTagName("hydrophobic_interaction").getLength());
        int parallel = 0, t = 0;
        NodeList stacks = site.getElementsByTagName("pi_stack");
        for (int i = 0; i < stacks.getLength(); i++) {
            String type=text((Element) stacks.item(i),"type");
            if(type.equals("P"))parallel++;
            else if(type.equals("T"))t++;
            else throw new IOException("UNKNOWN_PLIP_PI_STACK_TYPE: "+type+" in "+xml);
        }
        result.put("pi_parallel", parallel); result.put("pi_t", t);
        result.put("pi_cation", site.getElementsByTagName("pi_cation_interaction").getLength());
        result.put("halogen", site.getElementsByTagName("halogen_bond").getLength());
        return result;
    }

    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }

    private static List<String> csv(String line) throws IOException {
        List<String> fields = new ArrayList<>(); StringBuilder field = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') { if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                field.append('"'); i++; } else quoted = !quoted;
            } else if (c == ',' && !quoted) { fields.add(field.toString()); field.setLength(0); }
            else field.append(c);
        }
        if (quoted) throw new IOException("Unterminated CSV field"); fields.add(field.toString()); return fields;
    }
}
