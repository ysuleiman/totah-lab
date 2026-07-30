package totah.lab.pocket;


import org.junit.jupiter.api.Test;
import totah.lab.io.PocketIO;
import totah.lab.pocket.Pocket;
import totah.lab.pocket.analysis.PosePocketContactAnalyzer;

import java.nio.file.Path;
import java.util.List;

public class AnalyzerTest {

    @Test
    public void test() throws Exception {

        Path pocketsPath = Path.of(getClass().getResource("/Q6UX53").toURI());

        List<Pocket> pockets = PocketIO.load(pocketsPath);
        //PocketGeometryUtil.calculateCenter();
    }

}
