package totah.lab.pocket.fpocket;

import org.junit.jupiter.api.Test;
import totah.lab.pocket.Pocket;
import totah.lab.pocket.PocketGeometryUtil;
import totah.lab.fpocket.FPocketParser;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PocketGeometryCalculatorTest {

    @Test
    public void calculateCenter() throws Exception{
        Path folder = Paths.get(
                Objects.requireNonNull(
                                getClass().getResource("/AF-Q6UX53-F1-model_v6_out"))
                        .toURI());
        List<Pocket> pockets = FPocketParser.parse(folder);
        assertEquals(15, pockets.size());
        for(Pocket pocket : pockets){
            double[] center = PocketGeometryUtil.calculateCenter(pocket.getAlphaSpheres());
            System.out.println("Volume: "+pocket.getVolume()+", Center: "+Arrays.toString(center));
        }
    }
}
