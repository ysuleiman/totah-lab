package totah.lab.prometheus.validation;

import java.time.Instant;
import java.util.Objects;
import java.util.StringJoiner;

import totah.lab.prometheus.candidate.DerivedParameter;
import totah.lab.prometheus.candidate.ParameterCandidate;
import totah.lab.prometheus.candidate.ParameterProvenance;
import totah.lab.prometheus.identity.CanonicalAtomId;
import totah.lab.prometheus.identity.CanonicalHashing;
import totah.lab.prometheus.identity.ForceFieldAtomMap;

/**
 * An immutable, checksummed snapshot of a {@link ParameterCandidate} taken at
 * the moment of freeze, together with the preregistered {@link ValidationPlan}
 * it will be judged against.
 *
 * <p>Frozen candidates never change: this class exposes no mutation methods,
 * no {@code deriveChild}, and no refit path. A new parameter idea starts a new
 * development cycle with a new {@code ParameterCandidate} — it never edits a
 * frozen one.
 */
public final class FrozenCandidate {

    private final ParameterCandidate candidate;
    private final ValidationPlan plan;
    private final Instant frozenAt;
    private final String freezeChecksum;

    private FrozenCandidate(
            ParameterCandidate candidate,
            ValidationPlan plan,
            Instant frozenAt,
            String freezeChecksum) {

        this.candidate = candidate;
        this.plan = plan;
        this.frozenAt = frozenAt;
        this.freezeChecksum = freezeChecksum;
    }

    /**
     * Freezes {@code candidate} against {@code plan}. The freeze checksum is a
     * SHA-256 over the canonical serialization of the candidate content (id,
     * molecule, atom typing, and every parameter with value, unit and
     * provenance) plus the plan checksum; the same candidate and plan always
     * produce the same checksum.
     *
     * @throws IllegalStateException if the plan is not preregistered — a
     *         candidate cannot be frozen without a preregistered validation plan
     */
    public static FrozenCandidate freeze(ParameterCandidate candidate, ValidationPlan plan) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(plan, "plan");
        if (!plan.preregistered()) {
            throw new IllegalStateException(
                    "cannot freeze a candidate without a preregistered validation plan");
        }
        return new FrozenCandidate(candidate, plan, Instant.now(), checksumOf(candidate, plan));
    }

    private static String checksumOf(ParameterCandidate candidate, ValidationPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("candidateId=").append(candidate.candidateId())
                .append('\n').append("molecule=").append(candidate.molecule().moleculeId())
                .append('\n').append("plan=").append(plan.planChecksum());

        ForceFieldAtomMap typing = candidate.atomTyping();
        sb.append('\n').append("forceField=").append(typing.forceFieldFamily());
        for (CanonicalAtomId atom : typing.canonical().atoms()) {
            sb.append('\n').append("atom=").append(atom.canonicalIndex())
                    .append('|').append(atom.label())
                    .append('|').append(atom.elementSymbol())
                    .append('|').append(typing.typeOf(atom.canonicalIndex()));
        }

        for (DerivedParameter parameter : candidate.parameters()) {
            ParameterProvenance provenance = parameter.provenance();
            StringJoiner indices = new StringJoiner(",");
            for (Integer index : parameter.canonicalAtomIndices()) {
                indices.add(index.toString());
            }
            sb.append('\n').append("param=").append(parameter.parameterId())
                    .append('|').append(parameter.kind().name())
                    .append('|').append(parameter.functionalForm())
                    .append('|').append(indices)
                    .append('|').append(CanonicalHashing.format(parameter.value()))
                    .append('|').append(parameter.unit())
                    .append('|').append(provenance.derivationMethod())
                    .append('|').append(String.join(",", provenance.sourceEvidenceHashes()))
                    .append('|').append(provenance.developmentDatasetId())
                    .append('|').append(provenance.algorithmVersion())
                    .append('|').append(provenance.literatureReference())
                    .append('|').append(provenance.candidateLineageId())
                    .append('|').append(provenance.validationStatus().name());
        }
        return CanonicalHashing.sha256Hex(sb.toString());
    }

    public ParameterCandidate candidate() {
        return candidate;
    }

    public ValidationPlan plan() {
        return plan;
    }

    public String freezeChecksum() {
        return freezeChecksum;
    }

    public Instant frozenAt() {
        return frozenAt;
    }
}
