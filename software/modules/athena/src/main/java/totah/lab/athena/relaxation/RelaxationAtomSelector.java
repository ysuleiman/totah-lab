package totah.lab.athena.relaxation;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Structure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Deterministically materializes the explicit per-particle mobility mask. */
public final class RelaxationAtomSelector {
    /** Protein-defined mode; accepts an empty ligand and never uses ligand coordinates. */
    public Selection select(LocalRelaxationProtocol protocol, Structure receptor,
            Structure ligand, Structure sam, ProteinDefinedMask mask) {
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(receptor, "receptor");
        Objects.requireNonNull(ligand, "ligand");
        Objects.requireNonNull(mask, "mask");
        validateCompleteMapping(protocol.systemAtomMapping(), receptor, ligand, sam);
        if (mask.rules().stream().anyMatch(r -> r.mobility()
                == AtomSelectionRules.Mobility.POSITION_RESTRAINED)
                && protocol.localSideChainRestraintForceConstant().isEmpty()) {
            throw new IllegalArgumentException("protein mask requires a local side-chain restraint constant");
        }
        int[] matches = new int[mask.rules().size()];
        List<SelectedAtom> selected = new ArrayList<>();
        for (var entry : protocol.systemAtomMapping().entries().stream()
                .sorted(Comparator.comparingInt(SystemAtomMapping.Entry::openMmParticleIndex)).toList()) {
            if (source(entry.componentRole(), receptor, ligand, sam).findAtom(entry.gaiaAtom()).isEmpty()) {
                throw new IllegalArgumentException("mapped atom is absent: " + entry);
            }
            var mobility = AtomSelectionRules.Mobility.FIXED;
            String reason = "PROTEIN_DEFINED_MASK_V1 default FIXED";
            boolean matched = false;
            for (int i = 0; i < mask.rules().size(); i++) {
                var rule = mask.rules().get(i);
                if (!rule.matches(entry)) continue;
                if (matched) throw new IllegalArgumentException("overlapping protein mask rules: " + entry);
                matched = true;
                matches[i]++;
                mobility = rule.mobility();
                reason = "PROTEIN_DEFINED_MASK_V1 " + rule.canonical();
            }
            selected.add(atom(entry, mobility, reason));
        }
        for (int i = 0; i < matches.length; i++) {
            var rule = mask.rules().get(i);
            if (matches[i] == 0 || (!rule.atomNames().isEmpty() && matches[i] != rule.atomNames().size())) {
                throw new IllegalArgumentException("protein mask rule does not resolve completely: " + rule);
            }
        }
        String semantic = sha256(mask.canonical() + "\nSIDECHAIN_K="
                + force(protocol.localSideChainRestraintForceConstant()));
        return new Selection(selected, protocol.systemAtomMapping().sha256(), semantic,
                maskHash(protocol.systemAtomMapping().sha256(), semantic, selected), mask.provenance());
    }

    public Selection select(LocalRelaxationProtocol protocol,
            Structure receptor, Structure ligand, Structure sam) {
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(receptor, "receptor");
        Objects.requireNonNull(ligand, "ligand");
        List<Point3D> radiusReference = ligandAtoms(ligand,
                protocol.atomSelectionRules().localRadiusReference());
        if (radiusReference.isEmpty()) {
            throw new IllegalArgumentException("local-radius ligand reference set is empty");
        }
        validateCompleteMapping(protocol.systemAtomMapping(), receptor, ligand, sam);
        List<SelectedAtom> selected = new ArrayList<>();
        for (SystemAtomMapping.Entry entry : protocol.systemAtomMapping().entries().stream()
                .sorted(Comparator.comparingInt(SystemAtomMapping.Entry::openMmParticleIndex)).toList()) {
            Structure source = source(entry.componentRole(), receptor, ligand, sam);
            Atom atom = source.findAtom(entry.gaiaAtom()).orElseThrow(() ->
                    new IllegalArgumentException("mapped atom is absent: " + entry));
            selected.add(classify(entry, atom.getPosition(), radiusReference, protocol));
        }
        String semanticHash = semanticHash(protocol);
        String maskHash = maskHash(protocol.systemAtomMapping().sha256(), semanticHash, selected);
        return new Selection(selected, protocol.systemAtomMapping().sha256(), semanticHash,
                maskHash, protocol.atomSelectionRules().provenance());
    }

    private static void validateCompleteMapping(SystemAtomMapping mapping, Structure receptor,
            Structure ligand, Structure sam) {
        long expected = receptor.getAtomCount() + ligand.getAtomCount()
                + (sam == null ? 0 : sam.getAtomCount());
        if (mapping.entries().size() != expected) {
            throw new IllegalArgumentException("atom mapping does not cover every supplied system atom");
        }
    }

    private static SelectedAtom classify(SystemAtomMapping.Entry entry, Point3D position,
            List<Point3D> ligandReference, LocalRelaxationProtocol protocol) {
        AtomSelectionRules rules = protocol.atomSelectionRules();
        return switch (entry.componentRole()) {
            case PROTEIN_BACKBONE -> protocol.backbonePolicy()
                    == LocalRelaxationProtocol.BackbonePolicy.FIXED
                    ? atom(entry, rules.backboneFixedSelection(), "backbone atom under FIXED policy")
                    : atom(entry, rules.backboneRestrainedSelection(),
                    "backbone atom under HARMONICALLY_RESTRAINED policy");
            case PROTEIN_SIDE_CHAIN -> {
                double nearest = ligandReference.stream().mapToDouble(position::distance).min().orElseThrow();
                if (nearest <= protocol.receptorMobileRadiusAngstroms()) {
                    yield protocol.sideChainPolicy()
                            == LocalRelaxationProtocol.SideChainPolicy.WITHIN_RADIUS_MOBILE
                            ? atom(entry, rules.localSideChainMobileSelection(),
                            "side-chain atom within local radius; nearest=" + nearest + " A")
                            : atom(entry, rules.localSideChainRestrainedSelection(),
                            "side-chain atom within local radius under restraint; nearest=" + nearest + " A");
                }
                yield atom(entry, rules.outsideRadiusSideChainSelection(),
                        "side-chain atom outside local radius; nearest=" + nearest + " A");
            }
            case LIGAND -> protocol.ligandRestraint().policy() == LocalRelaxationProtocol.Policy.NONE
                    ? atom(entry, rules.ligandUnrestrainedSelection(), "ligand atom under NONE restraint policy")
                    : atom(entry, rules.ligandRestrainedSelection(), "ligand atom under active restraint policy");
            case SAM -> protocol.samRestraint().policy() == LocalRelaxationProtocol.Policy.NONE
                    ? atom(entry, rules.samUnrestrainedSelection(), "SAM atom under NONE restraint policy")
                    : atom(entry, rules.samRestrainedSelection(), "SAM atom under active restraint policy");
            case SOLVENT -> atom(entry, rules.solventSelection(), "solvent atom under explicit solvent rule");
            case ION -> atom(entry, rules.ionSelection(), "ion under explicit ion rule");
        };
    }

    private static SelectedAtom atom(SystemAtomMapping.Entry entry,
            AtomSelectionRules.Mobility mobility, String reason) {
        return new SelectedAtom(entry.openMmParticleIndex(), entry.gaiaAtom(),
                entry.componentRole(), mobility, reason);
    }

    private static Structure source(SystemAtomMapping.ComponentRole role, Structure receptor,
            Structure ligand, Structure sam) {
        return switch (role) {
            case LIGAND -> ligand;
            case SAM -> Objects.requireNonNull(sam, "SAM structure required by atom mapping");
            case PROTEIN_BACKBONE, PROTEIN_SIDE_CHAIN, SOLVENT, ION -> receptor;
        };
    }

    private static List<Point3D> ligandAtoms(Structure ligand,
            AtomSelectionRules.RadiusReference reference) {
        List<Point3D> result = new ArrayList<>();
        ligand.getChains().forEach(chain -> chain.residues().forEach(residue ->
                residue.getAtoms().stream().filter(atom -> reference
                        == AtomSelectionRules.RadiusReference.LIGAND_ALL_ATOMS || atom.isHeavyAtom())
                        .forEach(atom -> result.add(atom.getPosition()))));
        return result;
    }

    private static String semanticHash(LocalRelaxationProtocol p) {
        StringBuilder canonical = new StringBuilder("athena-relaxation-selection-semantics-v1\n")
                .append(p.backbonePolicy()).append('|')
                .append(force(p.backboneRestraintForceConstant())).append('|')
                .append(p.sideChainPolicy()).append('|')
                .append(force(p.localSideChainRestraintForceConstant())).append('|')
                .append(Double.toHexString(p.receptorMobileRadiusAngstroms())).append('|')
                .append(restraint(p.ligandRestraint())).append('|')
                .append(restraint(p.samRestraint())).append('\n');
        AtomSelectionRules r = p.atomSelectionRules();
        canonical.append(r.backboneFixedSelection()).append('|')
                .append(r.backboneRestrainedSelection()).append('|')
                .append(r.localSideChainMobileSelection()).append('|')
                .append(r.localSideChainRestrainedSelection()).append('|')
                .append(r.outsideRadiusSideChainSelection()).append('|')
                .append(r.ligandUnrestrainedSelection()).append('|')
                .append(r.ligandRestrainedSelection()).append('|')
                .append(r.samUnrestrainedSelection()).append('|')
                .append(r.samRestrainedSelection()).append('|')
                .append(r.solventSelection()).append('|').append(r.ionSelection()).append('|')
                .append(r.localRadiusReference()).append('|').append(r.provenance());
        return sha256(canonical.toString());
    }

    private static String force(java.util.Optional<HarmonicForceConstant> force) {
        return force.map(value -> Double.toHexString(value.value()) + "@" + value.unit())
                .orElse("ABSENT");
    }

    private static String restraint(LocalRelaxationProtocol.Restraint restraint) {
        return restraint.policy() + ":" + force(restraint.forceConstant()) + ":"
                + Double.toHexString(restraint.maximumDisplacementAngstroms());
    }

    private static String maskHash(String mappingHash, String semanticHash,
            List<SelectedAtom> selected) {
        StringBuilder canonical = new StringBuilder(mappingHash).append('|').append(semanticHash).append('\n');
        selected.forEach(atom -> canonical.append(atom.openMmParticleIndex()).append('|')
                .append(atom.mobility()).append('|').append(atom.reason()).append('\n'));
        return sha256(canonical.toString());
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public record SelectedAtom(int openMmParticleIndex,
            totah.lab.gaia.structure.AtomReference gaiaAtom,
            SystemAtomMapping.ComponentRole componentRole,
            AtomSelectionRules.Mobility mobility, String reason) {
        public SelectedAtom {
            if (openMmParticleIndex < 0 || reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("complete selected-atom evidence required");
            }
            Objects.requireNonNull(gaiaAtom, "gaiaAtom");
            Objects.requireNonNull(componentRole, "componentRole");
            Objects.requireNonNull(mobility, "mobility");
        }
    }

    public record Selection(List<SelectedAtom> atoms, String mappingSha256,
            String semanticPolicySha256, String selectionMaskSha256,
            String selectionRuleProvenance) {
        public Selection {
            atoms = List.copyOf(atoms);
        }
    }
}
