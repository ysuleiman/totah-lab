package totah.lab.athena.system.rules.research;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.mnemosyne.ScientificReference;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QualificationRequestCodecTest {
    static RuleRequest request() {
        var ref=new ScientificReference(ScientificReference.Kind.CONTEXT,"fixture","source","1");
        var binding=new SystemStateView.Binding(ref,"0".repeat(64),"1".repeat(64),"2".repeat(64),"3".repeat(64));
        return new RuleRequest(binding,"fixture","4".repeat(64),List.of(),List.of(new ResidueId("A",1,null)),List.of(new ResidueId("B",2,'X')),4.5,0,100,100);
    }
    @Test void preservesBothAbsentAndExplicitInsertionCodes() throws Exception {
        var r=request();assertEquals(r,QualificationRequestCodec.decode(SystemStateView.bytes(r)));
    }
    @Test void strictFieldsRemainStrict() throws Exception {
        for(var variant:List.of("missing","extra","null-chain","numeric-chain","numeric-insertion","long-insertion","fraction","null-state","null-radius")) {
            ObjectNode n=(ObjectNode)ResearchCodec.JSON.readTree(SystemStateView.bytes(request()));
            var residue=(ObjectNode)n.get("first").get(0);
            switch(variant) {
                case "missing" -> residue.remove("insertionCode");
                case "extra" -> residue.put("unexpected",true);
                case "null-chain" -> residue.putNull("chainId");
                case "numeric-chain" -> residue.put("chainId",1);
                case "numeric-insertion" -> residue.put("insertionCode",1);
                case "long-insertion" -> residue.put("insertionCode","XX");
                case "fraction" -> residue.put("residueNumber",1.5);
                case "null-state" -> n.putNull("state");
                case "null-radius" -> n.putNull("radiusAngstrom");
            }
            assertThrows(Exception.class,()->QualificationRequestCodec.decode(SystemStateView.bytes(n)),variant);
        }
    }
}
