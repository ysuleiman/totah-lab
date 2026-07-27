package totah.lab.protein.hydrogenation;

import totah.lab.protein.Atom;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;
import totah.lab.topology.ZMatrixMath;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static totah.lab.protein.hydrogenation.HydrogenationGeometry.*;

final class BackboneHydrogenator {

    // MSE is hydrogenated as MET elsewhere but is a modified residue:
    // it must not drive N-/C-terminus capping decisions (HETATM groups).
    private static final Set<String> STANDARD_AMINO_ACIDS = Set.of(
            "ALA", "ARG", "ASN", "ASP", "CYS", "GLN", "GLU", "GLY", "HIS", "ILE",
            "LEU", "LYS", "MET", "PHE", "PRO", "SER", "THR", "TRP", "TYR", "VAL");

    private BackboneHydrogenator() {}

    static void hydrogenate(Residue res, int index, List<Atom> atoms, HydrogenationContext ctx) {
        Atom n = res.getAtom("N");
        Atom ca = res.getAtom("CA");
        Atom c = res.getAtom("C");
        if (n == null || ca == null || c == null) return;

        List<Residue> all = ctx.allResidues();
        boolean isStandard = STANDARD_AMINO_ACIDS.contains(res.getName());

        Residue prev = index > 0 ? all.get(index - 1) : null;
        Residue next = index < all.size() - 1 ? all.get(index + 1) : null;

        boolean chainNTerminus = isStandard &&
                (prev == null || !Objects.equals(prev.getChain(), res.getChain()) || !isConsecutive(prev, res));
        boolean chainCTerminus = isStandard &&
                (next == null || !Objects.equals(next.getChain(), res.getChain()) || !isConsecutive(res, next));
        boolean isNTerminus = chainNTerminus && ctx.usesNTerminalTemplate(res);
        boolean isCTerminus = chainCTerminus && ctx.usesCTerminalTemplate(res);

        String nCap = ctx.config().nCap();
        String cCap = ctx.config().cCap();

        // N-terminus
        if (isNTerminus) {
            switch (nCap.toUpperCase()) {
                case "NH3+", "NH3", "NTERM" -> {
                    if ("PRO".equals(res.getName())) {
                        Atom cd = res.getAtom("CD");
                        if (cd != null) addSecondaryAmmonium(n, ca, cd, "H", atoms, ctx.checker(), ctx.config().clashCutoff());
                    } else {
                        Atom ref3 = res.getAtom("CB");
                        if (ref3 == null) ref3 = c;
                        addAmmonium(n, ca, ref3, "H", atoms, ctx.checker(), ctx.config().clashCutoff());
                    }
                }
                case "ACE" -> throw new UnsupportedOperationException(
                        "ACE N-terminal cap is not yet implemented. " +
                                "Use capNTerminus=NH3+ or NONE instead.");
                case "NONE" -> {
                    if (!"PRO".equals(res.getName())) {
                        Point3D h = ZMatrixMath.calculatePosition(
                                n.getPosition(), ca.getPosition(), c.getPosition(),
                                BOND_N_H_SP2, Math.toRadians(ANGLE_N_H_SP2), Math.toRadians(180.0));
                        ctx.tryAdd(atoms, hAtom("H", h, n.getBFactor()), n);
                    }
                }
                default -> {
                    System.err.println("[ReceptorHydrogenation] Unknown N-cap: " + nCap + ", using NH3+");
                    if ("PRO".equals(res.getName())) {
                        Atom cd = res.getAtom("CD");
                        if (cd != null) addSecondaryAmmonium(n, ca, cd, "H", atoms, ctx.checker(), ctx.config().clashCutoff());
                    } else {
                        Atom ref3 = res.getAtom("CB");
                        if (ref3 == null) ref3 = c;
                        addAmmonium(n, ca, ref3, "H", atoms, ctx.checker(), ctx.config().clashCutoff());
                    }
                }
            }
        } else {
            if (!"PRO".equals(res.getName())) {
                addStandardNH(res, index, all, atoms, ctx);
            }
        }

        // C-terminus
        if (isCTerminus) {
            switch (cCap.toUpperCase()) {
                case "COO-", "COO", "CTERM" -> {
                    Atom o = res.getAtom("O");
                    Atom existingOxt = res.getAtom("OXT");
                    if (o != null) {
                        if (existingOxt == null) {
                            Point3D oxt = ZMatrixMath.calculatePosition(
                                    c.getPosition(), ca.getPosition(), o.getPosition(),
                                    BOND_C_OXT, Math.toRadians(ANGLE_SP2), Math.toRadians(180.0));
                            ctx.tryAdd(atoms, oxtAtom("OXT", oxt, o.getBFactor()), c);
                        }
                    }
                }
                case "NME", "NONE" -> { /* no OXT */ }
                default -> {
                    System.err.println("[ReceptorHydrogenation] Unknown C-cap: " + cCap + ", using COO-");
                    Atom o = res.getAtom("O");
                    Atom existingOxt = res.getAtom("OXT");
                    if (o != null) {
                        if (existingOxt == null) {
                            Point3D oxt = ZMatrixMath.calculatePosition(
                                    c.getPosition(), ca.getPosition(), o.getPosition(),
                                    BOND_C_OXT, Math.toRadians(ANGLE_SP2), Math.toRadians(180.0));
                            ctx.tryAdd(atoms, oxtAtom("OXT", oxt, o.getBFactor()), c);
                        }
                    }
                }
            }
        }

        // CA-HA
        if (!"GLY".equals(res.getName())) {
            Atom cb = res.getAtom("CB");
            Point3D ha = cb != null
                    ? tetrahedralFourthPosition(ca, n, c, cb, BOND_C_H_SP3)
                    : null;
            if (ha == null) {
                ha = ZMatrixMath.calculatePosition(
                        ca.getPosition(), n.getPosition(), c.getPosition(),
                        BOND_C_H_SP3, Math.toRadians(ANGLE_SP3), Math.toRadians(180.0));
            }
            ctx.tryAdd(atoms, hAtom("HA", ha, ca.getBFactor()), ca);
        }
    }

    static boolean isConsecutive(Residue prev, Residue curr) {
        if (prev == null || curr == null) return false;
        if (!Objects.equals(prev.getChain(), curr.getChain())) return false;
        return curr.getNumber() == prev.getNumber() + 1;
    }

    private static void addStandardNH(Residue res, int index, List<Residue> all,
                                      List<Atom> atoms, HydrogenationContext ctx) {
        Atom n = res.getAtom("N");
        Atom ca = res.getAtom("CA");
        if (index > 0) {
            Residue prev = all.get(index - 1);
            Atom cPrev = prev.getAtom("C");
            if (cPrev != null && n != null && ca != null) {
                Point3D h = ZMatrixMath.calculatePosition(
                        n.getPosition(), ca.getPosition(), cPrev.getPosition(),
                        BOND_N_H_SP2, Math.toRadians(ANGLE_N_H_SP2), Math.toRadians(180.0));
                ctx.tryAdd(atoms, hAtom("H", h, n.getBFactor()), n);
            }
        }
    }
}
