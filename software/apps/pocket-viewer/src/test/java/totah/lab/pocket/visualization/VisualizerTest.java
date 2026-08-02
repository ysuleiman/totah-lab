package totah.lab.pocket.visualization;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class VisualizerTest {

    @Test
    void extractsArgbPixelsForImageExport() {
        BufferedImage image =
                new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, Color.RED.getRGB());
        image.setRGB(1, 0, Color.BLUE.getRGB());

        int[] pixels = Visualizer.argbPixels(image);

        assertThat(pixels)
                .containsExactly(Color.RED.getRGB(), Color.BLUE.getRGB());
    }

    @Test
    void relaunchesOnlyOnMacWhenFirstThreadFlagIsMissing() {
        assertThat(Visualizer.requiresMacFirstThreadRelaunch(
                "Mac OS X",
                false)).isTrue();
        assertThat(Visualizer.requiresMacFirstThreadRelaunch(
                "Mac OS X",
                true)).isFalse();
        assertThat(Visualizer.requiresMacFirstThreadRelaunch(
                "Linux",
                false)).isFalse();
    }
}
