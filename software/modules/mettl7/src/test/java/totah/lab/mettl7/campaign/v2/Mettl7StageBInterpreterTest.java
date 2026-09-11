package totah.lab.mettl7.campaign.v2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Mettl7StageBInterpreterTest {
    @TempDir Path temporaryDirectory;

    @Test
    void preservesDifferentialAndReciprocalMutationEvidence() throws Exception {
        Path stageA = Files.createDirectory(temporaryDirectory.resolve("a"));
        Path stageB = temporaryDirectory.resolve("b");
        Files.writeString(stageA.resolve("METTL7_V2_RESIDUE_INTERACTION_RECURRENCE.csv"), """
                receptor_id,species_id,compound_branch,dimension,residue,pose_fraction,seed_count
                A0,LIG,BRANCH,HYDROGEN_BOND,A:10,0.75,3
                B0,LIG,BRANCH,HYDROGEN_BOND,A:10,0.0,0
                A1,LIG,BRANCH,HYDROGEN_BOND,A:10,0.25,2
                B1,LIG,BRANCH,HYDROGEN_BOND,A:10,0.50,2
                """);
        String matrixHeader = "receptor_id,species_id,receptor_mutations,paralog,compound_branch,"
                + "central_productive_sector_pose_fraction,near_attack_pass_fraction,sam_clash_free_fraction,"
                + "directional_exit_sector_pose_fraction,max_direct_contact_jaccard_to_tsl_or_captopril,recurrent_family_count\n";
        Files.writeString(stageA.resolve("METTL7_V2_RECEPTOR_SPECIES_MECHANISTIC_MATRIX.csv"), matrixHeader
                + "A0,LIG,,METTL7A,BRANCH,1,0,1,0,1,1\n"
                + "B0,LIG,,METTL7B,BRANCH,1,0,1,0,1,1\n"
                + "A1,LIG,F10L,METTL7A,BRANCH,1,0,1,0,1,1\n"
                + "B1,LIG,L10F,METTL7B,BRANCH,1,0,1,0,1,1\n");
        Files.writeString(stageA.resolve("METTL7_V2_STATE_FAMILY_MECHANISTIC_EVIDENCE.csv"), "unused\n");

        Mettl7StageBInterpreter.build(stageA, stageB);

        String finalMatrix = Files.readString(stageB.resolve("METTL7_V2_FINAL_MECHANISTIC_MATRIX.csv"));
        String mutation = Files.readString(stageB.resolve("METTL7_V2_MUTATION_TRANSFER_MATRIX.csv"));
        assertTrue(finalMatrix.contains("A_ONLY"));
        assertTrue(mutation.contains("PARTIALLY_TRANSFERRED"));
        assertTrue(mutation.contains("\"true\""));
    }
}
