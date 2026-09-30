package totah.lab.mettl7.design;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class Mettl7bGrammarConfigurationLoaderTest {
 @Test void loadsFrozenHierarchicalGrammarWithoutEmbeddedScientificIdentifiers() throws Exception {
  Path root=Path.of(System.getProperty("user.dir")).resolve("../../../").normalize();
  Path d=root.resolve("analysis/mettl7/mettl7b_design_campaign_v1");
  var grammar=new Mettl7bGrammarConfigurationLoader().load(d.resolve("METTL7B_TARGET_LEVEL_B_RECOGNITION_GRAMMAR.json"),d.resolve("METTL7B_BRICS0040_SCAFFOLD_GRAMMAR.json"),d.resolve("METTL7B_A_COUNTER_RECOGNITION_GRAMMAR.json"),d.resolve("METTL7B_GRAMMAR_HARD_SOFT_RULES.csv"));
  assertThat(grammar.positiveFeatureGroups()).isNotEmpty();assertThat(grammar.counterRecognitionGroups()).isNotEmpty();assertThat(grammar.distanceEnvelopes()).isNotEmpty();assertThat(grammar.editableVectors()).isNotEmpty();
  String source=java.nio.file.Files.readString(root.resolve("software/modules/athena/src/main/java/totah/lab/athena/design/grammar/PredockGrammarEngine.java"));
  assertThat(source).doesNotContain("METTL7","BRICS","netarsudil","196","207","O7");
 }
}
