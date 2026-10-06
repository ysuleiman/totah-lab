package totah.lab.athena.system.rules.research;
import java.util.*;
import java.time.Instant;
import totah.lab.mnemosyne.ScientificReference;
import totah.lab.athena.system.rules.RuleManifest.Source;
record ImplementationAccess(Object document,boolean v2,String ruleKey,String definitionSha256,String domainSha256,
    String manifestSha256,List<Source> implementationPins,List<Source> fixturePins,
    List<RuleImplementationQualification.Check> checkResults,ScientificReference reviewer,Instant completedAt) {
    static ImplementationAccess of(RuleImplementationQualification p){return new ImplementationAccess(p,false,p.ruleKey(),p.definitionSha256(),p.domainSha256(),"",p.implementationPins(),p.fixturePins(),p.checkResults(),p.reviewer(),p.completedAt());}
    static ImplementationAccess of(RuleImplementationQualificationV2 p){return new ImplementationAccess(p,true,p.ruleKey(),p.definitionSha256(),p.domainSha256(),p.manifestSha256(),p.implementationPins(),p.fixturePins(),p.checkResults(),p.reviewer(),p.completedAt());}
}
