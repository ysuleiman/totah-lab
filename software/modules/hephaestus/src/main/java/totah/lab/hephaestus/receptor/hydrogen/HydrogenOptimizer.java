package totah.lab.hephaestus.receptor.hydrogen;

import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.amber.AmberParameterSet;
import totah.lab.hephaestus.amber.ResidueTemplate;
import totah.lab.hephaestus.amber.ResidueTemplateProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Optimizes existing rotatable hydrogen coordinates without changing chemistry. */
public final class HydrogenOptimizer {

    private static final double[] ROTATIONS = {
            0.0, 60.0, 120.0, 180.0, 240.0, 300.0
    };

    private static final double SCORE_CUTOFF = 10.0;
    private static final double COULOMB_FACTOR = 332.0;
    private static final double CLASH_PENALTY = 50.0;

    private final ResidueTemplateProvider templates;
    private final AmberParameterSet parameters;
    private final double clashCutoff;

    public HydrogenOptimizer(
            ResidueTemplateProvider templates,
            AmberParameterSet parameters,
            double clashCutoff) {

        this.templates = Objects.requireNonNull(
                templates,
                "templates");
        this.parameters = Objects.requireNonNull(
                parameters,
                "parameters");

        if (!Double.isFinite(clashCutoff)
                || clashCutoff < 0.0) {
            throw new IllegalArgumentException(
                    "clashCutoff must be finite and non-negative.");
        }

        this.clashCutoff = clashCutoff;
    }

    public List<Atom> optimize(
            String chainId,
            Residue residue,
            Structure environment,
            String amberTemplate,
            Map<String, String> environmentTemplates) {

        Objects.requireNonNull(chainId, "chainId");
        Objects.requireNonNull(residue, "residue");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(environmentTemplates, "environmentTemplates");

        List<Atom> optimized = new ArrayList<>(residue.getAtoms());

        for (int index = 0; index < optimized.size(); index++) {
            Atom hydrogen = optimized.get(index);
            if (hydrogen.getElement() != Element.H
                    || isBackboneHydrogen(hydrogen.getName())) {
                continue;
            }

            Atom parent = nearestHeavyAtom(hydrogen, optimized, null);
            if (parent == null) {
                continue;
            }

            Atom anchor = nearestHeavyAtom(parent, optimized, parent);
            if (anchor == null) {
                continue;
            }

            Point3D bestPosition = hydrogen.getPosition();
            double bestScore = score(
                    hydrogen,
                    bestPosition,
                    parent,
                    chainId,
                    residue,
                    optimized,
                    environment,
                    amberTemplate,
                    environmentTemplates);

            for (double angle : ROTATIONS) {
                Point3D candidate = rotateAroundAxis(
                        hydrogen.getPosition(),
                        anchor.getPosition(),
                        parent.getPosition(),
                        Math.toRadians(angle));

                double score = score(
                        hydrogen,
                        candidate,
                        parent,
                        chainId,
                        residue,
                        optimized,
                        environment,
                        amberTemplate,
                        environmentTemplates);

                if (score < bestScore) {
                    bestScore = score;
                    bestPosition = candidate;
                }
            }

            if (bestPosition.distance(hydrogen.getPosition()) > 1.0e-12) {
                optimized.set(
                        index,
                        hydrogen.toBuilder()
                                .position(bestPosition)
                                .build());
            }
        }

        return List.copyOf(optimized);
    }

    private boolean isBackboneHydrogen(String atomName) {
        return "H".equals(atomName)
                || "H1".equals(atomName)
                || "H2".equals(atomName)
                || "H3".equals(atomName)
                || "HA".equals(atomName)
                || "HA2".equals(atomName)
                || "HA3".equals(atomName);
    }

    private double score(
            Atom hydrogen,
            Point3D position,
            Atom parent,
            String chainId,
            Residue residue,
            List<Atom> optimizedResidueAtoms,
            Structure environment,
            String amberTemplate,
            Map<String, String> environmentTemplates) {

        double charge = charge(hydrogen, amberTemplate);
        String type = amberType(hydrogen, amberTemplate);
        double score = 0.0;

        for (Chain chain : environment.getChains()) {
            for (Residue otherResidue : chain.residues()) {
                String otherTemplate = environmentTemplates.get(
                        residueKey(chain.id(), otherResidue));

                // The residue being optimized may already contain moved
                // sibling hydrogens; score against the updated positions,
                // not the stale ones held by the environment structure.
                List<Atom> otherAtoms = otherResidue == residue
                        ? optimizedResidueAtoms
                        : otherResidue.getAtoms();

                for (Atom other : otherAtoms) {
                    if (other == hydrogen || other == parent) {
                        continue;
                    }

                    double distance = position.distance(other.getPosition());
                    if (distance > SCORE_CUTOFF || distance < 1.0e-9) {
                        continue;
                    }

                    if (distance < clashCutoff) {
                        score += CLASH_PENALTY
                                * (clashCutoff - distance)
                                / Math.max(clashCutoff, 1.0e-9);
                        continue;
                    }

                    double otherCharge = charge(other, otherTemplate);
                    score += COULOMB_FACTOR
                            * charge
                            * otherCharge
                            / distance;

                    String otherType = amberType(other, otherTemplate);
                    if (type != null && otherType != null) {
                        score += Math.min(
                                0.0,
                                parameters.ljEnergy(
                                        type,
                                        otherType,
                                        distance));
                    }
                }
            }
        }

        return score;
    }

    private Atom nearestHeavyAtom(
            Atom atom,
            List<Atom> atoms,
            Atom excluded) {

        Atom nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;

        for (Atom candidate : atoms) {
            if (candidate == atom
                    || candidate == excluded
                    || candidate.getElement() == Element.H) {
                continue;
            }

            double distance = atom.getPosition()
                    .distance(candidate.getPosition());

            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }

        return nearestDistance <= 1.9 ? nearest : null;
    }

    private double charge(
            Atom atom,
            String templateName) {

        var atomTemplate = atomTemplate(atom, templateName);
        return atomTemplate == null
                ? atom.getCharge()
                : atomTemplate.getCharge();
    }

    private String amberType(
            Atom atom,
            String templateName) {

        var atomTemplate = atomTemplate(atom, templateName);
        return atomTemplate == null
                ? atom.getAmberType()
                : atomTemplate.getAmberType();
    }

    private totah.lab.hephaestus.amber.AtomTemplate atomTemplate(
            Atom atom,
            String templateName) {

        if (templateName == null) {
            return null;
        }

        ResidueTemplate template = templates.getTemplate(templateName);
        return template == null
                ? null
                : template.getAtomMap().get(atom.getName());
    }

    private Point3D rotateAroundAxis(
            Point3D point,
            Point3D axisStart,
            Point3D axisEnd,
            double angle) {

        double ux = axisEnd.x() - axisStart.x();
        double uy = axisEnd.y() - axisStart.y();
        double uz = axisEnd.z() - axisStart.z();
        double magnitude = Math.sqrt(ux * ux + uy * uy + uz * uz);

        if (magnitude < 1.0e-12) {
            return point;
        }

        ux /= magnitude;
        uy /= magnitude;
        uz /= magnitude;

        double x = point.x() - axisStart.x();
        double y = point.y() - axisStart.y();
        double z = point.z() - axisStart.z();
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        double dot = ux * x + uy * y + uz * z;

        return new Point3D(
                axisStart.x() + x * cosine
                        + (uy * z - uz * y) * sine
                        + ux * dot * (1.0 - cosine),
                axisStart.y() + y * cosine
                        + (uz * x - ux * z) * sine
                        + uy * dot * (1.0 - cosine),
                axisStart.z() + z * cosine
                        + (ux * y - uy * x) * sine
                        + uz * dot * (1.0 - cosine));
    }

    private String residueKey(
            String chainId,
            Residue residue) {

        Character insertionCode = residue.getInsertionCode();
        return chainId
                + ":"
                + residue.getNumber()
                + (insertionCode == null ? "" : insertionCode);
    }
}
