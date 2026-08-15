package totah.lab.prometheus.comparability;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.evidence.EvidenceIdentity;
import totah.lab.prometheus.evidence.QmProtocol;

/**
 * Decides whether two pieces of evidence may be compared energetically.
 *
 * <p>Rules, applied in order:
 * <ol>
 *   <li>Any blank protocol metadata field in either identity →
 *       {@code INCOMPLETE_METADATA}.</li>
 *   <li>Different {@link EnergyTarget} (derived from calculation type) →
 *       {@code INCOMPATIBLE_ENERGY_TARGET}; the reason names both targets.</li>
 *   <li>Different molecule, formal charge or multiplicity →
 *       {@code INCOMPATIBLE_PROTOCOL}; being the same molecule under the same
 *       electronic state is a precondition for any energetic comparison.</li>
 *   <li>Same target, same calculation type, same protocol key →
 *       {@code COMPARABLE_AFTER_REFERENCE_SHIFT} for the CONFORMATIONAL target
 *       (absolute energies need a common reference zero), {@code COMPARABLE}
 *       otherwise.</li>
 *   <li>Same target, same calculation type, different protocol key →
 *       {@code SAME_GEOMETRY_DIFFERENT_METHOD} if the geometry is identical
 *       (re-computation of the same coordinates under a different method),
 *       otherwise {@code INCOMPATIBLE_PROTOCOL} with a reason naming the
 *       differing protocol fields.</li>
 *   <li>Different calculation types mapping to the same energy target
 *       (e.g. SINGLE_POINT vs TORSION_SCAN) →
 *       {@code COMPARABLE_AFTER_REFERENCE_SHIFT} only if the protocol keys are
 *       equal, otherwise {@code INCOMPATIBLE_PROTOCOL}.</li>
 * </ol>
 *
 * <p>The key TSL fact this encodes: PBE-D3(BJ)/def2-SVP conformational energies,
 * PBE0-D3(BJ)/def2-TZVP counterpoise interaction energies, and HF/6-31G(d) ESP
 * evidence are mutually INCOMPATIBLE — they differ in energy target and/or
 * protocol, so their numbers may never be pooled naively.
 */
public final class ProtocolComparability {

    public ComparabilityDecision compare(EvidenceIdentity a, EvidenceIdentity b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");

        List<String> blankA = blankFields(a.protocol());
        List<String> blankB = blankFields(b.protocol());
        if (!blankA.isEmpty() || !blankB.isEmpty()) {
            return new ComparabilityDecision(
                    ComparabilityVerdict.INCOMPLETE_METADATA,
                    "blank protocol metadata: a=" + blankA + ", b=" + blankB);
        }

        EnergyTarget targetA = EnergyTarget.of(a.calculationType());
        EnergyTarget targetB = EnergyTarget.of(b.calculationType());
        if (targetA != targetB) {
            return new ComparabilityDecision(
                    ComparabilityVerdict.INCOMPATIBLE_ENERGY_TARGET,
                    "different energy targets: " + targetA + " vs " + targetB);
        }

        if (!a.molecule().equals(b.molecule())) {
            return new ComparabilityDecision(
                    ComparabilityVerdict.INCOMPATIBLE_PROTOCOL,
                    "different molecule");
        }
        if (a.formalCharge() != b.formalCharge()) {
            return new ComparabilityDecision(
                    ComparabilityVerdict.INCOMPATIBLE_PROTOCOL,
                    "different formal charge: " + a.formalCharge() + " vs " + b.formalCharge());
        }
        if (a.multiplicity() != b.multiplicity()) {
            return new ComparabilityDecision(
                    ComparabilityVerdict.INCOMPATIBLE_PROTOCOL,
                    "different multiplicity: " + a.multiplicity() + " vs " + b.multiplicity());
        }

        boolean sameProtocol = a.protocol().protocolKey().equals(b.protocol().protocolKey());
        boolean sameCalculationType = a.calculationType() == b.calculationType();

        if (sameCalculationType && sameProtocol) {
            if (targetA == EnergyTarget.CONFORMATIONAL) {
                return new ComparabilityDecision(
                        ComparabilityVerdict.COMPARABLE_AFTER_REFERENCE_SHIFT,
                        "same protocol and calculation type; conformational absolute energies"
                                + " require a common reference zero");
            }
            return new ComparabilityDecision(
                    ComparabilityVerdict.COMPARABLE,
                    "same energy target, calculation type and protocol");
        }

        if (sameCalculationType) {
            if (a.geometry().equals(b.geometry())) {
                return new ComparabilityDecision(
                        ComparabilityVerdict.SAME_GEOMETRY_DIFFERENT_METHOD,
                        "same geometry evaluated under different protocols: differing fields "
                                + differingProtocolFields(a.protocol(), b.protocol()));
            }
            return new ComparabilityDecision(
                    ComparabilityVerdict.INCOMPATIBLE_PROTOCOL,
                    "different protocols and different geometries: differing fields "
                            + differingProtocolFields(a.protocol(), b.protocol()));
        }

        if (sameProtocol) {
            return new ComparabilityDecision(
                    ComparabilityVerdict.COMPARABLE_AFTER_REFERENCE_SHIFT,
                    "same protocol, different calculation types (" + a.calculationType()
                            + " vs " + b.calculationType() + ") within target " + targetA
                            + "; energies require a common reference zero");
        }
        return new ComparabilityDecision(
                ComparabilityVerdict.INCOMPATIBLE_PROTOCOL,
                "different calculation types (" + a.calculationType() + " vs " + b.calculationType()
                        + ") and different protocols: differing fields "
                        + differingProtocolFields(a.protocol(), b.protocol()));
    }

    private static List<String> blankFields(QmProtocol protocol) {
        List<String> blank = new ArrayList<>();
        if (protocol.method().isBlank()) {
            blank.add("method");
        }
        if (protocol.basis().isBlank()) {
            blank.add("basis");
        }
        if (protocol.dispersion().isBlank()) {
            blank.add("dispersion");
        }
        if (protocol.environment().isBlank()) {
            blank.add("environment");
        }
        if (protocol.software().isBlank()) {
            blank.add("software");
        }
        if (protocol.softwareVersion().isBlank()) {
            blank.add("softwareVersion");
        }
        return blank;
    }

    private static List<String> differingProtocolFields(QmProtocol a, QmProtocol b) {
        List<String> differing = new ArrayList<>();
        if (!a.method().equals(b.method())) {
            differing.add("method");
        }
        if (!a.basis().equals(b.basis())) {
            differing.add("basis");
        }
        if (!a.dispersion().equals(b.dispersion())) {
            differing.add("dispersion");
        }
        if (!a.environment().equals(b.environment())) {
            differing.add("environment");
        }
        if (a.counterpoise() != b.counterpoise()) {
            differing.add("counterpoise");
        }
        if (!a.software().equals(b.software())) {
            differing.add("software");
        }
        if (!a.softwareVersion().equals(b.softwareVersion())) {
            differing.add("softwareVersion");
        }
        return differing;
    }
}
