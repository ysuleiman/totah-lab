package totah.lab.pipeline;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "docking")
public record DockingProperties(
        Path vinaExecutable
) {
}
