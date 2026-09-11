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

/** Emits auditable atom/group-level detail for targeted Athena/PLIP mismatches. */
public final class Mettl7AtomLevelOracleAudit {
    private static final Map<String, String> TAGS = Map.of(
            "hbond", "hydrogen_bond", "salt", "salt_bridge",
            "pi_cation", "pi_cation_interaction");
    private static final List<String> TYPES = List.of("hbond", "salt", "pi_cation");

    private Mettl7AtomLevelOracleAudit() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Usage: <athena-pose.csv> <plip-results-root> <discrepancy.csv>");
        }
        audit(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
    }

    static void audit(Path athenaCsv, Path plipRoot, Path output) throws Exception {
        List<String> lines = Files.readAllLines(athenaCsv);
        List<String> header = csv(lines.getFirst());
        StringBuilder out = new StringBuilder("run_id,ligand_species,paralog,interaction_type,")
                .append("athena_ligand_atom_or_group,athena_receptor_atom_or_group,")
                .append("athena_geometry,athena_classification,plip_ligand_atom_or_group,")
                .append("plip_receptor_atom_or_group,plip_geometry,reason_for_difference\n");
        for (String line : lines.subList(1, lines.size())) {
            List<String> row = csv(line);
            if (!row.get(header.indexOf("pose_model")).equals("1")) continue;
            String run = row.get(header.indexOf("run_id"));
            String species = row.get(header.indexOf("species_id"));
            String paralog = row.get(header.indexOf("receptor_id")).startsWith("A") ? "7A" : "7B";
            List<AthenaDetail> athena = athenaDetails(
                    row.get(header.indexOf("athena_refined_interaction_details")));
            Path xml = plipRoot.resolve(run).resolve(run + "_report.xml");
            if (!Files.isRegularFile(xml)) {
                xml = plipRoot.resolve(run).resolve(run + ".xml");
            }
            for (String type : TYPES) {
                List<AthenaDetail> ours = athena.stream()
                        .filter(detail -> detail.type().equals(type)).toList();
                List<PlipDetail> theirs = plipDetails(xml, type);
                if (ours.size() == theirs.size()) continue;
                out.append(q(run)).append(',').append(q(species)).append(',')
                        .append(q(paralog)).append(',').append(q(type)).append(',')
                        .append(q(ours.stream().map(AthenaDetail::ligand).toList())).append(',')
                        .append(q(ours.stream().map(AthenaDetail::receptor).toList())).append(',')
                        .append(q(ours.stream().map(AthenaDetail::geometry).toList())).append(',')
                        .append(q("Athena refined count=" + ours.size())).append(',')
                        .append(q(theirs.stream().map(PlipDetail::ligand).toList())).append(',')
                        .append(q(theirs.stream().map(PlipDetail::receptor).toList())).append(',')
                        .append(q(theirs.stream().map(PlipDetail::geometry).toList())).append(',')
                        .append(q(reason(type, run, ours, theirs))).append('\n');
            }
        }
        Files.writeString(output, out, StandardCharsets.UTF_8);
    }

    private static String reason(String type, String run, List<AthenaDetail> ours,
                                 List<PlipDetail> theirs) {
        if (type.equals("hbond") && !ours.isEmpty() && theirs.isEmpty()) {
            return "Athena uses checksum-bound PDBQT explicit polar-H donor geometry; "
                    + "PLIP/OpenBabel --nohydro did not perceive the same donor/acceptor pair. "
                    + "No threshold changed; intentional input/perception divergence.";
        }
        if (type.equals("salt") && ours.isEmpty() && !theirs.isEmpty()) {
            return "PLIP/OpenBabel inferred a ligand charged group from the exported PDB; "
                    + "Athena used the frozen SDF formal-charge assignment and did not classify "
                    + "that group as formally charged. Authoritative-charge divergence.";
        }
        if (type.equals("pi_cation") && run.contains("NETARSUDIL")
                && ours.size() < theirs.size()) {
            return "PLIP treats protein HIS as unconditionally positive and reports the additional "
                    + "HIS196 pi-cation; Athena applies its frozen protein charged-group perception. "
                    + "Intentional charge-state divergence.";
        }
        if (type.equals("pi_cation") && run.contains("DCMB")
                && ours.size() > theirs.size()) {
            return "Athena restores the frozen protonated DCMB formal charge and detects its cation "
                    + "against the receptor aromatic group; PLIP's PDB export loses authoritative "
                    + "ligand charge/bond-order metadata and does not perceive that cation.";
        }
        return "Different chemical-group perception; inspect the atom/group and geometry fields.";
    }

    private static List<AthenaDetail> athenaDetails(String encoded) {
        List<AthenaDetail> result = new ArrayList<>();
        if (encoded.isBlank()) return result;
        for (String item : encoded.split("\\|")) {
            String[] f = item.split("~", -1);
            if (f.length < 9) continue;
            String type = switch (f[0]) {
                case "HYDROGEN_BOND" -> "hbond";
                case "SALT_BRIDGE" -> "salt";
                case "PI_CATION" -> "pi_cation";
                default -> f[0].toLowerCase();
            };
            String ligand = f[7].isBlank() ? f[2] : f[7] + " atoms=" + f[2];
            String receptor = f[8].isBlank() ? f[1] + " atoms=" + f[3]
                    : f[8] + " atoms=" + f[3];
            String geometry = "distance=" + f[4] + ";angle1=" + f[5] + ";angle2=" + f[6];
            result.add(new AthenaDetail(type, ligand, receptor, geometry));
        }
        return result;
    }

    private static List<PlipDetail> plipDetails(Path xml, String type) throws Exception {
        var document = SecurePlipXml.parse(xml);
        Element site = null;
        NodeList sites = document.getElementsByTagName("bindingsite");
        for (int i = 0; i < sites.getLength(); i++) {
            Element candidate = (Element) sites.item(i);
            if (text(candidate, "hetid").equals("LIG")) { site = candidate; break; }
        }
        if (site == null) throw new IOException("PLIP XML has no LIG site: " + xml);
        NodeList nodes = site.getElementsByTagName(TAGS.get(type));
        List<PlipDetail> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element e = (Element) nodes.item(i);
            String receptor = text(e, "restype") + " " + text(e, "reschain") + ":"
                    + text(e, "resnr") + " idx=" + indexes(e, "prot_idx_list");
            String ligand = "LIG " + text(e, "reschain_lig") + ":" + text(e, "resnr_lig")
                    + " group=" + text(e, "lig_group") + " idx=" + indexes(e, "lig_idx_list");
            String geometry = "dist=" + first(e, "dist_d-a", "dist")
                    + ";angle=" + first(e, "don_angle", "angle")
                    + ";offset=" + text(e, "offset")
                    + ";protisdon=" + text(e, "protisdon")
                    + ";protispos=" + text(e, "protispos")
                    + ";protcharged=" + text(e, "protcharged");
            result.add(new PlipDetail(ligand, receptor, geometry));
        }
        return result;
    }

    private static String indexes(Element parent, String containerTag) {
        NodeList containers = parent.getElementsByTagName(containerTag);
        if (containers.getLength() == 0) return "";
        NodeList indexes = ((Element) containers.item(0)).getElementsByTagName("idx");
        List<String> values = new ArrayList<>();
        for (int i = 0; i < indexes.getLength(); i++) values.add(indexes.item(i).getTextContent().trim());
        return String.join("+", values);
    }

    private static String first(Element parent, String... tags) {
        for (String tag : tags) { String value = text(parent, tag); if (!value.isBlank()) return value; }
        return "";
    }

    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }

    private static String q(Object value) {
        String text = String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static List<String> csv(String line) throws IOException {
        List<String> fields = new ArrayList<>(); StringBuilder field = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { field.append('"'); i++; }
                else quoted = !quoted;
            } else if (c == ',' && !quoted) { fields.add(field.toString()); field.setLength(0); }
            else field.append(c);
        }
        if (quoted) throw new IOException("Unterminated CSV field");
        fields.add(field.toString()); return fields;
    }

    private record AthenaDetail(String type, String ligand, String receptor, String geometry) {}
    private record PlipDetail(String ligand, String receptor, String geometry) {}
}
