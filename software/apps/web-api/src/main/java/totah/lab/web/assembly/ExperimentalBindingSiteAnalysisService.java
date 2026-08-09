package totah.lab.web.assembly;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import totah.lab.athena.pocket.component.ComponentPocketRelationshipClass;
import totah.lab.athena.pocket.component.ExperimentalBindingSiteGrouper;
import totah.lab.athena.pocket.component.ExperimentalBindingSiteGrouping;
import totah.lab.athena.pocket.component.ExperimentalBindingSiteGroupingRule;
import totah.lab.athena.pocket.component.ExperimentalSitePocket;
import totah.lab.athena.pocket.component.PocketSphere;
import totah.lab.gaia.geometry.Point3D;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Builds canonical-site evidence in memory before persistence is enabled. */
@Service
public class ExperimentalBindingSiteAnalysisService {
    public static final String METHOD = "LIGAND_CENTERED_FPOCKET_GROUPING";
    public static final String METHOD_VERSION = "1.0";

    private static final double DIRECT_CONTACT_ANGSTROM = 4.0;
    private static final double NEAR_SHELL_ANGSTROM = 6.0;
    private static final double SPHERE_NEAR_SHELL_ANGSTROM = 2.0;

    private final JdbcTemplate jdbc;
    private final String schema;
    private final ExperimentalBindingSiteGroupingRule rule =
            ExperimentalBindingSiteGroupingRule.defaults();
    private final ExperimentalBindingSiteGrouper grouper =
            new ExperimentalBindingSiteGrouper(rule);

    public ExperimentalBindingSiteAnalysisService(JdbcTemplate jdbc,
            @Value("${totah.persistence.docking-schema:docking}") String schema) {
        this.jdbc = jdbc;
        if (!schema.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("Invalid persistence schema");
        }
        this.schema = schema;
    }

    public List<OccurrenceRef> meaningfulOccurrences(List<String> pdbIds,
            List<String> componentIds) {
        String selection = pdbIds.isEmpty() ? "" : " AND a.pdb_id IN ("
                + String.join(",", java.util.Collections.nCopies(
                        pdbIds.size(), "?")) + ")";
        String componentSelection = componentIds.isEmpty() ? ""
                : " AND o.component_id IN (" + String.join(",",
                java.util.Collections.nCopies(componentIds.size(), "?")) + ")";
        List<Object> arguments = new ArrayList<>(pdbIds);
        arguments.addAll(componentIds);
        return jdbc.query("""
                SELECT o.id,a.pdb_id,a.assembly_id,o.component_id,o.category
                FROM %s.assembly_component_occurrence o
                JOIN %s.experimental_assembly a ON a.id=o.assembly_id
                WHERE o.category IN ('COFACTOR','ORGANIC_LIGAND')%s%s
                ORDER BY a.pdb_id,a.assembly_id,o.id
                """.formatted(schema, schema, selection, componentSelection), (rs, row) ->
                new OccurrenceRef(rs.getLong(1), rs.getString(2),
                        rs.getString(3), rs.getString(4), rs.getString(5)),
                arguments.toArray());
    }

    public OccurrenceAnalysis analyze(OccurrenceRef occurrence) {
        List<Atom> ligand = ligandAtoms(occurrence.id());
        List<CandidateRow> rows = jdbc.query("""
                SELECT an.pocket_id,p.pocket_number,p.fpocket_rank,
                       an.relationship_class,an.heavy_atom_inside_fraction,
                       an.minimum_pocket_atom_distance,
                       an.minimum_alpha_sphere_surface_distance,
                       an.component_centroid_pocket_centroid_distance
                FROM %s.component_pocket_annotation an
                JOIN %s.assembly_pocket p ON p.id=an.pocket_id
                WHERE an.occurrence_id=?
                  AND an.relationship_class<>'NOT_ASSOCIATED'
                ORDER BY p.pocket_number
                """.formatted(schema, schema), (rs, row) -> new CandidateRow(
                rs.getLong(1), rs.getInt(2), rs.getInt(3),
                ComponentPocketRelationshipClass.valueOf(rs.getString(4)),
                rs.getDouble(5), rs.getDouble(6), rs.getDouble(7),
                rs.getDouble(8)), occurrence.id());
        List<ExperimentalSitePocket> candidates = rows.stream()
                .map(row -> candidate(row, ligand)).toList();
        ExperimentalBindingSiteGrouping grouping = grouper.group(candidates);
        return new OccurrenceAnalysis(occurrence, ligand.size(),
                centroid(ligand.stream().map(Atom::position).toList()),
                candidates, grouping);
    }

    public ExperimentalBindingSiteGroupingRule rule() {
        return rule;
    }

    private ExperimentalSitePocket candidate(CandidateRow row,
            List<Atom> ligand) {
        List<PocketAtom> pocketAtoms = jdbc.query("""
                SELECT source_atom_id,element,auth_asym_id,residue_number,
                       insertion_code,residue_name,x,y,z
                FROM %s.assembly_pocket_atom WHERE pocket_id=?
                """.formatted(schema), (rs, number) -> new PocketAtom(
                rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getInt(4), normalize(rs.getString(5)), rs.getString(6),
                new Point3D(rs.getDouble(7), rs.getDouble(8), rs.getDouble(9)),
                row.pocketId()),
                row.pocketId());
        List<PocketSphere> spheres = jdbc.query("""
                SELECT x,y,z,radius FROM %s.assembly_pocket_alpha_sphere
                WHERE pocket_id=? ORDER BY sphere_number
                """.formatted(schema), (rs, number) -> new PocketSphere(
                new Point3D(rs.getDouble(1), rs.getDouble(2), rs.getDouble(3)),
                rs.getDouble(4)), row.pocketId());
        Set<String> residues = new LinkedHashSet<>(), direct = new LinkedHashSet<>(),
                near = new LinkedHashSet<>(), covered = new LinkedHashSet<>(),
                nearAtoms = new LinkedHashSet<>(), contacted = new LinkedHashSet<>(),
                chains = new LinkedHashSet<>();
        for (PocketAtom pocket : pocketAtoms) {
            String residue = residueId(pocket);
            residues.add(residue);
            chains.add(pocket.chain());
            for (Atom atom : ligand) {
                double distance = distance(atom.position(), pocket.position());
                if (distance <= NEAR_SHELL_ANGSTROM) near.add(residue);
                if (distance <= DIRECT_CONTACT_ANGSTROM) {
                    direct.add(residue);
                    contacted.add(atom.id());
                }
            }
        }
        for (Atom atom : ligand) for (PocketSphere sphere : spheres) {
            double distance = distance(atom.position(), sphere.center());
            if (distance <= sphere.radius()) covered.add(atom.id());
            if (distance <= sphere.radius() + SPHERE_NEAR_SHELL_ANGSTROM) {
                nearAtoms.add(atom.id());
            }
        }
        Point3D centroid = centroid(spheres.stream().map(PocketSphere::center)
                .toList());
        Set<String> targets = new LinkedHashSet<>(jdbc.queryForList("""
                SELECT DISTINCT t.uniprot_id
                FROM %s.assembly_pocket_target apt
                JOIN public.targets t ON t.id=apt.target_id
                WHERE apt.pocket_id=? AND EXISTS (
                    SELECT 1 FROM %s.assembly_polymer_target pt
                    WHERE pt.target_id=apt.target_id AND pt.is_human IS TRUE)
                ORDER BY t.uniprot_id
                """.formatted(schema, schema), String.class, row.pocketId()));
        return new ExperimentalSitePocket(row.pocketId(), row.pocketNumber(),
                row.fpocketRank(), row.relationship(), row.occupancyFraction(),
                row.minimumProteinDistance(), row.minimumSphereDistance(),
                row.ligandCentroidDistance(), centroid, spheres, residues,
                direct, near, covered, nearAtoms, contacted, ligand.stream()
                .collect(java.util.stream.Collectors.toMap(Atom::id,
                        Atom::position)), chains, targets);
    }

    private List<Atom> ligandAtoms(long occurrence) {
        return jdbc.query("""
                SELECT source_atom_id,element,x,y,z
                FROM %s.assembly_component_atom WHERE occurrence_id=?
                ORDER BY source_atom_id
                """.formatted(schema), (rs, row) -> new Atom(rs.getString(1),
                rs.getString(2), new Point3D(rs.getDouble(3), rs.getDouble(4),
                rs.getDouble(5))), occurrence).stream().filter(Atom::heavy).toList();
    }

    private static Point3D centroid(List<Point3D> points) {
        double x = 0, y = 0, z = 0;
        for (Point3D point : points) { x += point.x(); y += point.y(); z += point.z(); }
        return new Point3D(x / points.size(), y / points.size(), z / points.size());
    }

    private static double distance(Point3D a, Point3D b) {
        double x=a.x()-b.x(), y=a.y()-b.y(), z=a.z()-b.z();
        return Math.sqrt(x*x+y*y+z*z);
    }

    private static String residueId(PocketAtom atom) {
        return atom.chain()+":"+atom.residueNumber()+":"+atom.insertionCode()
                +":"+atom.residueName();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "" : value;
    }

    public record OccurrenceRef(long id, String pdbId, String assemblyId,
            String componentId, String category) {}
    public record OccurrenceAnalysis(OccurrenceRef occurrence,
            int ligandHeavyAtoms, Point3D ligandCentroid,
            List<ExperimentalSitePocket> candidates,
            ExperimentalBindingSiteGrouping grouping) {}
    private record CandidateRow(long pocketId, int pocketNumber, int fpocketRank,
            ComponentPocketRelationshipClass relationship,
            double occupancyFraction, double minimumProteinDistance,
            double minimumSphereDistance, double ligandCentroidDistance) {}
    private record Atom(String id, String element, Point3D position) {
        boolean heavy() {
            String symbol=element.toUpperCase(Locale.ROOT);
            return !symbol.equals("H")&&!symbol.equals("D")&&!symbol.equals("T");
        }
    }
    private record PocketAtom(String id, String element, String chain,
            int residueNumber, String insertionCode, String residueName,
            Point3D position, long pocketId) {}
}
