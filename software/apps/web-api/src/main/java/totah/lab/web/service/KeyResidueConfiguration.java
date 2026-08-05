package totah.lab.web.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Query-specific key residues, configured per UniProt accession
 * (property {@code totah.key-residues.<uniProtId>}). Used to
 * highlight residues in the comparison UI and to score key-residue
 * chemistry in Stage 3 similarity ranking; never affects alignment
 * or correspondence. Queries without configuration get an empty
 * list.
 */
@Component
@ConfigurationProperties(prefix = "totah")
public class KeyResidueConfiguration {

    private Map<String, List<String>> keyResidues =
            new LinkedHashMap<>();

    public Map<String, List<String>> getKeyResidues() {
        return keyResidues;
    }

    public void setKeyResidues(Map<String, List<String>> keyResidues) {
        this.keyResidues = keyResidues;
    }

    public List<String> forUniProtId(String uniProtId) {
        if (uniProtId == null) {
            return List.of();
        }

        return keyResidues.getOrDefault(uniProtId, List.of());
    }
}
