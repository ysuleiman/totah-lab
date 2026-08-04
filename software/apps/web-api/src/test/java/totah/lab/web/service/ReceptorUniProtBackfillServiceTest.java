package totah.lab.web.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.web.persistence.ReceptorEntity;
import totah.lab.web.persistence.ReceptorRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class ReceptorUniProtBackfillServiceTest {

    @TempDir
    Path tempDir;

    private Path writeTsv(String rows) throws Exception {
        Path tsv = tempDir.resolve("uniprot.tsv");
        Files.writeString(tsv,
                "Entry\tProtein names\tGene Names (primary)\tOrganism\n"
                        + rows);
        return tsv;
    }

    @Test
    void parsesTsvWithRecommendedNameAndNullableGene() throws Exception {
        Path tsv = writeTsv(
                "P51801\tRibosomal protein (Alternative name)\tRPS1\tHomo sapiens (Human)\n"
                        + "Q6UX53\tThiol S-methyltransferase TMT1B\tMETTL7B\tHomo sapiens (Human)\n"
                        + "A0A000\tSome orphan protein\t\tHomo sapiens (Human)\n"
                        + "A0A001\tFamily protein\tCT47A1; CT47A2; CT47A3\tHomo sapiens (Human)\n");

        Map<String, ReceptorUniProtBackfillService.UniProtIdentity>
                identities = ReceptorUniProtBackfillService.readTsv(tsv);

        assertEquals(4, identities.size());
        assertEquals("Ribosomal protein",
                identities.get("P51801").proteinName());
        assertEquals("RPS1", identities.get("P51801").geneName());
        assertEquals("Thiol S-methyltransferase TMT1B",
                identities.get("Q6UX53").proteinName());
        assertNull(identities.get("A0A000").geneName());
        assertEquals("CT47A1", identities.get("A0A001").geneName());
    }

    @Test
    void updatesOnlyNullFieldsAndSkipsUnknownAccessions() throws Exception {
        Path tsv = writeTsv(
                "P51801\tRibosomal protein\tRPS1\tHomo sapiens (Human)\n"
                        + "Q6UX53\tTMT1B renamed\tMETX\tHomo sapiens (Human)\n");

        ReceptorEntity empty = new ReceptorEntity();
        empty.setUniProtId("P51801");

        ReceptorEntity complete = new ReceptorEntity();
        complete.setUniProtId("Q6UX53");
        complete.setProteinName("Existing name");
        complete.setGeneName("METTL7B");

        ReceptorEntity unknown = new ReceptorEntity();
        unknown.setUniProtId("NOTINTSV");

        ReceptorRepository repository = mock(ReceptorRepository.class);
        when(repository.findAll())
                .thenReturn(List.of(empty, complete, unknown));

        ReceptorUniProtBackfillService.BackfillResult result =
                new ReceptorUniProtBackfillService(repository).backfill(tsv);

        assertEquals(1, result.updated());
        assertEquals(1, result.alreadyComplete());
        assertEquals("Ribosomal protein", empty.getProteinName());
        assertEquals("RPS1", empty.getGeneName());
        assertEquals("Existing name", complete.getProteinName());
        assertEquals("METTL7B", complete.getGeneName());
        assertNull(unknown.getProteinName());

        verify(repository).saveAll(argThat((Iterable<ReceptorEntity> it) -> {
            List<ReceptorEntity> saved = new java.util.ArrayList<>();
            it.forEach(saved::add);
            return saved.size() == 1 && saved.getFirst() == empty;
        }));
    }
}
