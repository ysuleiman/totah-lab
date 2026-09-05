package totah.lab.athena.regression;

import org.junit.jupiter.api.Test;
import totah.lab.athena.interaction.ContactMatrix;
import totah.lab.athena.interaction.InteractionProfile;
import totah.lab.athena.interaction.InteractionProfiler;
import totah.lab.athena.interaction.InteractionType;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Category 8: 74-ligand docking selectivity inventory. The historical
 * pose geometry lives only in PostgreSQL
 * ({@code docking.pose_residue_contact}); no PDBQT exports exist in-repo,
 * so per-pose geometry is NOT_COMPUTABLE_GEOMETRY_DB_ONLY. What is
 * regression-tested here is the aggregation-path compatibility: a
 * {@link ContactMatrix} built from a representative profile must expose
 * the granularity the historical pipeline consumed (per-residue contact
 * counts + minimum distances, residue keyed by chain:number), rendered
 * as CSV with one column per {@link InteractionType}.
 */
class ContactMatrixCompatibilityTest {

    private static final String CATEGORY = "docking_inventory_74_ligand";

    @Test
    void contactMatrixSchemaMatchesHistoricalGranularity() {
        InteractionProfile profile = new InteractionProfiler().profile(
                receptor(), ligand());
        ContactMatrix matrix = ContactMatrix.of(profile);

        // Schema: header row of interaction type names in enum order,
        // one row per residue keyed chain:number.
        String csv = matrix.toCsv();
        String expectedHeader = "residue,"
                + "HYDROGEN_BOND,SALT_BRIDGE,HYDROPHOBIC_CONTACT,"
                + "PI_STACK_PARALLEL,PI_STACK_T_SHAPED,PI_CATION,"
                + "HALOGEN_BOND";
        assertThat(csv.lines().findFirst().orElseThrow())
                .isEqualTo(expectedHeader);
        assertThat(matrix.rows()).containsExactly(new ResidueId("A", 10, null));
        ContactMatrix.Cell cell = matrix.cell(
                new ResidueId("A", 10, null), InteractionType.HYDROPHOBIC_CONTACT);
        // Historical pose_residue_contact: (residue_id, atom_contact_count,
        // min_distance); the cell carries exactly count + min distance.
        assertThat(cell.count()).isEqualTo(1);
        assertThat(cell.minDistanceAngstroms()).isEqualTo(3.5);
        assertThat(csv).contains("A:10");

        RegressionHarness.record(CATEGORY, "contact_matrix_csv_schema",
                "docking.pose_residue_contact(pose_id,residue_id,"
                        + "atom_contact_count,min_distance), contact <= 4.0 A",
                "ContactMatrix.toCsv: residue x InteractionType count "
                        + "matrix, per-cell (count, minDistance); residue "
                        + "keyed chain:number",
                "cell granularity matches the historical per-residue "
                        + "(count, min_distance) pair with an added "
                        + "interaction-type dimension; cutoff differs (ours "
                        + "per-type PLIP thresholds, historical flat 4.0 A)",
                "REPRODUCED");
        RegressionHarness.record(CATEGORY,
                "per_pose_contact_sets_74_ligands",
                "matched_pose_contact_sets.csv: 148 rows (74 ligands x 2 "
                        + "enzymes), PoseContactCalculator.CUTOFF_ANGSTROM "
                        + "= 4.0",
                "",
                "pose geometry exists only as PostgreSQL rows in "
                        + "totah_lab_db (no PDBQT exports in-repo); "
                        + "representative subset for a future DB export: "
                        + "864/CHEMBL4436028, BIX 01294, MCULE-2135392775, "
                        + "MCULE-4144593857, METTL7-BRICS-0001, "
                        + "METTL7-BRICS-0034 (empty-set control)",
                "NOT_COMPUTABLE_GEOMETRY_DB_ONLY");
    }

    @Test
    void notComputableBookkeeping() {
        RegressionHarness.record("tsl_nac_geometry",
                "placement_search_counts_14400_per_system",
                "matrix_summary.csv: 14400 tested per system, accepted "
                        + "5/6/5/5/5/7/1/2",
                "",
                "the 8-conformer x 3-distance x 600-rotation RDKit ETKDG "
                        + "search is not recomputable in Java; counts are "
                        + "golden constants",
                "NOT_COMPUTABLE_RDKIT_SEARCH");
        RegressionHarness.record("stage12d_brics",
                "brics_placement_generation",
                "96 fragments, 0 retained Region-1 placements",
                "",
                "RDKit BRICS cleavage + ETKDG conformers not available in "
                        + "Java; only the frozen anchor geometry is "
                        + "regression-tested",
                "NOT_COMPUTABLE_RDKIT_BRICS");
        RegressionHarness.record("netarsudil_nac_and_c202_c203",
                "dcmb_amine_to_sam_methyl_report_constants",
                "3.6 A (7A) / 7.6 A (7B)",
                "",
                "exist only as report/CSV prose "
                        + "(dcmb_vs_netarsudil_descriptor_matrix.csv "
                        + "SAM_RELATIVE_POSITION); golden constants",
                "NOT_COMPUTABLE_REPORT_ONLY");
        RegressionHarness.record("escape_route_analysis",
                "stage8_11_escape_connectivity",
                "",
                "",
                "EscapeRouteAnalyzer not regression-tested: historical "
                        + "stage8_11 escape/connectivity numbers depend on "
                        + "the full structural-design pipeline (RDKit "
                        + "conformers, fpocket spheres) and are not "
                        + "straightforwardly recomputable",
                "NOT_COMPUTABLE_PIPELINE_DEPENDENT");
    }

    /** One ALA with CB 3.5 A from the ligand carbon: one hydrophobic contact. */
    private static Structure receptor() {
        Atom cb = atom(1, "CB", 3.5, 0, 0);
        Atom ca = atom(2, "CA", 5.0, 0, 0);
        return new Structure(List.of(new Chain("A",
                List.of(new Residue("ALA", 10, List.of(ca, cb))))));
    }

    private static Structure ligand() {
        Atom c1 = atom(3, "C1", 0, 0, 0);
        return new Structure(List.of(new Chain("L",
                List.of(new Residue("LIG", 1, List.of(c1))))));
    }

    private static Atom atom(
            int serial, String name, double x, double y, double z) {
        return Atom.builder()
                .pdbSerial(serial)
                .name(name)
                .autoDockType("C")
                .position(new Point3D(x, y, z))
                .charge(0.0)
                .occupancy(1.0)
                .bFactor(0.0)
                .element(Element.C)
                .build();
    }
}
