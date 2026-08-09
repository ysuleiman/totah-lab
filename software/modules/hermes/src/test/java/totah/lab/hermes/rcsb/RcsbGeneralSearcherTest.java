package totah.lab.hermes.rcsb;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import totah.lab.hermes.rcsb.internal.RcsbJsonParser;

import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RcsbGeneralSearcherTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final RcsbJsonParser parser = new RcsbJsonParser(mapper);
    private final RcsbGeneralSearcher searcher = new RcsbGeneralSearcher(mapper, parser);
    private final RcsbQueryFactory queryFactory = new RcsbQueryFactory(mapper);

    @Test
    @Tag("integration")
    void canExecuteWholeStructureSimilaritySearch() throws Exception {
        // Build a global 3D structural shape query for Hemoglobin (PDB ID: 4HHB)
        var wholeStructureQuery = queryFactory.createWholeStructureQuery("4HHB", "1");

        List<Object> results = searcher.searchAndParse(wholeStructureQuery);

        assertFalse(results.isEmpty(), "Should find globally similar fold architectures.");
    }

    @Test
    @Tag("integration")
    void canExecuteSequenceSimilaritySearch() throws Exception {
        // Short partial sequence snippet
        String humanInsulinSnippet = "GIVEQCCTSICSLYQLENYCN";
        var sequenceQuery = queryFactory.createSequenceQuery(humanInsulinSnippet, 0.70);

        List<Object> results = searcher.searchAndParse(sequenceQuery);

        assertFalse(results.isEmpty(), "Should find sequence-similar matching proteins.");
    }

    @Test
    void throwsCheckedExceptionOnNon200SearchResponse() {
        RcsbException exception = assertThrows(RcsbException.class,
                () -> searcher.parseSearchResultIds(429, "{}"));

        assertTrue(exception.getMessage().contains("429"));
    }

    @Test
    void skipsResultEntriesMissingIdentifier() throws Exception {
        List<String> ids = searcher.parseSearchResultIds(200,
                "{\"result_set\":[{\"identifier\":\"4HHB\"},{\"score\":0.91}]}");

        assertEquals(List.of("4HHB"), ids);
    }
}
