package totah.lab.mettl7.design;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import totah.lab.athena.design.grammar.ExecutableScaffoldGrammar;
import totah.lab.athena.design.grammar.ExecutableScaffoldGrammarValidator;
import totah.lab.athena.design.generation.GrammarDrivenCandidateGenerator;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class Mettl7bExecutableGrammarValidationTest {
 @Test void compiledFrozenParentIsValidAndRoundTrips()throws Exception{Path root=Path.of(System.getProperty("user.dir")).resolve("../../../").normalize();Path path=root.resolve("analysis/mettl7/mettl7b_design_campaign_v1/METTL7B_BRICS0040_EXECUTABLE_SCAFFOLD_GRAMMAR.json");var mapper=new ObjectMapper();var first=mapper.readValue(path.toFile(),ExecutableScaffoldGrammar.class);byte[]serialized=mapper.writeValueAsBytes(first);var second=mapper.readValue(serialized,ExecutableScaffoldGrammar.class);assertThat(second).isEqualTo(first);var validation=new ExecutableScaffoldGrammarValidator().validate(first);assertThat(validation.valid()).isTrue();assertThat(new GrammarDrivenCandidateGenerator().inspect(first).ready()).isTrue();assertThat(first.editableVectors()).hasSize(12).allMatch(v->!v.attachmentBonds().isEmpty()&&!v.protectedNeighborhood().isEmpty()&&!v.allowedTransformationClasses().isEmpty());assertThat(first.featureProtections()).allMatch(f->first.parentGraph().atoms().stream().anyMatch(a->a.id().equals(f.atomId())));}
}
