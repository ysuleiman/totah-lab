package totah.lab.report.narrative;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import totah.lab.report.model.PocketReport;
import totah.lab.report.validation.NarrativeEvidenceValidator;

import java.util.Objects;

public final class SpringAiPocketNarrativeGenerator
        implements PocketNarrativeGenerator {

    private static final String SYSTEM_INSTRUCTIONS = """
            You write computational structural-biology reports.
            Use only the supplied evidence records.
            Every substantive finding must cite one or more supplied evidence IDs.
            Never invent or recalculate measurements.
            Distinguish observations, interpretations, limitations, and recommendations.
            Do not infer affinity, inhibition, selectivity, experimental validation, or
            covalent compatibility from docking scores or proximity alone.
            Return a PocketNarrative matching the requested structured output.
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final NarrativeEvidenceValidator validator;

    public SpringAiPocketNarrativeGenerator(
            ChatClient chatClient,
            ObjectMapper objectMapper,
            NarrativeEvidenceValidator validator
    ) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient");
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper"
        );
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    @Override
    public PocketNarrative generate(PocketReport report) {
        Objects.requireNonNull(report, "report");
        String evidenceJson = writeEvidence(report);
        PocketNarrative narrative = chatClient.prompt()
                .system(SYSTEM_INSTRUCTIONS)
                .user("""
                        Write an evidence-linked pocket narrative from this JSON.
                        Preserve the supplied evidence identifiers exactly.

                        %s
                        """.formatted(evidenceJson))
                .call()
                .entity(PocketNarrative.class);
        if (narrative == null) {
            throw new IllegalStateException(
                    "Spring AI returned no pocket narrative");
        }
        validator.validate(report, narrative);
        return narrative;
    }

    private String writeEvidence(PocketReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Cannot serialize pocket report evidence",
                    exception
            );
        }
    }
}
