package totah.lab.prometheus.fixtures;

import java.util.List;
import java.util.Map;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.prometheus.identity.CanonicalAtomId;
import totah.lab.prometheus.identity.CanonicalAtomMap;
import totah.lab.prometheus.identity.EvidenceAtomMap;
import totah.lab.prometheus.identity.ForceFieldAtomMap;
import totah.lab.prometheus.identity.GeometryIdentity;
import totah.lab.prometheus.identity.MoleculeIdentity;

/**
 * Small but realistic TSL (thiol-substituted ligand) fixture exercising the famous
 * mapping bug: the number inside a label is NOT the canonical serial.
 *
 * <p>Canonical serials and labels:
 * <pre>
 *   serial  9  -> "C8"
 *   serial 10  -> "C9"   (label number 9, serial 10)
 *   serial 11  -> "C10"  (label number 10, serial 11)
 *   serial 26  -> "S26"
 *   serial 56  -> "H56"
 * </pre>
 * C9 is canonical serial 10 and C10 is canonical serial 11 — any code that parses
 * a serial out of a label will swap these two atoms.
 */
public final class TslFixtures {

    public static final MoleculeIdentity TSL =
            new MoleculeIdentity("TSL", "thiol-substituted ligand", "C3H7S");

    private TslFixtures() {
    }

    /** Canonical atom map for TSL, in canonical (ascending serial) order. */
    public static CanonicalAtomMap canonicalMap() {
        return new CanonicalAtomMap(TSL, List.of(
                new CanonicalAtomId(9, "C8", "C"),
                new CanonicalAtomId(10, "C9", "C"),
                new CanonicalAtomId(11, "C10", "C"),
                new CanonicalAtomId(26, "S26", "S"),
                new CanonicalAtomId(56, "H56", "H")));
    }

    /**
     * Evidence map whose artifact file order differs from canonical order:
     * file order is [H56, C10, C9, S26, C8], i.e. filePosition -&gt; canonicalIndex
     * is [56, 11, 10, 26, 9].
     */
    public static EvidenceAtomMap evidenceMapReordered() {
        return new EvidenceAtomMap(canonicalMap(), List.of(56, 11, 10, 26, 9));
    }

    /**
     * GAFF2 typing of the TSL canonical map: the three carbons all share the
     * generic type "c6" (they may later receive distinct molecule-specific
     * parameters), sulfur is "sh", the thiol hydrogen is "hs".
     */
    public static ForceFieldAtomMap forceFieldMapGaff2() {
        return new ForceFieldAtomMap(canonicalMap(), "GAFF2", Map.of(
                9, "c6",
                10, "c6",
                11, "c6",
                26, "sh",
                56, "hs"));
    }

    /** Reference geometry in canonical order (serials 9, 10, 11, 26, 56). */
    public static List<Point3D> geometryA() {
        return List.of(
                new Point3D(0.00000000, 0.00000000, 0.00000000),
                new Point3D(1.53000000, 0.00000000, 0.00000000),
                new Point3D(2.29000000, 1.21000000, 0.00000000),
                new Point3D(-0.62000000, -1.51000000, 0.24000000),
                new Point3D(-1.68000000, -1.21000000, 0.18000000));
    }

    /** A different geometry (conformer) in canonical order. */
    public static List<Point3D> geometryB() {
        return List.of(
                new Point3D(0.00000000, 0.00000000, 0.00000000),
                new Point3D(1.51000000, 0.00000000, 0.00000000),
                new Point3D(2.31000000, -1.19000000, 0.11000000),
                new Point3D(-0.59000000, -1.49000000, -0.26000000),
                new Point3D(-1.66000000, -1.23000000, -0.15000000));
    }

    public static GeometryIdentity geometryIdentityA() {
        return GeometryIdentity.of(canonicalMap(), geometryA());
    }

    public static GeometryIdentity geometryIdentityB() {
        return GeometryIdentity.of(canonicalMap(), geometryB());
    }
}
