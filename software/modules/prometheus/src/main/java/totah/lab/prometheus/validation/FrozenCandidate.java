package totah.lab.prometheus.validation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
        List<String> fields = new ArrayList<>(List.of(
                candidate.candidateId(), candidate.molecule().moleculeId(), plan.planChecksum()));

        ForceFieldAtomMap typing = candidate.atomTyping();
        fields.add(typing.forceFieldFamily());
        for (CanonicalAtomId atom : typing.canonical().atoms()) {
            fields.add(CanonicalHashing.sequence(List.of(Integer.toString(atom.canonicalIndex()), atom.label(),
                    atom.elementSymbol(), typing.typeOf(atom.canonicalIndex()))));
        }

        for (DerivedParameter parameter : candidate.parameters()) {
            ParameterProvenance provenance = parameter.provenance();
            List<String> indices = parameter.canonicalAtomIndices().stream().map(Object::toString).toList();
            fields.add(CanonicalHashing.sequence(List.of(parameter.parameterId(), parameter.kind().name(),
                    parameter.functionalForm(), CanonicalHashing.sequence(indices),
                    CanonicalHashing.format(parameter.value()), parameter.unit(), provenance.derivationMethod(),
                    CanonicalHashing.sequence(provenance.sourceEvidenceHashes()), provenance.developmentDatasetId(),
                    provenance.algorithmVersion(), provenance.literatureReference(), provenance.candidateLineageId(),
                    provenance.validationStatus().name())));
        }
        return CanonicalHashing.sha256Hex(CanonicalHashing.sequence(fields));
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
