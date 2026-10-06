package totah.lab.athena.system.rules.research;

import org.junit.jupiter.api.Test;
import totah.lab.athena.system.rules.*;
import totah.lab.mnemosyne.EvidenceExchange;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class FirstRealRulePackageTest {
    @Test void stagedProjectionMatchesQualifiedCodecWithoutFabricatingReview() throws Exception {
        Path p=Path.of(System.getProperty("athena.reviewPackage"));
        var root=(com.fasterxml.jackson.databind.node.ObjectNode)ResearchCodec.JSON.readTree(Files.readAllBytes(p.resolve("MANIFEST_UNBOUND.json")));
        assertThrows(java.io.IOException.class,()->ResearchDocuments.decode(Files.readAllBytes(p.resolve("DOSSIER_DRAFT.json")),RuleResearchDossier.class));
        var domain=pin(p.resolve("DOMAIN.json"));var policy=pin(p.resolve("POLICY_DRAFT.json"));
        var digest=EvidenceExchange.sha256(Files.readAllBytes(p.resolve("SCIENTIFIC_PROJECTION.json")));
        // Deliberately pin incomplete review material: not a valid dossier or usable registry activation.
        root.set("research",ResearchCodec.JSON.valueToTree(new ResearchBinding("athena-rule-research-binding/2",pin(p.resolve("DOSSIER_REVIEW_MATERIAL.json")),policy,domain,digest,"athena-rule-definition-projection/2")));
        var manifest=ResearchCodec.JSON.treeToValue(root,RuleManifest.class);
        assertEquals(digest,ResearchCodec.definition(manifest));
        // Definition fields other than versioned governance schema remain exactly those of the accepted rule.
        var existing=ResearchCodec.JSON.readTree(getClass().getClassLoader().getResourceAsStream("totah/lab/athena/system/rules/ss-connectivity-v1/ATHENA.SULF.SS_CONNECTIVITY.rule.json"));
        var projection=ResearchCodec.JSON.readTree(Files.readAllBytes(p.resolve("SCIENTIFIC_PROJECTION.json")));
        projection.fieldNames().forEachRemaining(key->{if(!key.equals("schema")&&!key.equals("domainSha256"))assertEquals(existing.get(key),projection.get(key),key);});
    }
    private static RuleManifest.Source pin(Path p)throws Exception{return new RuleManifest.Source(p.toString(),EvidenceExchange.sha256(Files.readAllBytes(p)),"inactive review package");}
}
