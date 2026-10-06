package totah.lab.athena.system.rules.research;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import totah.lab.athena.system.rules.RuleRequest;
import totah.lab.gaia.structure.ResidueId;
import java.io.IOException;
import java.util.Set;
import java.util.HashSet;

/** RuleRequest is source-state transport, not null-free research metadata. */
final class QualificationRequestCodec {
    private QualificationRequestCodec() { }
    private static final ObjectMapper JSON = ResearchCodec.JSON.copy().registerModule(
            new SimpleModule().addDeserializer(ResidueId.class, new JsonDeserializer<ResidueId>() {
                @Override public ResidueId deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                    JsonNode n = context.readTree(parser);
                    var names = new HashSet<String>(); n.fieldNames().forEachRemaining(names::add);
                    if (!n.isObject() || !names.equals(Set.of("chainId", "residueNumber", "insertionCode"))
                            || !n.get("chainId").isTextual() || !n.get("residueNumber").isIntegralNumber()
                            || !n.get("residueNumber").canConvertToInt()
                            || !(n.get("insertionCode").isNull() || n.get("insertionCode").isTextual()
                                 && n.get("insertionCode").textValue().length() == 1))
                        return context.reportInputMismatch(ResidueId.class, "exact source residue identity required");
                    // Null means no insertion code in the existing authoritative Gaia contract.
                    return new ResidueId(n.get("chainId").textValue(), n.get("residueNumber").intValue(),
                            n.get("insertionCode").isNull() ? null : n.get("insertionCode").textValue().charAt(0));
                }
            }));
    static RuleRequest decode(byte[] bytes) throws IOException { return JSON.readValue(bytes, RuleRequest.class); }
}
