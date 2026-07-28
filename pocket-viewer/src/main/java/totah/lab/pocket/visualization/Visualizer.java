package totah.lab.pocket.visualization;

import totah.lab.io.ProteinIO;
import totah.lab.protein.Protein;

import java.nio.file.Path;

public class Visualizer {
    public static void main(String[] args) throws Exception {
        Path path = Path.of(Visualizer.class.getResource("/Q6UX53/").toURI());

        Protein protein = ProteinIO.load(path);
        PocketImageExporter exporter =
                new PocketImageExporter();

        PocketImageExporter.ExportResult result =
                exporter.exportStandardViews(
                        pocket,
                        Path.of("/Users/yazan/artifacts/pocket-images"));

        System.out.println(result.topProjection());
        System.out.println(result.sideProjection());
        System.out.println(result.endProjection());
        System.out.println(result.crossSection());
    }
}
