package totah.lab.report.render;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import totah.lab.report.model.CompletePocketReport;

import java.util.Objects;

public final class PocketJsonReportRenderer
        implements PocketReportRenderer<String> {

    private final ObjectMapper objectMapper;

    public PocketJsonReportRenderer(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper"
        );
    }

    @Override
    public String render(CompletePocketReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Cannot render pocket report as JSON",
                    exception
            );
        }
    }
}
