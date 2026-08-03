package totah.lab.web.persistence;

import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

/**
 * Spring Boot's default naming strategy plus one extension: the schema of
 * entities mapped to "public" (targets, pipeline_runs) can be remapped via
 * the {@value #PUBLIC_SCHEMA_PROPERTY} system property. Integration tests
 * point it at a throwaway schema cloned from the live DDL so they never
 * touch the real public tables; production leaves the property unset.
 */
public class SchemaRemappingPhysicalNamingStrategy
        extends CamelCaseToUnderscoresNamingStrategy {

    public static final String PUBLIC_SCHEMA_PROPERTY =
            "totah.hibernate.public-schema";

    private static final String PUBLIC_SCHEMA = "public";

    @Override
    public Identifier toPhysicalSchemaName(
            Identifier logicalName,
            JdbcEnvironment context
    ) {
        Identifier physical = super.toPhysicalSchemaName(logicalName, context);

        if (physical == null
                || !PUBLIC_SCHEMA.equals(physical.getText())) {
            return physical;
        }

        String override = System.getProperty(PUBLIC_SCHEMA_PROPERTY);
        if (override == null || override.isBlank()) {
            return physical;
        }

        return Identifier.toIdentifier(override.trim());
    }
}
