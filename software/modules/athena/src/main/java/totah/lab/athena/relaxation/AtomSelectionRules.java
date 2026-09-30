package totah.lab.athena.relaxation;

import java.util.Objects;

/** Explicit atom mobility rules and their immutable provenance. */
public record AtomSelectionRules(
        Mobility backboneFixedSelection,
        Mobility backboneRestrainedSelection,
        Mobility localSideChainMobileSelection,
        Mobility localSideChainRestrainedSelection,
        Mobility outsideRadiusSideChainSelection,
        Mobility ligandUnrestrainedSelection,
        Mobility ligandRestrainedSelection,
        Mobility samUnrestrainedSelection,
        Mobility samRestrainedSelection,
        Mobility solventSelection,
        Mobility ionSelection,
        RadiusReference localRadiusReference,
        String provenance) {
    public AtomSelectionRules {
        for (Mobility value : new Mobility[]{backboneFixedSelection,
                backboneRestrainedSelection, localSideChainMobileSelection,
                localSideChainRestrainedSelection, outsideRadiusSideChainSelection,
                ligandUnrestrainedSelection, ligandRestrainedSelection,
                samUnrestrainedSelection, samRestrainedSelection, solventSelection,
                ionSelection}) Objects.requireNonNull(value, "mobility selection");
        Objects.requireNonNull(localRadiusReference, "localRadiusReference");
        if (backboneFixedSelection != Mobility.FIXED
                || backboneRestrainedSelection != Mobility.POSITION_RESTRAINED
                || localSideChainMobileSelection != Mobility.LOCAL_MOBILE
                || localSideChainRestrainedSelection != Mobility.POSITION_RESTRAINED
                || ligandUnrestrainedSelection != Mobility.UNRESTRAINED
                || ligandRestrainedSelection != Mobility.POSITION_RESTRAINED
                || samUnrestrainedSelection != Mobility.UNRESTRAINED
                || samRestrainedSelection != Mobility.POSITION_RESTRAINED) {
            throw new IllegalArgumentException("core mobility semantics are inconsistent");
        }
        provenance = Objects.requireNonNull(provenance, "provenance").trim();
        if (provenance.isEmpty()) throw new IllegalArgumentException("selection provenance required");
    }

    public enum Mobility { FIXED, POSITION_RESTRAINED, LOCAL_MOBILE, UNRESTRAINED }
    public enum RadiusReference { LIGAND_ALL_ATOMS, LIGAND_HEAVY_ATOMS }
}
