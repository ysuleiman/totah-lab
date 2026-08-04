package totah.lab.web.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import totah.lab.web.persistence.ReceptorEntity;
import totah.lab.web.persistence.ReceptorRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Backfills receptor identity fields ({@code protein_name},
 * {@code gene_name}) from a UniProt proteome TSV download. The bulk
 * importer only has the AlphaFold filename, so it cannot know these
 * fields; this service fills them in afterwards, matched on
 * {@code uniprot_id}. Only null fields are written — existing values
 * are never overwritten.
 *
 * Expected TSV: header line, then rows of
 * {@code accession <TAB> protein names <TAB> primary gene <TAB> organism}
 * as produced by the UniProt stream endpoint with
 * {@code fields=accession,protein_name,gene_primary,organism_name}.
 */
@Service
public class ReceptorUniProtBackfillService {

    private final ReceptorRepository receptorRepository;

    public ReceptorUniProtBackfillService(
            ReceptorRepository receptorRepository
    ) {
        this.receptorRepository = Objects.requireNonNull(receptorRepository);
    }

    @Transactional
    public BackfillResult backfill(Path uniprotTsv) throws IOException {
        Map<String, UniProtIdentity> identities = readTsv(uniprotTsv);

        int updated = 0;
        int alreadyComplete = 0;
        Map<String, ReceptorEntity> dirty = new HashMap<>();

        for (ReceptorEntity receptor : receptorRepository.findAll()) {
            UniProtIdentity identity =
                    identities.get(receptor.getUniProtId());
            if (identity == null) {
                continue;
            }

            boolean changed = false;
            if (receptor.getProteinName() == null
                    && identity.proteinName() != null) {
                receptor.setProteinName(identity.proteinName());
                changed = true;
            }
            if (receptor.getGeneName() == null
                    && identity.geneName() != null) {
                receptor.setGeneName(identity.geneName());
                changed = true;
            }

            if (changed) {
                dirty.put(receptor.getUniProtId(), receptor);
                updated++;
            } else {
                alreadyComplete++;
            }
        }

        receptorRepository.saveAll(dirty.values());

        return new BackfillResult(
                identities.size(),
                updated,
                alreadyComplete
        );
    }

    static Map<String, UniProtIdentity> readTsv(Path uniprotTsv)
            throws IOException {

        Map<String, UniProtIdentity> identities = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(uniprotTsv)) {
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split("\t", -1);
                if (columns.length < 4 || columns[0].isBlank()) {
                    continue;
                }
                identities.put(
                        columns[0],
                        new UniProtIdentity(
                                recommendedName(columns[1]),
                                primaryGene(columns[2])
                        )
                );
            }
        }
        return Map.copyOf(identities);
    }

    /*
     * The "Protein names" column appends synonyms in parentheses; the
     * recommended name is the part before the first parenthesized group.
     */
    private static String recommendedName(String proteinNames) {
        int parenthesis = proteinNames.indexOf(" (");
        String name = parenthesis < 0
                ? proteinNames
                : proteinNames.substring(0, parenthesis);
        return blankToNull(name);
    }

    /*
     * The primary-gene column can hold a whole semicolon-separated
     * family (e.g. "CT47A1; CT47A2; ..."). One receptor row represents
     * one protein, so keep the first symbol; gene_name is varchar(50).
     */
    private static String primaryGene(String geneNames) {
        String value = blankToNull(geneNames);
        if (value == null) {
            return null;
        }
        int semicolon = value.indexOf(';');
        String gene = (semicolon < 0
                ? value
                : value.substring(0, semicolon)).trim();
        return gene.length() <= 50 ? gene : gene.substring(0, 50);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    record UniProtIdentity(
            String proteinName,
            String geneName
    ) {
    }

    public record BackfillResult(
            int uniProtEntries,
            int updated,
            int alreadyComplete
    ) {
    }
}
