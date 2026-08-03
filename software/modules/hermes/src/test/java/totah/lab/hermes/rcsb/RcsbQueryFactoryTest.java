package totah.lab.hermes.rcsb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.hermes.file.pocket.FPocketParser;
import totah.lab.hermes.file.reader.AutoDetectingPocketReader;
import totah.lab.hermes.http.DefaultStructureResolutionClient;
import totah.lab.hermes.structure.ResolvedProteinStructure;
import totah.lab.hermes.structure.StructureResolutionClient;
import totah.lab.hermes.structure.StructureSource;
import totah.lab.hermes.uniprot.RestUniProtClient;
import totah.lab.hermes.uniprot.UniProtClient;
import totah.lab.hermes.uniprot.UniProtEntry;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class RcsbQueryFactoryTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @Test
    @Disabled("Live-network exploration test: queries UniProt/RCSB and depends on upstream data; run manually")
    void play() throws Exception {

        StructureResolutionClient structureResolver =
                new DefaultStructureResolutionClient(
                        new RestUniProtClient()
                );

        ResolvedProteinStructure reference = structureResolver
                .resolve("Q6UX53")
                .orElseThrow(() -> new IllegalStateException(
                        "No structure is available for Q6UX53"
                ));

        System.out.println("UniProt: " + reference.uniProtAccession());
        System.out.println("Source: " + reference.source());
        System.out.println("Kind: " + reference.kind());
        System.out.println("Identifier: " + reference.identifier());
        System.out.println("Coordinates: " + reference.coordinateUri());

        List<Pocket> pockets = FPocketParser.parse(
                resource("AF-Q6UX53-F1-model_v6_out")
        );


        Pocket pocket = pockets.get(1);

        List<RcsbResidue> residues = new ArrayList<>();

        for (ResidueId id : pocket.residues()) {
            if ("A".equals(id.chainId())) {
                residues.add(
                        new RcsbResidue(
                                id.chainId(),
                                id.residueNumber()
                        )
                );
            }
        }

        if (reference.source() != StructureSource.RCSB_PDB) {
            System.out.println("Skipping motif search: " + reference.identifier()
                    + " is not a PDB entry");
            return;
        }

        RcsbStructureMotifSearch search =
                new RcsbStructureMotifSearch(
                        reference.identifier(),
                        residues,
                        2.0
                );

        RcsbClient rcsb = new RestRcsbClient();

        List<RcsbSearchHit> hits = rcsb.search(search);

        for (RcsbSearchHit hit : hits) {
            System.out.println(hit);
        }
    }

    @Test
    @Disabled("Live-network test: asserts METTL7B resolves to an RCSB PDB entry, but upstream UniProt currently returns an AlphaFold model; run manually")
    void resolveMettl7bStructure() throws Exception {
        StructureResolutionClient resolver =
                new DefaultStructureResolutionClient(
                        new RestUniProtClient()
                );

        ResolvedProteinStructure reference = resolver.resolve("Q6UX53")
                .orElseThrow();

        System.out.println(reference);

        if (reference.source() != StructureSource.RCSB_PDB) {
            throw new IllegalStateException(
                    "METTL7B resolved to " + reference.source()
                            + " structure " + reference.identifier()
                            + ". RCSB entry-anchored motif search cannot use "
                            + "an AlphaFold model."
            );
        }
    }

    private Path resource(String name) throws URISyntaxException {
        return Path.of(getClass().getClassLoader()
                .getResource(name).toURI());
    }
}
