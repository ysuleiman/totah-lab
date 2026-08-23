package totah.lab.prometheus.neural.ferminet.force;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetDerivativeConfiguration;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1Configuration;

/** Complete numerical identity for a FermiNet nuclear-force execution. */
public final class FermiNetForceScientificIdentity {
    private FermiNetForceScientificIdentity() {}

    public static String create(FermiNetForceEvaluationContext context,
            NuclearForceConfiguration force,
            FermiNetDerivativeConfiguration derivative) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(force, "force");
        Objects.requireNonNull(derivative, "derivative");
        return sha256(String.join("\n",
                "prometheus-ferminet-force-execution-v1",
                "estimator=" + force.estimatorType().name(),
                "estimator_configuration=" + force.identity(),
                "derivative_configuration=" + derivative.scientificIdentity(),
                "parameters=" + context.parameterChecksum(),
                "geometry=" + context.geometryIdentity(),
                "dataset=" + context.dataset().sha256(),
                "checkpoint=" + context.checkpointChecksum(),
                "root_parameters=" + context.rootParameterChecksum(),
                "sampling=" + context.dataset().toString(),
                "model=" + FermiNetV1Configuration.REPRESENTATION_ID,
                "model_configuration=" + context.state().configuration().toString()));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
