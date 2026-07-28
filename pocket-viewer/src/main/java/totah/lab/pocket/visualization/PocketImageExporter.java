package totah.lab.pocket.visualization;

import totah.lab.pocket.Pocket;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class PocketImageExporter {

    private final PocketSliceRenderer renderer;

    public PocketImageExporter() {
        this.renderer = new PocketSliceRenderer();
    }

    public Path exportTopProjection(
            Pocket pocket,
            Path outputFile) throws IOException {

        Objects.requireNonNull(pocket, "pocket");
        Objects.requireNonNull(outputFile, "outputFile");

        PocketPca.Orientation orientation =
                PocketPca.calculate(pocket);

        BufferedImage image =
                renderer.renderProjection(
                        pocket,
                        orientation.topPlane(),
                        PocketSliceRenderer.RenderOptions
                                .defaults()
                                .withTitle("Pocket top projection"));

        writePng(image, outputFile);

        return outputFile;
    }

    public Path exportCrossSection(
            Pocket pocket,
            double slabThickness,
            Path outputFile) throws IOException {

        Objects.requireNonNull(pocket, "pocket");
        Objects.requireNonNull(outputFile, "outputFile");

        PocketPca.Orientation orientation =
                PocketPca.calculate(pocket);

        BufferedImage image =
                renderer.renderCrossSection(
                        pocket,
                        orientation.topPlane(),
                        PocketSliceRenderer.RenderOptions
                                .defaults()
                                .withSlabThickness(slabThickness)
                                .withTitle("Pocket cross-section"));

        writePng(image, outputFile);

        return outputFile;
    }

    private static void writePng(
            BufferedImage image,
            Path outputFile) throws IOException {

        Path absolutePath =
                outputFile.toAbsolutePath();

        Path parent = absolutePath.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        boolean written =
                ImageIO.write(
                        image,
                        "png",
                        absolutePath.toFile());

        if (!written) {
            throw new IOException(
                    "No PNG ImageIO writer is available");
        }
    }
}