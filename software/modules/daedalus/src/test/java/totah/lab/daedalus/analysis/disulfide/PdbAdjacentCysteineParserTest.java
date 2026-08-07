package totah.lab.daedalus.analysis.disulfide;

import org.junit.jupiter.api.Test;
import totah.lab.daedalus.analysis.disulfide.PdbAdjacentCysteineParser.AdjacentCysteinePair;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdbAdjacentCysteineParserTest {

    @Test
    void retainsSequenceEnvironmentAndGeometry() throws Exception {
        List<AdjacentCysteinePair> pairs = new PdbAdjacentCysteineParser()
                .parse(resource("/disulfide/adjacent-cysteines.pdb"), 8);

        assertThat(pairs).hasSize(1);
        AdjacentCysteinePair pair = pairs.getFirst();
        assertThat(pair.chain()).isEqualTo("A");
        assertThat(pair.firstResidue()).isEqualTo(2);
        assertThat(pair.secondResidue()).isEqualTo(3);
        assertThat(pair.sequenceContext()).isEqualTo("ACCG");
        assertThat(pair.motifOffset()).isEqualTo(1);
        assertThat(pair.sgDistanceAngstrom()).hasValue(2.0);
        assertThat(pair.chi3Degrees()).isPresent();
        assertThat(pair.meanPlddt()).isEqualTo(92.0);
    }

    @Test
    void leavesChi3EmptyWhenBothSgAtomsShareCoordinates() throws Exception {
        List<AdjacentCysteinePair> pairs = new PdbAdjacentCysteineParser()
                .parse(resource("/disulfide/degenerate-cysteines.pdb"), 8);

        assertThat(pairs).hasSize(1);
        AdjacentCysteinePair pair = pairs.getFirst();
        assertThat(pair.sgDistanceAngstrom()).hasValue(0.0);
        assertThat(pair.chi3Degrees()).isEmpty();
    }

    private static Path resource(String name) throws URISyntaxException {
        return Path.of(PdbAdjacentCysteineParserTest.class.getResource(name).toURI());
    }
}
