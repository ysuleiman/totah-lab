package totah.lab.athena.system.rules.research;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.athena.system.rules.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ResearchV2AcceptanceTest {
    static ResearchV2Fixtures.Fixture fixture(String variant)throws Exception{return ResearchV2Fixtures.upgrade(ResearchGateAcceptanceTest.fixture(variant),variant);}
    @ParameterizedTest @ValueSource(strings={"valid","long-validity"})
    void separateAuthoritiesAndPerRuleValidityPass(String variant)throws Exception {
        var f=fixture(variant);assertTrue(f.run().eligible(),f.run().reasons().toString());
        assertEquals(f.policy(),ResearchDocuments.decode(ResearchDocuments.encode(f.policy()),RuleReviewPolicyV2.class));
    }
    @ParameterizedTest @ValueSource(strings={"expired","swapped-science","swapped-context","unauthorized","wrong-definition","missing-system","missing-data","unresolved","invalidated","stale-context"})
    void incompleteOrUnauthorizedScienceFails(String variant)throws Exception { assertFalse(fixture(variant).run().eligible()); }
    @ParameterizedTest @ValueSource(strings={"tampered","missing-artifact"})
    void badProvenanceFailsChecked(String variant)throws Exception {assertThrows(java.io.IOException.class,()->fixture(variant).run());}
    @Test void implementationFieldsDoNotChangeScienceButScientificFieldsDo()throws Exception {
        var f=fixture("valid");var before=ResearchCodec.definition(f.manifest());
        for(String field:List.of("implementationId","implementationVersion")) {
            var tree=ResearchCodec.JSON.valueToTree(f.manifest());((com.fasterxml.jackson.databind.node.ObjectNode)tree).put(field,"changed");
            var revised=ResearchCodec.JSON.treeToValue(tree,RuleManifest.class);
            assertEquals(before,ResearchCodec.definition(revised));assertNotEquals(RuleRegistry.digest(f.manifest()),RuleRegistry.digest(revised));
            assertTrue(new ScientificRuleResearchGate().evaluate(revised,f.policy(),f.context(),s->f.bytes().get(s.sha256()),ResearchGateAcceptanceTest.AT,QualificationMode.CURRENT).eligible());
        }
        for(String field:List.of("version","profile")) {
            var tree=ResearchCodec.JSON.valueToTree(f.manifest());((com.fasterxml.jackson.databind.node.ObjectNode)tree).put(field,"changed");
            var revised=ResearchCodec.JSON.treeToValue(tree,RuleManifest.class);
            assertNotEquals(before,ResearchCodec.definition(revised));
            assertFalse(new ScientificRuleResearchGate().evaluate(revised,f.policy(),f.context(),s->f.bytes().get(s.sha256()),ResearchGateAcceptanceTest.AT,QualificationMode.CURRENT).eligible());
        }
    }
    @Test void mixedPolicyAndBindingFailClosed()throws Exception {
        var old=ResearchGateAcceptanceTest.fixture("valid");var modern=fixture("valid");
        assertFalse(new ScientificRuleResearchGate().evaluate(modern.manifest(),old.policy(),modern.context(),s->modern.bytes().get(s.sha256()),ResearchGateAcceptanceTest.AT,QualificationMode.CURRENT).eligible());
        assertFalse(new ScientificRuleResearchGate().evaluate(old.manifest(),modern.policy(),old.context(),s->old.bytes().get(s.sha256()),ResearchGateAcceptanceTest.AT,QualificationMode.CURRENT).eligible());
        assertThrows(IllegalArgumentException.class,()->new ResearchBinding("athena-rule-research-binding/1",old.manifest().research().dossier(),old.manifest().research().reviewPolicy(),old.manifest().research().domain(),old.manifest().research().definitionSha256(),"athena-rule-definition-projection/2"));
    }
    @ParameterizedTest @ValueSource(strings={"requiredChemistry","requiredGeometry","limitations","negativeCoverage","parameters","scientificSources"})
    void scientificContentCannotHideBehindImplementationRevision(String field)throws Exception {
        var f=fixture("valid");var tree=(com.fasterxml.jackson.databind.node.ObjectNode)ResearchCodec.JSON.valueToTree(f.manifest());
        if(Set.of("requiredChemistry","requiredGeometry","limitations").contains(field))((com.fasterxml.jackson.databind.node.ArrayNode)tree.get(field)).add("changed scientific domain");
        else if(field.equals("negativeCoverage"))((com.fasterxml.jackson.databind.node.ObjectNode)tree.get(field)).put("supportedDomain","changed domain");
        else if(field.equals("parameters"))((com.fasterxml.jackson.databind.node.ObjectNode)tree.get(field).get("definition")).put("value","changed scientific definition");
        else ((com.fasterxml.jackson.databind.node.ArrayNode)tree.get(field)).add(ResearchCodec.JSON.valueToTree(f.raw()));
        var revised=ResearchCodec.JSON.treeToValue(tree,RuleManifest.class);
        assertNotEquals(ResearchCodec.definition(f.manifest()),ResearchCodec.definition(revised));
        assertFalse(new ScientificRuleResearchGate().evaluate(revised,f.policy(),f.context(),pin->f.bytes().get(pin.sha256()),ResearchGateAcceptanceTest.AT,QualificationMode.CURRENT).eligible());
    }
    @Test void scientificAndExecutorRoleOverlapIsRejected()throws Exception {
        var f=fixture("valid");var tree=(com.fasterxml.jackson.databind.node.ObjectNode)ResearchCodec.JSON.valueToTree(f.policy());
        tree.set("authorizedImplementationReviewers",tree.get("authorizedScientificReviewers"));
        assertThrows(java.io.IOException.class,()->ResearchDocuments.decode(ResearchCodec.bytes(tree),RuleReviewPolicyV2.class));
    }

}
