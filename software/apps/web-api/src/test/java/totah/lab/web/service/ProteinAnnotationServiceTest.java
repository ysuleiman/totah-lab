package totah.lab.web.service;

import org.junit.jupiter.api.Test;
import totah.lab.hermes.uniprot.UniProtAnnotation;
import totah.lab.hermes.uniprot.UniProtClient;
import totah.lab.hermes.uniprot.UniProtCrossReference;
import totah.lab.hermes.uniprot.UniProtEntry;
import totah.lab.hermes.uniprot.UniProtException;
import totah.lab.web.persistence.ReceptorRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProteinAnnotationServiceTest {

    private final ReceptorRepository receptorRepository =
            mock(ReceptorRepository.class);

    private final FakeUniProtClient uniProtClient =
            new FakeUniProtClient();

    private final ProteinAnnotationService service =
            new ProteinAnnotationService(
                    receptorRepository,
                    uniProtClient
            );

    @Test
    void annotatesHitsAndMarksMissingAccessions() throws Exception {
        when(receptorRepository.findDistinctUniProtIds())
                .thenReturn(List.of("B1", "B2", "B3", "B4"));

        AnnotationReport report = service.annotateTopHits(
                List.of("P11111", "p22222", "P99999")
        );

        assertEquals(3, report.requested());
        assertEquals(2, report.found());
        assertEquals(3, report.hits().size());
        assertFalse(report.hits().get(2).found());
        assertEquals("P99999", report.hits().get(2).accession());

        AnnotatedProtein first = report.hits().get(0);
        assertEquals("P11111", first.accession());
        assertEquals("Protein-lysine methyltransferase",
                first.proteinName());
        assertEquals("METTL7A", first.geneName());
        assertEquals("Homo sapiens", first.organism());
        assertTrue(first.reviewed());
        assertEquals(List.of("2.1.1.43"), first.ecNumbers());
        assertEquals(List.of("PF08241 Methyltransf_12"), first.pfam());
        assertEquals(List.of("8ABC"), first.pdbIds());

        FlagTally hits = report.hitTally();
        assertEquals(3, hits.total());
        assertEquals(1, hits.enzymes());
        assertEquals(1, hits.transferases());
        assertEquals(1, hits.methyltransferases());
        assertEquals(1, hits.membraneProteins());
        assertEquals(1, hits.ligandBindingProteins());
        assertEquals(1, hits.catalyticResidues());
        assertEquals(1, hits.experimentalStructures());
        assertEquals(1, hits.rossmannLikeFolds());
        assertEquals(1, hits.samBinders());
    }

    @Test
    void computesEnrichmentAgainstDatabaseBackground() throws Exception {
        when(receptorRepository.findDistinctUniProtIds())
                .thenReturn(List.of("B1", "B2", "B3", "B4"));

        AnnotationReport report = service.annotateTopHits(
                List.of("P11111", "P22222", "P99999")
        );

        FlagTally background = report.backgroundTally();
        // B4 has no UniProt entry but still counts toward the total.
        assertEquals(4, background.total());
        assertEquals(1, background.enzymes());
        assertEquals(1, background.membraneProteins());
        assertEquals(1, background.samBinders());

        AnnotationEnrichment enzymes = report.enrichment().stream()
                .filter(row -> row.category().equals("Enzymes"))
                .findFirst()
                .orElseThrow();

        assertEquals(1, enzymes.hitsFlagged());
        assertEquals(3, enzymes.hitsTotal());
        assertEquals(1, enzymes.backgroundFlagged());
        assertEquals(4, enzymes.backgroundTotal());
        assertEquals(
                (1.0 / 3.0) / (1.0 / 4.0),
                enzymes.foldEnrichment(),
                1e-9
        );
        assertEquals(0.75, enzymes.pValue(), 1e-9);
    }

    @Test
    void omitsRossmannEnrichmentWhenBackgroundCannotDetectIt()
            throws Exception {
        when(receptorRepository.findDistinctUniProtIds())
                .thenReturn(List.of("B1", "B2", "B3", "B4"));

        AnnotationReport report = service.annotateTopHits(
                List.of("P11111")
        );

        // The hit summary still reports Rossmann-like folds...
        assertEquals(1, report.hitTally().rossmannLikeFolds());

        // ...but no enrichment row is produced for the category,
        // because the compact background annotation lacks the
        // Pfam/InterPro family names needed to detect it.
        assertTrue(report.enrichment().stream()
                .noneMatch(row ->
                        row.category().equals("Rossmann-like folds")));
    }

    @Test
    void entryAndAnnotationRepresentationsProduceEquivalentFlags() {
        UniProtEntry entry = methyltransferaseEntry();

        UniProtAnnotation annotation = new UniProtAnnotation(
                entry.accession(),
                entry.reviewed(),
                entry.proteinName(),
                entry.ecNumbers(),
                entry.keywords(),
                entry.catalyticActivities().isEmpty()
                        ? null
                        : String.join("; ", entry.catalyticActivities()),
                entry.activeSites().isEmpty()
                        ? null
                        : String.join("; ", entry.activeSites()),
                entry.bindingLigands().isEmpty()
                        ? null
                        : String.join("; ", entry.bindingLigands()),
                entry.cofactors().isEmpty()
                        ? null
                        : String.join("; ", entry.cofactors()),
                entry.pfam().isEmpty()
                        ? null
                        : entry.pfam().get(0).id()
                                + " " + entry.pfam().get(0).name(),
                entry.interPro().isEmpty()
                        ? null
                        : entry.interPro().get(0).id()
                                + " " + entry.interPro().get(0).name(),
                entry.pdbIds().isEmpty()
                        ? null
                        : String.join(";", entry.pdbIds())
        );

        assertEquals(
                AnnotationFlags.derive(AnnotationFacts.from(entry)),
                AnnotationFlags.derive(AnnotationFacts.from(annotation))
        );
    }

    @Test
    void fetchesBackgroundOnlyOnce() throws Exception {
        when(receptorRepository.findDistinctUniProtIds())
                .thenReturn(List.of("B1", "B2", "B3", "B4"));

        service.annotateTopHits(List.of("P11111"));
        service.annotateTopHits(List.of("P22222"));

        verify(receptorRepository, times(1)).findDistinctUniProtIds();
        assertEquals(1, uniProtClient.backgroundFetches.get());
    }

    @Test
    void computesBackgroundOnceUnderConcurrentAccess() throws Exception {
        when(receptorRepository.findDistinctUniProtIds())
                .thenReturn(List.of("B1", "B2", "B3", "B4"));

        uniProtClient.backgroundGate = new CountDownLatch(1);

        Thread first = new Thread(() -> annotateQuietly("P11111"));
        Thread second = new Thread(() -> annotateQuietly("P22222"));

        first.start();
        // Let the first thread enter the background fetch, then start
        // a competing request and release the gate.
        Thread.sleep(100);
        second.start();
        uniProtClient.backgroundGate.countDown();

        first.join(TimeUnit.SECONDS.toMillis(10));
        second.join(TimeUnit.SECONDS.toMillis(10));

        assertFalse(first.isAlive());
        assertFalse(second.isAlive());
        verify(receptorRepository, times(1)).findDistinctUniProtIds();
        assertEquals(1, uniProtClient.backgroundFetches.get());
    }

    @Test
    void rejectsEmptyAccessionList() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.annotateTopHits(List.of(" ", ""))
        );
    }

    private void annotateQuietly(String accession) {
        try {
            service.annotateTopHits(List.of(accession));
        } catch (UniProtException | InterruptedException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static UniProtEntry methyltransferaseEntry() {
        return new UniProtEntry(
                "P11111",
                "METTL7A_HUMAN",
                "Protein-lysine methyltransferase",
                "METTL7A",
                "Homo sapiens",
                9606,
                "MABC",
                4,
                "Catalyzes methylation.",
                List.of("Methyltransferase", "Transferase"),
                List.of("8ABC"),
                List.of("AF-P11111-F1"),
                List.of("GO:0008168"),
                true,
                List.of("2.1.1.43"),
                List.of("methyltransferase activity"),
                List.of("S-adenosyl-L-methionine + L-lysine = products"),
                List.of("S-adenosyl-L-methionine"),
                List.of("Proton acceptor"),
                List.of(),
                List.of(new UniProtCrossReference(
                        "PF08241",
                        "Methyltransf_12"
                )),
                List.of(new UniProtCrossReference(
                        "IPR029063",
                        "Rossmann-like fold"
                ))
        );
    }

    private static UniProtEntry membraneEntry() {
        return new UniProtEntry(
                "P22222",
                "TMEM_HUMAN",
                "Transmembrane protein",
                "TMEM1",
                "Homo sapiens",
                9606,
                "MABC",
                4,
                null,
                List.of("Membrane"),
                List.of(),
                List.of("AF-P22222-F1"),
                List.of(),
                true,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static final class FakeUniProtClient
            implements UniProtClient {

        private final AtomicInteger backgroundFetches =
                new AtomicInteger();
        private volatile CountDownLatch backgroundGate;

        @Override
        public Optional<UniProtEntry> fetch(String accession) {
            return Optional.ofNullable(switch (accession) {
                case "P11111" -> methyltransferaseEntry();
                case "P22222" -> membraneEntry();
                default -> null;
            });
        }

        @Override
        public List<UniProtAnnotation> fetchAnnotations(
                Collection<String> accessions
        ) throws UniProtException, InterruptedException {
            backgroundFetches.incrementAndGet();

            CountDownLatch gate = backgroundGate;
            if (gate != null) {
                gate.await();
            }

            return List.of(
                    new UniProtAnnotation(
                            "B1", true, "Kinase",
                            List.of("2.7.11.1"), List.of(),
                            null, null, null, null, null, null, null
                    ),
                    new UniProtAnnotation(
                            "B2", true, "Channel",
                            List.of(), List.of("Membrane"),
                            null, null, null, null, null, null, null
                    ),
                    new UniProtAnnotation(
                            "B3", true, "Methylase",
                            List.of(), List.of(),
                            null, null,
                            "BINDING 1; /ligand=\"SAM\"",
                            null, null, null, null
                    )
            );
        }

        @Override
        public List<UniProtAnnotation> search(String query) {
            return List.of();
        }
    }
}
