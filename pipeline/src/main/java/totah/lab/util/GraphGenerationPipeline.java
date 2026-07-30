package totah.lab.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import totah.lab.io.ProteinIO;
import totah.lab.protein.Protein;
import totah.lab.topology.AmberResidueTemplateLibrary;
import totah.lab.topology.ProteinTopologyBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class GraphGenerationPipeline {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ProteinTopologyBuilder topologyBuilder;

    public GraphGenerationPipeline() {
        // Use singleton library, loaded once
        this.topologyBuilder = new ProteinTopologyBuilder(
                AmberResidueTemplateLibrary.getInstance()
        );
    }

    public void processProteinDirectory(Path proteinFolder, Path outputDir) {
        try {
            // Load protein with topology builder
            Protein protein = ProteinIO.load(proteinFolder);

            // Extract pocket graphs
            String targetId = protein.getTargetId().uniProtId();
            List<Map<String, Object>> pocketGraphs = protein.getPockets().stream()
                    .map(PocketGraphExporter::exportToGraphJson)
                    .toList();

            // Serialize each graph
            for (Map<String, Object> graph : pocketGraphs) {
                Map<String, Object> metadata = (Map<String, Object>) graph.get("metadata");
                metadata.put("target_id", targetId);

                File outputFile = outputDir.resolve(targetId + "_graphs.json").toFile();
                mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, graph);
            }

            System.out.println("Successfully generated GNN data for target: " + targetId);

        } catch (IOException e) {
            System.err.println("Failed to process structural graph pipeline: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
