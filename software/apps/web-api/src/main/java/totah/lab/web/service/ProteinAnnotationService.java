package totah.lab.web.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import totah.lab.hermes.uniprot.RestUniProtClient;
import totah.lab.hermes.uniprot.UniProtAnnotation;
import totah.lab.hermes.uniprot.UniProtClient;
import totah.lab.hermes.uniprot.UniProtEntry;
import totah.lab.hermes.uniprot.UniProtException;
import totah.lab.web.persistence.ReceptorRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ProteinAnnotationService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ProteinAnnotationService.class);

    private final ReceptorRepository receptorRepository;
    private final UniProtClient uniProtClient;

    // Process-local background cache. The background tally only
    // changes when new receptors are imported; computing it means
    // thousands of UniProt cells, so it is fetched lazily once and
    // reused for the application's lifetime. It is stale after
    // receptor imports until the process restarts.
    private final AtomicReference<FlagTally> backgroundCache =
            new AtomicReference<>();

    @Autowired
    public ProteinAnnotationService(
            ReceptorRepository receptorRepository
    ) {
        this(
                receptorRepository,
                new RestUniProtClient()
        );
    }

    ProteinAnnotationService(
            ReceptorRepository receptorRepository,
            UniProtClient uniProtClient
    ) {
        this.receptorRepository =
                Objects.requireNonNull(receptorRepository);
        this.uniProtClient = Objects.requireNonNull(uniProtClient);
    }

    @Transactional(readOnly = true)
    public AnnotationReport annotateTopHits(List<String> accessions)
            throws UniProtException, InterruptedException {

        List<String> normalized = normalize(accessions);

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one UniProt accession is required"
            );
        }

        List<AnnotatedProtein> hits = new ArrayList<>();

        for (String accession : normalized) {
            hits.add(annotate(accession));
        }

        long found = hits.stream().filter(AnnotatedProtein::found).count();

        FlagTally hitTally = FlagTally.of(
                hits.stream().map(AnnotatedProtein::flags).toList()
        );

        FlagTally backgroundTally = backgroundTally();

        return new AnnotationReport(
                List.copyOf(hits),
                normalized.size(),
                (int) found,
                hitTally,
                backgroundTally,
                enrichment(hitTally, backgroundTally)
        );
    }

    private AnnotatedProtein annotate(String accession)
            throws UniProtException, InterruptedException {

        Optional<UniProtEntry> entry = uniProtClient.fetch(accession);

        if (entry.isEmpty()) {
            LOGGER.warn(
                    "No UniProt entry found for accession {}",
                    accession
            );
            return AnnotatedProtein.notFound(accession);
        }

        UniProtEntry protein = entry.get();

        return new AnnotatedProtein(
                protein.accession(),
                true,
                protein.proteinName(),
                protein.geneName(),
                protein.organism(),
                protein.reviewed(),
                protein.ecNumbers(),
                protein.goMolecularFunctions(),
                protein.catalyticActivities(),
                protein.bindingLigands(),
                protein.cofactors(),
                protein.pfam().stream()
                        .map(ProteinAnnotationService::formatReference)
                        .toList(),
                protein.interPro().stream()
                        .map(ProteinAnnotationService::formatReference)
                        .toList(),
                protein.pdbIds(),
                protein.alphaFoldIds(),
                AnnotationFlags.derive(AnnotationFacts.from(protein))
        );
    }

    private FlagTally backgroundTally()
            throws UniProtException, InterruptedException {

        FlagTally cached = backgroundCache.get();

        if (cached != null) {
            return cached;
        }

        synchronized (backgroundCache) {
            cached = backgroundCache.get();

            if (cached != null) {
                return cached;
            }

            long startNanos = System.nanoTime();

            List<String> accessions =
                    receptorRepository.findDistinctUniProtIds();

            LOGGER.info(
                    "Annotating {} database receptors"
                            + " for enrichment background",
                    accessions.size()
            );

            List<UniProtAnnotation> annotations =
                    uniProtClient.fetchAnnotations(accessions);

            FlagTally tally = FlagTally.of(
                    annotations.stream()
                            .map(annotation -> AnnotationFlags.derive(
                                    AnnotationFacts.from(annotation)
                            ))
                            .toList()
            );

            // Accessions without a UniProt entry still count toward
            // the background total: they are receptors in the
            // database with no retrievable annotation.
            tally = new FlagTally(
                    accessions.size(),
                    tally.enzymes(),
                    tally.transferases(),
                    tally.methyltransferases(),
                    tally.membraneProteins(),
                    tally.ligandBindingProteins(),
                    tally.catalyticResidues(),
                    tally.experimentalStructures(),
                    tally.rossmannLikeFolds(),
                    tally.samBinders()
            );

            backgroundCache.set(tally);

            LOGGER.info(
                    "Enrichment background ready:"
                            + " {} receptors annotated in {} ms",
                    annotations.size(),
                    (System.nanoTime() - startNanos) / 1_000_000L
            );

            return tally;
        }
    }

    private static List<AnnotationEnrichment> enrichment(
            FlagTally hits,
            FlagTally background
    ) {
        List<AnnotationEnrichment> rows = new ArrayList<>();

        addEnrichment(rows, "Enzymes",
                hits.enzymes(), hits.total(),
                background.enzymes(), background.total());
        addEnrichment(rows, "Transferases",
                hits.transferases(), hits.total(),
                background.transferases(), background.total());
        addEnrichment(rows, "Methyltransferases",
                hits.methyltransferases(), hits.total(),
                background.methyltransferases(), background.total());
        // Rossmann-like folds are deliberately omitted: the compact
        // background annotation has no Pfam/InterPro family names, so
        // a background count would always be zero and the enrichment
        // meaningless. The hit-list summary still reports the count.
        addEnrichment(rows, "Ligand-binding proteins",
                hits.ligandBindingProteins(), hits.total(),
                background.ligandBindingProteins(), background.total());
        addEnrichment(rows, "Proteins binding SAM",
                hits.samBinders(), hits.total(),
                background.samBinders(), background.total());
        addEnrichment(rows, "Membrane proteins",
                hits.membraneProteins(), hits.total(),
                background.membraneProteins(), background.total());
        addEnrichment(rows, "Catalytic residues annotated",
                hits.catalyticResidues(), hits.total(),
                background.catalyticResidues(), background.total());
        addEnrichment(rows, "Experimental structures",
                hits.experimentalStructures(), hits.total(),
                background.experimentalStructures(), background.total());

        return List.copyOf(rows);
    }

    private static void addEnrichment(
            List<AnnotationEnrichment> rows,
            String category,
            int hitsFlagged,
            int hitsTotal,
            int backgroundFlagged,
            int backgroundTotal
    ) {
        Double fold = null;

        if (backgroundFlagged > 0 && hitsTotal > 0) {
            fold = ((double) hitsFlagged / hitsTotal)
                    / ((double) backgroundFlagged / backgroundTotal);
        }

        rows.add(new AnnotationEnrichment(
                category,
                hitsFlagged,
                hitsTotal,
                backgroundFlagged,
                backgroundTotal,
                fold,
                FisherExactTest.enrichmentPValue(
                        hitsFlagged,
                        hitsTotal,
                        backgroundFlagged,
                        backgroundTotal
                )
        ));
    }

    private static String formatReference(
            totah.lab.hermes.uniprot.UniProtCrossReference reference
    ) {
        return reference.name() == null
                ? reference.id()
                : reference.id() + " " + reference.name();
    }

    private static List<String> normalize(List<String> accessions) {
        if (accessions == null) {
            return List.of();
        }

        return accessions.stream()
                .filter(Objects::nonNull)
                .map(accession ->
                        accession.trim().toUpperCase(Locale.ROOT))
                .filter(accession -> !accession.isEmpty())
                .distinct()
                .toList();
    }
}
