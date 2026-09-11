package totah.lab.mettl7.campaign.v2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Mettl7BInhibitorCandidateMinerTest {
    @TempDir Path temporaryDirectory;

    @Test
    void emitsTransparentBAndARecognitionDimensions() throws Exception {
        Path a=Files.createDirectory(temporaryDirectory.resolve("a"));
        Path b=Files.createDirectory(temporaryDirectory.resolve("b"));
        Path out=temporaryDirectory.resolve("out");
        String mh="receptor_id,paralog,receptor_mutations,compound_branch,species_id,recurrent_family_count,mean_burial_fraction,"
                +"sam_clash_free_fraction,mean_sam_contact_count_le_4p5,central_productive_sector_pose_fraction,directional_exit_sector_pose_fraction,near_attack_pass_fraction\n";
        Files.writeString(a.resolve("METTL7_V2_RECEPTOR_SPECIES_MECHANISTIC_MATRIX.csv"),mh
                +"A0,METTL7A,,METTL7_BRICS_0003,BRICS0003_NEUTRAL,1,0.2,1,0,1,0,0\n"
                +"B0,METTL7B,,METTL7_BRICS_0003,BRICS0003_NEUTRAL,3,0.6,1,1,1,0,0\n");
        String rh="receptor_id,species_id,dimension,residue,recurrent_across_seeds\n";
        Files.writeString(a.resolve("METTL7_V2_RESIDUE_INTERACTION_RECURRENCE.csv"),rh
                +"B0,BRICS0003_NEUTRAL,HYDROPHOBIC_CONTACT,A:196,true\n"
                +"B0,BRICS0003_NEUTRAL,HYDROGEN_BOND,A:203,true\n"
                +"B0,BRICS0003_NEUTRAL,SALT_BRIDGE,A:206,true\n"
                +"B0,BRICS0003_NEUTRAL,DIRECT_CONTACT_4P5,A:196,true\n");
        String dh="species_id,dimension,residue,a_pose_fraction,a_seed_count,b_pose_fraction,b_seed_count\n";
        Files.writeString(a.resolve("METTL7_V2_MATCHED_A_B_RESIDUE_INTERACTION_DELTAS.csv"),dh
                +"BRICS0003_NEUTRAL,HYDROPHOBIC_CONTACT,A:196,0,0,1,3\n");
        Files.writeString(b.resolve("METTL7_V2_MUTATION_TRANSFER_MATRIX.csv"),"species_id,transfer_class\n");

        Mettl7BInhibitorCandidateMiner.build(a,b,out);

        String matrix=Files.readString(out.resolve("METTL7B_SELECTIVE_INHIBITOR_EVIDENCE_MATRIX.csv"));
        assertTrue(matrix.contains("B_RECOGNITION_STRONG"));
        assertTrue(matrix.contains("A_RECOGNITION_WEAK"));
        assertTrue(matrix.contains("candidate only"));
    }
}
