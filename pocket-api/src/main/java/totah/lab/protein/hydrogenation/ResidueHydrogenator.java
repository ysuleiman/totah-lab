package totah.lab.protein.hydrogenation;

import totah.lab.protein.Atom;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;
import totah.lab.topology.ZMatrixMath;

import java.util.List;

import static totah.lab.protein.hydrogenation.HydrogenationGeometry.*;
import static totah.lab.protein.hydrogenation.ProtonationConfig.*;

/**
 * Backbone and side-chain hydrogenation for a single residue.
 * Stateless — all per-run state lives in {@link HydrogenationContext}.
 */
public final class ResidueHydrogenator {

    private ResidueHydrogenator() {}

    // ==================== BACKBONE ====================

    public static void hydrogenateBackbone(Residue res, int index,
                                           List<Atom> atoms, HydrogenationContext ctx) {
        BackboneHydrogenator.hydrogenate(res, index, atoms, ctx);
    }

    public static boolean isConsecutive(Residue prev, Residue curr) {
        return BackboneHydrogenator.isConsecutive(prev, curr);
    }

    // ==================== SIDE CHAIN ====================

    public static void hydrogenateSideChain(Residue res, List<Atom> atoms, HydrogenationContext ctx) {
        String templateName = ctx.baseTemplateName(res);
        String name = templateName != null ? templateName : res.getName();
        if ("MSE".equals(name)) name = "MET";
        double ph = ctx.config().ph();
        HisState his = ctx.config().hisState();

        switch (name) {
            case "ALA" -> addAla(res, atoms, ctx);
            case "VAL" -> addVal(res, atoms, ctx);
            case "LEU" -> addLeu(res, atoms, ctx);
            case "ILE" -> addIle(res, atoms, ctx);
            case "PRO" -> addPro(res, atoms, ctx);
            case "GLY" -> addGly(res, atoms, ctx);
            case "SER" -> addSer(res, atoms, ctx);
            case "THR" -> addThr(res, atoms, ctx);
            case "CYS", "CYM", "CYX" -> addCys(res, atoms, ctx, name);
            case "MET" -> addMet(res, atoms, ctx);
            case "PHE" -> addPhe(res, atoms, ctx);
            case "TYR" -> addTyr(res, atoms, ctx, ph);
            case "TRP" -> addTrp(res, atoms, ctx);
            case "HIS", "HID", "HIE", "HIP" -> addHis(res, atoms, ctx, his, name);
            case "LYS", "LYN" -> addLys(res, atoms, ctx, ph, name);
            case "ARG" -> addArg(res, atoms, ctx);
            case "ASP", "ASH" -> addAsp(res, atoms, ctx, ph, name);
            case "GLU", "GLH" -> addGlu(res, atoms, ctx, ph, name);
            case "ASN" -> addAsn(res, atoms, ctx);
            case "GLN" -> addGln(res, atoms, ctx);
            default -> System.err.println("[ReceptorHydrogenation] Warning: Unknown residue type '" +
                    res.getName() + "' - skipping side-chain hydrogenation");
        }
    }

    // -------------------- aliphatic --------------------

    private static void addAla(Residue r, List<Atom> atoms, HydrogenationContext ctx) {
        Atom cb = r.getAtom("CB"), ca = r.getAtom("CA"), n = r.getAtom("N");
        if (cb != null && ca != null && n != null) addMethyl(cb, ca, n, "HB", atoms, ctx.checker(), ctx.config().clashCutoff());
    }

    private static void addVal(Residue r, List<Atom> atoms, HydrogenationContext ctx) {
        Atom cb = r.getAtom("CB"), ca = r.getAtom("CA"), cg1 = r.getAtom("CG1"), cg2 = r.getAtom("CG2");
        if (cb != null && ca != null) {
            if (cg1 != null && cg2 != null) {
                Point3D hb = tetrahedralFourthPosition(cb, ca, cg1, cg2, BOND_C_H_SP3);
                ctx.tryAdd(atoms, hAtom("HB", hb, cb.getBFactor()), cb);
            }
            if (cg1 != null) addMethyl(cg1, cb, ca, "HG1", atoms, ctx.checker(), ctx.config().clashCutoff());
            if (cg2 != null) addMethyl(cg2, cb, ca, "HG2", atoms, ctx.checker(), ctx.config().clashCutoff());
        }
    }

    private static void addLeu(Residue r, List<Atom> atoms, HydrogenationContext ctx) {
        Atom cb = r.getAtom("CB"), ca = r.getAtom("CA"), cg = r.getAtom("CG");
        Atom cd1 = r.getAtom("CD1"), cd2 = r.getAtom("CD2");
        if (cb != null && ca != null && cg != null) {
            addMethylene(cb, ca, cg, "HB", atoms, ctx.checker(), ctx.config().clashCutoff());
            if (cd1 != null && cd2 != null) {
                Point3D hg = tetrahedralFourthPosition(cg, cb, cd1, cd2, BOND_C_H_SP3);
                ctx.tryAdd(atoms, hAtom("HG", hg, cg.getBFactor()), cg);
            }
        }
        if (cd1 != null) addMethyl(cd1, cg, cb, "HD1", atoms, ctx.checker(), ctx.config().clashCutoff());
        if (cd2 != null) addMethyl(cd2, cg, cb, "HD2", atoms, ctx.checker(), ctx.config().clashCutoff());
    }

    private static void addIle(Residue r, List<Atom> atoms, HydrogenationContext ctx) {
        Atom cb = r.getAtom("CB"), ca = r.getAtom("CA"), cg1 = r.getAtom("CG1"), cg2 = r.getAtom("CG2"), cd1 = r.getAtom("CD1");
        if (cb != null && ca != null) {
            if (cg1 != null) {
                Point3D hb = ZMatrixMath.calculatePosition(cb.getPosition(), ca.getPosition(), cg1.getPosition(),
                        BOND_C_H_SP3, Math.toRadians(ANGLE_SP3), Math.toRadians(120.0));
                ctx.tryAdd(atoms, hAtom("HB", hb, cb.getBFactor()), cb);
            }
            if (cg2 != null) addMethyl(cg2, cb, ca, "HG2", atoms, ctx.checker(), ctx.config().clashCutoff());
            if (cg1 != null && cd1 != null) {
                addMethylene(cg1, cb, cd1, "HG1", atoms, ctx.checker(), ctx.config().clashCutoff());
                addMethyl(cd1, cg1, cb, "HD1", atoms, ctx.checker(), ctx.config().clashCutoff());
            }
        }
    }

    private static void addPro(Residue r, List<Atom> atoms, HydrogenationContext ctx) {
        Atom cb = r.getAtom("CB"), cg = r.getAtom("CG"), cd = r.getAtom("CD"), ca = r.getAtom("CA"), n = r.getAtom("N");
        if (cb != null && ca != null && cg != null)
            addMethylene(cb, ca, cg, "HB", atoms, ctx.checker(), ctx.config().clashCutoff());
        if (cg != null && cb != null && cd != null)
            addMethylene(cg, cb, cd, "HG", atoms, ctx.checker(), ctx.config().clashCutoff());
        if (cd != null && cg != null && n != null)
            addMethylene(cd, cg, n, "HD", atoms, ctx.checker(), ctx.config().clashCutoff());
    }

    private static void addGly(Residue r, List<Atom> atoms, HydrogenationContext ctx) {
        Atom ca = r.getAtom("CA"), n = r.getAtom("N"), c = r.getAtom("C");
        if (ca != null && n != null && c != null) {
            Point3D ha1 = ZMatrixMath.calculatePosition(ca.getPosition(), n.getPosition(), c.getPosition(),
                    BOND_C_H_SP3, Math.toRadians(ANGLE_SP3), Math.toRadians(120.0));
            Point3D ha2 = ZMatrixMath.calculatePosition(ca.getPosition(), n.getPosition(), c.getPosition(),
                    BOND_C_H_SP3, Math.toRadians(ANGLE_SP3), Math.toRadians(-120.0));
            ctx.tryAdd(atoms, hAtom("HA2", ha1, ca.getBFactor()), ca);
            ctx.tryAdd(atoms, hAtom("HA3", ha2, ca.getBFactor()), ca);
        }
    }

    // -------------------- hydroxyl / sulfur --------------------

    private static void addSer(Residue r, List<Atom> atoms, HydrogenationContext ctx) {
        Atom cb = r.getAtom("CB"), ca = r.getAtom("CA"), og = r.getAtom("OG");
        if (cb != null && ca != null && og != null) {
            addMethylene(cb, ca, og, "HB", atoms, ctx.checker(), ctx.config().clashCutoff());
            if (!ctx.isNearMetal(og.getPosition())) {
                Point3D hg = ZMatrixMath.calculatePosition(og.getPosition(), cb.getPosition(), ca.getPosition(),
                        BOND_O_H, Math.toRadians(ANGLE_O_H), Math.toRadians(180.0));
                ctx.tryAdd(atoms, hAtom("HG", hg, og.getBFactor()), og);
            }
        }
    }

    private static void addThr(Residue r, List<Atom> atoms, HydrogenationContext ctx) {
        Atom cb = r.getAtom("CB"), ca = r.getAtom("CA"), og1 = r.getAtom("OG1"), cg2 = r.getAtom("CG2");
        if (cb != null && ca != null) {
            if (og1 != null) {
                Point3D hb = ZMatrixMath.calculatePosition(cb.getPosition(), ca.getPosition(), og1.getPosition(),
                        BOND_C_H_SP3, Math.toRadians(ANGLE_SP3), Math.toRadians(120.0));
                ctx.tryAdd(atoms, hAtom("HB", hb, cb.getBFactor()), cb);
                if (!ctx.isNearMetal(og1.getPosition())) {
                    Point3D hg1 = ZMatrixMath.calculatePosition(og1.getPosition(), cb.getPosition(), ca.getPosition(),
                            BOND_O_H, Math.toRadians(ANGLE_O_H), Math.toRadians(180.0));
                    ctx.tryAdd(atoms, hAtom("HG1", hg1, og1.getBFactor()), og1);
                }
            }
            if (cg2 != null) addMethyl(cg2, cb, ca, "HG2", atoms, ctx.checker(), ctx.config().clashCutoff());
        }
    }

    private static void addCys(Residue r, List<Atom> atoms, HydrogenationContext ctx, String templateName) {
        Atom cb = r.getAtom("CB"), ca = r.getAtom("CA"), sg = r.getAtom("SG");
        if (cb != null && ca != null && sg != null) {
            addMethylene(cb, ca, sg, "HB", atoms, ctx.checker(), ctx.config().clashCutoff());
            boolean inSS = ctx.isDisulfideCys(r) || "CYX".equals(templateName);
            boolean deprot = "CYM".equals(templateName) || ctx.config().ph() > (PKA_CYS + 1.0);
            if (!inSS && !deprot && !ctx.isNearMetal(sg.getPosition())) {
                Point3D hg = ZMatrixMath.calculatePosition(sg.getPosition(), cb.getPosition(), ca.getPosition(),
                        BOND_S_H, Math.toRadians(ANGLE_SP3), Math.toRadians(180.0));
                ctx.tryAdd(atoms, hAtom("HG", hg, sg.getBFactor()), sg);
            }
        }
    }

    private static void addMet(Residue r, List<Atom> atoms, HydrogenationContext ctx) {
        Atom cb = r.getAtom("CB"), ca = r.getAtom("CA"), cg = r.getAtom("CG"), sd = r.getAtom("SD"), ce = r.getAtom("CE");
        if (cb != null && ca != null && cg != null && sd != null) {
            addMethylene(cb, ca, cg, "HB", atoms, ctx.checker(), ctx.config().clashCutoff());
            addMethylene(cg, cb, sd, "HG", atoms, ctx.checker(), ctx.config().clashCutoff());
        }
        if (ce != null && sd != null && cg != null)
            addMethyl(ce, sd, cg, "HE", atoms, ctx.checker(), ctx.config().clashCutoff());
    }

    // -------------------- aromatic --------------------

    private static void addPhe(Residue r, List<Atom> atoms, HydrogenationContext ctx) {
        addAromaticRing(r, atoms, ctx.checker(), ctx.config().clashCutoff());
    }

    private static void addTyr(Residue r, List<Atom> atoms, HydrogenationContext ctx, double ph) {
        addTyrRing(r, atoms, ctx);
        Atom oh = r.getAtom("OH"), cz = r.getAtom("CZ"), ce1 = r.getAtom("CE1");
        if (oh != null && cz != null && ce1 != null && ph < (PKA_TYR - 1.0) && !ctx.isNearMetal(oh.getPosition())) {
            Point3D hh = ZMatrixMath.calculatePosition(oh.getPosition(), cz.getPosition(), ce1.getPosition(),
                    BOND_O_H, Math.toRadians(ANGLE_O_H), Math.toRadians(180.0));
            ctx.tryAdd(atoms, hAtom("HH", hh, oh.getBFactor()), oh);
        }
    }

    private static void addTyrRing(Residue r, List<Atom> atoms, HydrogenationContext ctx) {
        Atom cb = r.getAtom("CB");
        Atom ca = r.getAtom("CA");
        Atom cg = r.getAtom("CG");
        if (cb != null && ca != null && cg != null) {
            addMethylene(cb, ca, cg, "HB", atoms, ctx.checker(), ctx.config().clashCutoff());
        }

        addAromaticHydrogen(r, atoms, ctx, "CD1", "CG", "CE1", "HD1");
        addAromaticHydrogen(r, atoms, ctx, "CD2", "CG", "CE2", "HD2");
        addAromaticHydrogen(r, atoms, ctx, "CE1", "CD1", "CZ", "HE1");
        addAromaticHydrogen(r, atoms, ctx, "CE2", "CD2", "CZ", "HE2");
    }

    private static void addAromaticHydrogen(Residue r, List<Atom> atoms, HydrogenationContext ctx,
                                            String carbonName, String neighbor1Name,
                                            String neighbor2Name, String hydrogenName) {
        Atom carbon = r.getAtom(carbonName);
        Atom neighbor1 = r.getAtom(neighbor1Name);
        Atom neighbor2 = r.getAtom(neighbor2Name);
        if (carbon != null && neighbor1 != null && neighbor2 != null) {
            ctx.tryAdd(atoms, aromaticH(hydrogenName, carbon, neighbor1, neighbor2), carbon);
        }
    }

    private static void addTrp(Residue r, List<Atom> atoms, HydrogenationContext ctx) {
        Atom cb = r.getAtom("CB"), ca = r.getAtom("CA"), cg = r.getAtom("CG"), cd1 = r.getAtom("CD1");
        Atom cd2 = r.getAtom("CD2"), ne1 = r.getAtom("NE1"), ce2 = r.getAtom("CE2");
        Atom ce3 = r.getAtom("CE3"), cz2 = r.getAtom("CZ2"), cz3 = r.getAtom("CZ3"), ch2 = r.getAtom("CH2");

        if (cb != null && ca != null && cg != null)
            addMethylene(cb, ca, cg, "HB", atoms, ctx.checker(), ctx.config().clashCutoff());

        if (ne1 != null && cd1 != null && ce2 != null) {
            Point3D he1 = ZMatrixMath.calculatePosition(ne1.getPosition(), cd1.getPosition(), ce2.getPosition(),
                    BOND_N_H_SP2, Math.toRadians(125.0), Math.toRadians(180.0));
            ctx.tryAdd(atoms, hAtom("HE1", he1, ne1.getBFactor()), ne1);
        }
        if (cd1 != null && cg != null && ne1 != null) ctx.tryAdd(atoms, aromaticH5Ring("HD1", cd1, cg, ne1), cd1);
        if (ce3 != null && cd2 != null && cz3 != null) ctx.tryAdd(atoms, aromaticH("HE3", ce3, cd2, cz3), ce3);
        if (cz2 != null && ce2 != null && ch2 != null) ctx.tryAdd(atoms, aromaticH("HZ2", cz2, ce2, ch2), cz2);
        if (cz3 != null && ce3 != null && ch2 != null) ctx.tryAdd(atoms, aromaticH("HZ3", cz3, ce3, ch2), cz3);
        if (ch2 != null && cz2 != null && cz3 != null) ctx.tryAdd(atoms, aromaticH("HH2", ch2, cz2, cz3), ch2);
    }

    // -------------------- charged / ionizable --------------------

    private static void addHis(Residue r, List<Atom> atoms, HydrogenationContext ctx, HisState state,
                               String templateName) {
        Atom cb = r.getAtom("CB"), ca = r.getAtom("CA"), cg = r.getAtom("CG");
        Atom nd1 = r.getAtom("ND1"), cd2 = r.getAtom("CD2"), ce1 = r.getAtom("CE1"), ne2 = r.getAtom("NE2");

        if (cb != null && ca != null && cg != null)
            addMethylene(cb, ca, cg, "HB", atoms, ctx.checker(), ctx.config().clashCutoff());

        if (cd2 != null && cg != null && ne2 != null) ctx.tryAdd(atoms, aromaticH("HD2", cd2, cg, ne2), cd2);
        if (ce1 != null && nd1 != null && ne2 != null) ctx.tryAdd(atoms, aromaticH("HE1", ce1, nd1, ne2), ce1);

        HisState resolvedState = switch (templateName) {
            case "HID" -> HisState.HID;
            case "HIE", "HIS" -> HisState.HIE;
            case "HIP" -> HisState.HIP;
            default -> state;
        };

        switch (resolvedState) {
            case HIE -> {
                if (ne2 != null && ce1 != null && cd2 != null && !ctx.isNearMetal(ne2.getPosition())) {
                    Point3D he2 = ZMatrixMath.calculatePosition(ne2.getPosition(), ce1.getPosition(), cd2.getPosition(),
                            BOND_N_H_SP2, Math.toRadians(125.0), Math.toRadians(180.0));
                    ctx.tryAdd(atoms, hAtom("HE2", he2, ne2.getBFactor()), ne2);
                }
            }
            case HID -> {
                if (nd1 != null && cg != null && ce1 != null && !ctx.isNearMetal(nd1.getPosition())) {
                    Point3D hd1 = ZMatrixMath.calculatePosition(nd1.getPosition(), cg.getPosition(), ce1.getPosition(),
                            BOND_N_H_SP2, Math.toRadians(125.0), Math.toRadians(180.0));
                    ctx.tryAdd(atoms, hAtom("HD1", hd1, nd1.getBFactor()), nd1);
                }
            }
            case HIP -> {
                if (nd1 != null && cg != null && ce1 != null && !ctx.isNearMetal(nd1.getPosition())) {
                    Point3D hd1 = ZMatrixMath.calculatePosition(nd1.getPosition(), cg.getPosition(), ce1.getPosition(),
                            BOND_N_H_SP2, Math.toRadians(125.0), Math.toRadians(180.0));
                    ctx.tryAdd(atoms, hAtom("HD1", hd1, nd1.getBFactor()), nd1);
                }
                if (ne2 != null && ce1 != null && cd2 != null && !ctx.isNearMetal(ne2.getPosition())) {
                    Point3D he2 = ZMatrixMath.calculatePosition(ne2.getPosition(), ce1.getPosition(), cd2.getPosition(),
                            BOND_N_H_SP2, Math.toRadians(125.0), Math.toRadians(180.0));
                    ctx.tryAdd(atoms, hAtom("HE2", he2, ne2.getBFactor()), ne2);
                }
            }
            case AUTO -> {
                // Without a full H-bond network evaluator, default to HIE
                // (neutral, proton on NE2 — most common in buried protein contexts).
                // Log so the user knows AUTO was resolved at runtime.
                System.err.println("[ReceptorHydrogenation] Warning: HIS AUTO state resolved to HIE for "
                        + r.getChain() + ":" + r.getNumber()
                        + " (implement geometry-based auto-detection for production)");
                if (ne2 != null && ce1 != null && cd2 != null && !ctx.isNearMetal(ne2.getPosition())) {
                    Point3D he2 = ZMatrixMath.calculatePosition(ne2.getPosition(), ce1.getPosition(), cd2.getPosition(),
                            BOND_N_H_SP2, Math.toRadians(125.0), Math.toRadians(180.0));
                    ctx.tryAdd(atoms, hAtom("HE2", he2, ne2.getBFactor()), ne2);
                }
            }
        }
    }

    private static void addLys(Residue r, List<Atom> atoms, HydrogenationContext ctx, double ph, String templateName) {
        Atom cb = r.getAtom("CB"), ca = r.getAtom("CA"), cg = r.getAtom("CG");
        Atom cd = r.getAtom("CD"), ce = r.getAtom("CE"), nz = r.getAtom("NZ");

        if (cb != null && ca != null && cg != null) {
            addMethylene(cb, ca, cg, "HB", atoms, ctx.checker(), ctx.config().clashCutoff());
            addMethylene(cg, cb, cd, "HG", atoms, ctx.checker(), ctx.config().clashCutoff());
            addMethylene(cd, cg, ce, "HD", atoms, ctx.checker(), ctx.config().clashCutoff());
            addMethylene(ce, cd, nz, "HE", atoms, ctx.checker(), ctx.config().clashCutoff());
        }
        if (nz != null && ce != null && cd != null) {
            if ("LYN".equals(templateName)) {
                addPlanarNH2(nz, ce, cd, "HZ", atoms, ctx.checker(), ctx.config().clashCutoff());
            } else if (ph < (PKA_LYS - 1.0)) {
                addAmmonium(nz, ce, cd, "HZ", atoms, ctx.checker(), ctx.config().clashCutoff());
            } else if (ph > (PKA_LYS + 1.0)) {
                addPlanarNH2(nz, ce, cd, "HZ", atoms, ctx.checker(), ctx.config().clashCutoff());
            } else {
                addAmmonium(nz, ce, cd, "HZ", atoms, ctx.checker(), ctx.config().clashCutoff());
            }
        }
    }

    private static void addArg(Residue r, List<Atom> atoms, HydrogenationContext ctx) {
        Atom cb = r.getAtom("CB"), ca = r.getAtom("CA"), cg = r.getAtom("CG");
        Atom cd = r.getAtom("CD"), ne = r.getAtom("NE"), cz = r.getAtom("CZ");
        Atom nh1 = r.getAtom("NH1"), nh2 = r.getAtom("NH2");

        if (cb != null && ca != null && cg != null) {
            addMethylene(cb, ca, cg, "HB", atoms, ctx.checker(), ctx.config().clashCutoff());
            addMethylene(cg, cb, cd, "HG", atoms, ctx.checker(), ctx.config().clashCutoff());
            addMethylene(cd, cg, ne, "HD", atoms, ctx.checker(), ctx.config().clashCutoff());
        }
        if (ne != null && cd != null && cz != null) {
            Point3D he = ZMatrixMath.calculatePosition(ne.getPosition(), cd.getPosition(), cz.getPosition(),
                    BOND_N_H_SP2, Math.toRadians(ANGLE_SP2), Math.toRadians(180.0));
            ctx.tryAdd(atoms, hAtom("HE", he, ne.getBFactor()), ne);
        }
        if (nh1 != null && cz != null && ne != null)
            addPlanarNH2(nh1, cz, ne, "HH1", atoms, ctx.checker(), ctx.config().clashCutoff());
        if (nh2 != null && cz != null && ne != null)
            addPlanarNH2(nh2, cz, ne, "HH2", atoms, ctx.checker(), ctx.config().clashCutoff());
    }

    private static void addAsp(Residue r, List<Atom> atoms, HydrogenationContext ctx, double ph, String templateName) {
        Atom cb = r.getAtom("CB"), ca = r.getAtom("CA"), cg = r.getAtom("CG");
        if (cb != null && ca != null && cg != null)
            addMethylene(cb, ca, cg, "HB", atoms, ctx.checker(), ctx.config().clashCutoff());
        if ("ASH".equals(templateName) || ph < (PKA_ASP - 1.0)) {
            Atom od2 = r.getAtom("OD2");
            if (od2 != null && cg != null && cb != null && !ctx.isNearMetal(od2.getPosition())) {
                Point3D hd = ZMatrixMath.calculatePosition(od2.getPosition(), cg.getPosition(), cb.getPosition(),
                        BOND_O_H, Math.toRadians(ANGLE_SP2), Math.toRadians(0.0));
                ctx.tryAdd(atoms, hAtom("HD2", hd, od2.getBFactor()), od2);
            }
        }
    }

    private static void addGlu(Residue r, List<Atom> atoms, HydrogenationContext ctx, double ph, String templateName) {
        Atom cb = r.getAtom("CB"), ca = r.getAtom("CA"), cg = r.getAtom("CG"), cd = r.getAtom("CD");
        if (cb != null && ca != null && cg != null)
            addMethylene(cb, ca, cg, "HB", atoms, ctx.checker(), ctx.config().clashCutoff());
        if (cg != null && cb != null && cd != null)
            addMethylene(cg, cb, cd, "HG", atoms, ctx.checker(), ctx.config().clashCutoff());
        if ("GLH".equals(templateName) || ph < (PKA_GLU - 1.0)) {
            Atom oe2 = r.getAtom("OE2");
            if (oe2 != null && cd != null && cg != null && !ctx.isNearMetal(oe2.getPosition())) {
                Point3D he = ZMatrixMath.calculatePosition(oe2.getPosition(), cd.getPosition(), cg.getPosition(),
                        BOND_O_H, Math.toRadians(ANGLE_SP2), Math.toRadians(0.0));
                ctx.tryAdd(atoms, hAtom("HE2", he, oe2.getBFactor()), oe2);
            }
        }
    }

    private static void addAsn(Residue r, List<Atom> atoms, HydrogenationContext ctx) {
        Atom cb = r.getAtom("CB"), ca = r.getAtom("CA"), cg = r.getAtom("CG"), nd2 = r.getAtom("ND2");
        if (cb != null && ca != null && cg != null)
            addMethylene(cb, ca, cg, "HB", atoms, ctx.checker(), ctx.config().clashCutoff());
        if (nd2 != null && cg != null && cb != null)
            addAmideNH2(nd2, cg, cb, "HD2", atoms, ctx.checker(), ctx.config().clashCutoff());
    }

    private static void addGln(Residue r, List<Atom> atoms, HydrogenationContext ctx) {
        Atom cb = r.getAtom("CB"), ca = r.getAtom("CA"), cg = r.getAtom("CG");
        Atom cd = r.getAtom("CD"), ne2 = r.getAtom("NE2");
        if (cb != null && ca != null && cg != null)
            addMethylene(cb, ca, cg, "HB", atoms, ctx.checker(), ctx.config().clashCutoff());
        if (cg != null && cb != null && cd != null)
            addMethylene(cg, cb, cd, "HG", atoms, ctx.checker(), ctx.config().clashCutoff());
        if (ne2 != null && cd != null && cg != null)
            addAmideNH2(ne2, cd, cg, "HE2", atoms, ctx.checker(), ctx.config().clashCutoff());
    }
}
