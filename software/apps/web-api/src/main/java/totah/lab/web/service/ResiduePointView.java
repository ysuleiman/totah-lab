package totah.lab.web.service;

import totah.lab.gaia.geometry.Point3D;

/**
 * Web view of one pocket residue point: its identity, a display label
 * of the form {@code A:CYS202} (insertion code appended when present,
 * for example {@code B:LEU145A}), its chemistry class and its
 * position.
 */
public record ResiduePointView(
        String chainId,
        int residueNumber,
        String insertionCode,
        String residueName,
        String label,
        String chemistry,
        Point3D position
) {
}
