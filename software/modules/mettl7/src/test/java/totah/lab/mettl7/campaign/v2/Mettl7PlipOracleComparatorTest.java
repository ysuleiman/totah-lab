package totah.lab.mettl7.campaign.v2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Mettl7PlipOracleComparatorTest {
    @TempDir Path temporary;
    @Test void labelsCountOnlyOracleAndRejectsUnknownPiVocabulary() throws Exception {
        Path csv=athenaCsv();Path root=temporary.resolve("plip");Files.createDirectories(root.resolve("run"));
        Files.writeString(root.resolve("run/run.xml"),xml("X"));
        assertThatThrownBy(()->Mettl7PlipOracleComparator.compare(csv,root,temporary.resolve("out.csv")))
                .hasMessageContaining("UNKNOWN_PLIP_PI_STACK_TYPE");
        Files.writeString(root.resolve("run/run.xml"),xml("P"));
        Path out=temporary.resolve("out.csv");Mettl7PlipOracleComparator.compare(csv,root,out);
        assertThat(Files.readString(out)).startsWith("oracle_semantics,")
                .contains("PLIP_COUNT_ORACLE_COMPARISON");
    }
    @Test void rejectsDoctype() throws Exception {
        Path csv=athenaCsv();Path root=temporary.resolve("xxe");Files.createDirectories(root.resolve("run"));
        Files.writeString(root.resolve("run/run.xml"),"<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><report><bindingsite><hetid>LIG</hetid></bindingsite></report>");
        assertThatThrownBy(()->Mettl7PlipOracleComparator.compare(csv,root,temporary.resolve("out2.csv"))).isInstanceOf(Exception.class);
    }
    private Path athenaCsv()throws Exception{Path p=temporary.resolve("athena.csv");Files.writeString(p,
            "pose_model,run_id,athena_hbond_count,athena_salt_bridge_count,athena_hydrophobic_refined_count,athena_pi_parallel_count,athena_pi_t_count,athena_pi_cation_count,athena_halogen_bond_count,athena_hydrophobic_raw_count\n1,run,0,0,0,1,0,0,0,0\n");return p;}
    private static String xml(String type){return "<report><bindingsite><hetid>LIG</hetid><pi_stack><type>"+type+"</type></pi_stack></bindingsite></report>";}
}
