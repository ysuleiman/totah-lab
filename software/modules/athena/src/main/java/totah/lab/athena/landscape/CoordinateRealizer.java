package totah.lab.athena.landscape;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Deterministically realizes ligand-only rigid and allowed torsional changes. */
public final class CoordinateRealizer {
    public Result realize(Structure referenceLigand, Perturbation perturbation,
            Map<AtomReference, AtomReference> atomCorrespondence,
            Map<String, TorsionDefinition> allowedTorsions,
            String commonFrameProvenance) {
        Objects.requireNonNull(referenceLigand, "referenceLigand");
        Objects.requireNonNull(perturbation, "perturbation");
        atomCorrespondence = Map.copyOf(Objects.requireNonNull(
                atomCorrespondence, "atomCorrespondence"));
        allowedTorsions = Map.copyOf(Objects.requireNonNull(
                allowedTorsions, "allowedTorsions"));
        if (commonFrameProvenance == null || commonFrameProvenance.isBlank()) {
            return Result.rejected("COMMON_FRAME_PROVENANCE_MISSING");
        }
        Map<AtomReference, Point3D> coordinates = coordinates(referenceLigand);
        if (!atomCorrespondence.keySet().equals(coordinates.keySet())
                || !Set.copyOf(atomCorrespondence.values()).equals(coordinates.keySet())) {
            return Result.rejected("ATOM_CORRESPONDENCE_INCOMPLETE_OR_NONBIJECTIVE");
        }
        // Canonical identifier order is part of the realization definition;
        // torsion rotations generally do not commute.
        for (Map.Entry<String, Double> delta : perturbation.torsionDeltaDegrees()
                .entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            TorsionDefinition torsion = allowedTorsions.get(delta.getKey());
            if (torsion == null) return Result.rejected("TORSION_NOT_ALLOWED:" + delta.getKey());
            if (!coordinates.keySet().containsAll(torsion.rotatingAtoms())
                    || !coordinates.containsKey(torsion.axisStart())
                    || !coordinates.containsKey(torsion.axisEnd())) {
                return Result.rejected("TORSION_ATOM_MISSING:" + delta.getKey());
            }
            if (!referenceLigand.hasBond(torsion.axisStart(), torsion.axisEnd())) {
                return Result.rejected("TORSION_AXIS_NOT_BONDED:" + delta.getKey());
            }
            if (!torsion.rotatingAtoms().equals(
                    downstreamAtoms(referenceLigand, torsion))) {
                return Result.rejected("TORSION_ROTATING_SET_INVALID:" + delta.getKey());
            }
            if (!rotateSubset(coordinates, torsion, Math.toRadians(delta.getValue()))) {
                return Result.rejected("TORSION_AXIS_DEGENERATE:" + delta.getKey());
            }
        }
        Point3D centroid = centroid(coordinates.values());
        coordinates.replaceAll((ignored, point) -> translate(
                rotate(point, centroid, perturbation.rotationDegrees()),
                perturbation.translationAngstroms()));
        Structure realized = rebuild(referenceLigand, coordinates);
        return new Result(true, realized, perturbation, atomCorrespondence,
                centroid, commonFrameProvenance.trim(), "");
    }

    private static Map<AtomReference, Point3D> coordinates(Structure structure) {
        Map<AtomReference, Point3D> result = new LinkedHashMap<>();
        for (Chain chain : structure.getChains()) for (Residue residue : chain.residues())
            for (Atom atom : residue.getAtoms()) {
                AtomReference reference = new AtomReference(chain.id(), residue.getNumber(),
                        residue.getInsertionCode() == null ? ' ' : residue.getInsertionCode(),
                        atom.getName());
                result.put(reference, atom.getPosition());
            }
        return result;
    }

    private static Structure rebuild(Structure source,
            Map<AtomReference, Point3D> coordinates) {
        List<Chain> chains = new ArrayList<>();
        for (Chain chain : source.getChains()) {
            List<Residue> residues = new ArrayList<>();
            for (Residue residue : chain.residues()) {
                List<Atom> atoms = new ArrayList<>();
                for (Atom atom : residue.getAtoms()) {
                    AtomReference reference = new AtomReference(chain.id(), residue.getNumber(),
                            residue.getInsertionCode() == null ? ' ' : residue.getInsertionCode(),
                            atom.getName());
                    atoms.add(atom.toBuilder().position(coordinates.get(reference)).build());
                }
                residues.add(residue.toBuilder().atoms(atoms).build());
            }
            chains.add(new Chain(chain.id(), residues));
        }
        return new Structure(chains, source.bonds(), source.getConnectivityMetadata());
    }

    private static Point3D centroid(java.util.Collection<Point3D> points) {
        if (points.isEmpty()) throw new IllegalArgumentException("ligand has no atoms");
        double x = 0, y = 0, z = 0;
        for (Point3D point : points) { x += point.x(); y += point.y(); z += point.z(); }
        return new Point3D(x / points.size(), y / points.size(), z / points.size());
    }

    private static Point3D rotate(Point3D p, Point3D c,
            LigandConfiguration.EulerRotation degrees) {
        double x = p.x() - c.x(), y = p.y() - c.y(), z = p.z() - c.z();
        double rx = Math.toRadians(degrees.x());
        double ry = Math.toRadians(degrees.y());
        double rz = Math.toRadians(degrees.z());
        double y1 = y * Math.cos(rx) - z * Math.sin(rx);
        double z1 = y * Math.sin(rx) + z * Math.cos(rx);
        double x2 = x * Math.cos(ry) + z1 * Math.sin(ry);
        double z2 = -x * Math.sin(ry) + z1 * Math.cos(ry);
        double x3 = x2 * Math.cos(rz) - y1 * Math.sin(rz);
        double y3 = x2 * Math.sin(rz) + y1 * Math.cos(rz);
        return new Point3D(x3 + c.x(), y3 + c.y(), z2 + c.z());
    }

    private static Point3D translate(Point3D p, Point3D delta) {
        return new Point3D(p.x() + delta.x(), p.y() + delta.y(), p.z() + delta.z());
    }

    private static boolean rotateSubset(Map<AtomReference, Point3D> coordinates,
            TorsionDefinition torsion, double radians) {
        Point3D a = coordinates.get(torsion.axisStart());
        Point3D b = coordinates.get(torsion.axisEnd());
        double ux = b.x() - a.x(), uy = b.y() - a.y(), uz = b.z() - a.z();
        double norm = Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (norm == 0.0) return false;
        ux /= norm; uy /= norm; uz /= norm;
        double cos = Math.cos(radians), sin = Math.sin(radians);
        for (AtomReference atom : torsion.rotatingAtoms()) {
            Point3D p = coordinates.get(atom);
            double x = p.x() - a.x(), y = p.y() - a.y(), z = p.z() - a.z();
            double dot = ux * x + uy * y + uz * z;
            double cx = uy * z - uz * y, cy = uz * x - ux * z, cz = ux * y - uy * x;
            coordinates.put(atom, new Point3D(a.x() + x * cos + cx * sin + ux * dot * (1-cos),
                    a.y() + y * cos + cy * sin + uy * dot * (1-cos),
                    a.z() + z * cos + cz * sin + uz * dot * (1-cos)));
        }
        return true;
    }

    private static Set<AtomReference> downstreamAtoms(Structure structure,
            TorsionDefinition torsion) {
        Map<AtomReference, Set<AtomReference>> adjacency = new LinkedHashMap<>();
        for (var bond : structure.bonds()) {
            if ((bond.atom1().equals(torsion.axisStart())
                    && bond.atom2().equals(torsion.axisEnd()))
                    || (bond.atom2().equals(torsion.axisStart())
                    && bond.atom1().equals(torsion.axisEnd()))) continue;
            adjacency.computeIfAbsent(bond.atom1(), ignored -> new java.util.LinkedHashSet<>())
                    .add(bond.atom2());
            adjacency.computeIfAbsent(bond.atom2(), ignored -> new java.util.LinkedHashSet<>())
                    .add(bond.atom1());
        }
        Set<AtomReference> visited = new java.util.LinkedHashSet<>();
        java.util.ArrayDeque<AtomReference> queue = new java.util.ArrayDeque<>();
        queue.add(torsion.axisEnd());
        while (!queue.isEmpty()) {
            AtomReference atom = queue.removeFirst();
            if (!visited.add(atom)) continue;
            queue.addAll(adjacency.getOrDefault(atom, Set.of()));
        }
        if (visited.contains(torsion.axisStart())) return Set.of();
        visited.remove(torsion.axisEnd());
        return Set.copyOf(visited);
    }

    public record TorsionDefinition(AtomReference axisStart, AtomReference axisEnd,
            Set<AtomReference> rotatingAtoms) {
        public TorsionDefinition {
            Objects.requireNonNull(axisStart, "axisStart");
            Objects.requireNonNull(axisEnd, "axisEnd");
            rotatingAtoms = Set.copyOf(Objects.requireNonNull(rotatingAtoms, "rotatingAtoms"));
            if (axisStart.equals(axisEnd) || rotatingAtoms.contains(axisStart)) {
                throw new IllegalArgumentException("invalid torsion definition");
            }
        }
    }

    public record Result(boolean valid, Structure coordinates,
            Perturbation appliedTransform,
            Map<AtomReference, AtomReference> atomMapping,
            Point3D rotationCenterAngstroms,
            String commonFrameProvenance, String rejectionReason) {
        private static Result rejected(String reason) {
            return new Result(false, null, null, Map.of(), null, "", reason);
        }
    }
}
