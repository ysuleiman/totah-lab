package totah.lab.topology;

import totah.lab.chemistry.ChemicalAtomFactory;
import totah.lab.protein.*;

import java.util.*;

/**
 * Post-hydrogenation optimizer for rotatable polar groups.
 *
 * <p>Evaluates alternative orientations (Asn/Gln flips, His tautomers,
 * hydroxyl/methyl rotations) using Coulomb scoring with AMBER RESP charges.
 * Keeps the orientation with the best electrostatic complementarity and
 * fewest steric clashes.
 */
public class HydrogenOptimizer {

    private static final double[] ROTAMER_ANGLES = {0, 60, 120, 180, 240, 300};

    private final HydrogenScorer scorer;
    private final boolean allowHeavyAtomFlips;

    public HydrogenOptimizer(ResidueTemplateProvider amberLib, double clashCutoff) {
        this(amberLib, null, clashCutoff);
    }

    public HydrogenOptimizer(ResidueTemplateProvider amberLib,
                             AmberParameterSet ljParams, double clashCutoff) {
        this(amberLib, ljParams, clashCutoff, true);
    }

    public HydrogenOptimizer(ResidueTemplateProvider amberLib,
                             AmberParameterSet ljParams,
                             double clashCutoff,
                             boolean allowHeavyAtomFlips) {
        this.scorer = new HydrogenScorer(Objects.requireNonNull(amberLib), ljParams, clashCutoff);
        this.allowHeavyAtomFlips = allowHeavyAtomFlips;
    }

    public List<Atom> optimize(Residue residue, List<Residue> allResidues) {
        return optimize(residue, allResidues, residue.getName());
    }

    public List<Atom> optimize(Residue residue, List<Residue> allResidues, String amberTemplateName) {
        String lookupName = baseTemplateName(amberTemplateName, residue.getName());
        return switch (lookupName) {
            case "ASN" -> optimizeAsn(residue, allResidues);
            case "GLN" -> optimizeGln(residue, allResidues);
            case "HIS" -> optimizeHisWithRingFlip(residue, allResidues, null);
            case "HID" -> optimizeHisWithRingFlip(residue, allResidues, HisState.HID);
            case "HIE" -> optimizeHisWithRingFlip(residue, allResidues, HisState.HIE);
            case "HIP" -> optimizeHisWithRingFlip(residue, allResidues, HisState.HIP);
            case "SER", "THR", "TYR" -> optimizeHydroxyl(residue, allResidues);
            case "CYS", "CYM", "CYX" -> optimizeCys(residue, allResidues);
            case "ASP", "ASH" -> optimizeAsp(residue, allResidues);
            case "GLU", "GLH" -> optimizeGlu(residue, allResidues);
            case "LYS", "LYN" -> optimizeLys(residue, allResidues);
            case "ARG" -> optimizeArg(residue, allResidues);
            case "TRP" -> optimizeTrp(residue, allResidues);
            case "ALA", "VAL", "LEU", "ILE", "MET", "PRO", "GLY", "PHE" ->
                    optimizeMethyl(residue, allResidues);
            default -> new ArrayList<>(residue.getAtoms());
        };
    }

    // ==================== ASN / GLN FLIP ====================

    private String baseTemplateName(String amberTemplateName, String defaultName) {
        String name = amberTemplateName == null || amberTemplateName.isBlank() ? defaultName : amberTemplateName;
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() == 4 && (normalized.charAt(0) == 'N' || normalized.charAt(0) == 'C')) {
            return normalized.substring(1);
        }
        return normalized;
    }

    private List<Atom> optimizeAsn(Residue r, List<Residue> env) {
        List<Atom> stateA = buildAsn(r, false);
        if (!allowHeavyAtomFlips) {
            return stateA;
        }
        List<Atom> stateB = buildAsn(r, true);
        return pickBest(stateA, stateB, r, env);
    }

    private List<Atom> optimizeGln(Residue r, List<Residue> env) {
        List<Atom> stateA = buildGln(r, false);
        if (!allowHeavyAtomFlips) {
            return stateA;
        }
        List<Atom> stateB = buildGln(r, true);
        return pickBest(stateA, stateB, r, env);
    }

    /**
     * Build ASN in unflipped or flipped orientation.
     * Flip = 180° rotation around the CB-CG bond, swapping OD1 and ND2.
     */
    private List<Atom> buildAsn(Residue r, boolean flipped) {
        Atom cb = r.getAtom("CB");
        Atom ca = r.getAtom("CA");
        Atom cg = r.getAtom("CG");
        Atom od1 = r.getAtom("OD1");
        Atom nd2 = r.getAtom("ND2");
        if (cb == null || ca == null || cg == null || od1 == null || nd2 == null) {
            return new ArrayList<>(r.getAtoms());
        }

        // Unflipped state: keep the input exactly as-is (including amide H's)
        if (!flipped) {
            return new ArrayList<>(r.getAtoms());
        }

        List<Atom> atoms = new ArrayList<>(r.getAtoms());
        // Strip only the amide H's being rebuilt — backbone H/HA/HB must survive
        atoms.removeIf(a -> a.getName().equals("HD21") || a.getName().equals("HD22"));

        replaceAtom(atoms, "OD1", atom -> atom.toBuilder().position(nd2.getPosition()).build());
        replaceAtom(atoms, "ND2", atom -> atom.toBuilder().position(od1.getPosition()).build());

        // Rebuild amide H's on whichever atom is ND2
        Atom donor = findByName(atoms, "ND2");
        Atom ref1 = findByName(atoms, "CG");
        Atom ref2 = findByName(atoms, "CB");
        if (donor != null && ref1 != null && ref2 != null) {
            addAmideNH2(donor, ref1, ref2, "HD2", atoms);
        }
        return atoms;
    }

    private List<Atom> buildGln(Residue r, boolean flipped) {
        Atom cb = r.getAtom("CB");
        Atom ca = r.getAtom("CA");
        Atom cg = r.getAtom("CG");
        Atom cd = r.getAtom("CD");
        Atom oe1 = r.getAtom("OE1");
        Atom ne2 = r.getAtom("NE2");
        if (cb == null || ca == null || cg == null || cd == null || oe1 == null || ne2 == null) {
            return new ArrayList<>(r.getAtoms());
        }

        // Unflipped state: keep the input exactly as-is (including amide H's)
        if (!flipped) {
            return new ArrayList<>(r.getAtoms());
        }

        List<Atom> atoms = new ArrayList<>(r.getAtoms());
        // Strip only the amide H's being rebuilt — backbone H/HA/HB must survive
        atoms.removeIf(a -> a.getName().equals("HE21") || a.getName().equals("HE22"));

        replaceAtom(atoms, "OE1", atom -> atom.toBuilder().position(ne2.getPosition()).build());
        replaceAtom(atoms, "NE2", atom -> atom.toBuilder().position(oe1.getPosition()).build());

        Atom donor = findByName(atoms, "NE2");
        Atom ref1 = findByName(atoms, "CD");
        Atom ref2 = findByName(atoms, "CG");
        if (donor != null && ref1 != null && ref2 != null) {
            addAmideNH2(donor, ref1, ref2, "HE2", atoms);
        }
        return atoms;
    }

    // ==================== HIS TAUTOMER + RING FLIP ====================

    private List<Atom> optimizeHisWithRingFlip(Residue r, List<Residue> env, HisState fixedState) {
        List<Atom> best = null;
        double bestScore = Double.POSITIVE_INFINITY;

        // The optimizer may migrate an existing proton between ND1/NE2, but it
        // must not change the net protonation state — that decision belongs to
        // the upstream pH/pKa assignment. So HIP trials are only allowed when
        // the input already carries both ring protons.
        int inputTautomerH = (r.getAtom("HD1") != null ? 1 : 0) + (r.getAtom("HE2") != null ? 1 : 0);

        boolean[] flipStates = allowHeavyAtomFlips ? new boolean[]{false, true} : new boolean[]{false};
        for (boolean flipped : flipStates) {
            for (HisState state : HisState.values()) {
                if (fixedState != null && state != fixedState) continue;
                int trialTautomerH = state == HisState.HIP ? 2 : 1;
                if (fixedState == null && trialTautomerH > inputTautomerH) continue;
                List<Atom> trial = buildHis(r, state, flipped);
                double s = scorer.score(trial, r, env);
                if (s < bestScore) {
                    bestScore = s;
                    best = trial;
                }
            }
        }
        return best != null ? best : new ArrayList<>(r.getAtoms());
    }

    private List<Atom> buildHis(Residue r, HisState state, boolean flipped) {
        Atom cb = r.getAtom("CB");
        Atom ca = r.getAtom("CA");
        Atom cg = r.getAtom("CG");
        Atom nd1 = r.getAtom("ND1");
        Atom cd2 = r.getAtom("CD2");
        Atom ce1 = r.getAtom("CE1");
        Atom ne2 = r.getAtom("NE2");
        if (cb == null || ca == null || cg == null) {
            return new ArrayList<>(r.getAtoms());
        }

        List<Atom> atoms = new ArrayList<>(r.getAtoms());
        // Strip only the H's rebuilt below (ring + methylene + tautomer H's) —
        // backbone H/HA and any other hydrogens must survive
        atoms.removeIf(a -> switch (a.getName()) {
            case "HB2", "HB3", "HD2", "HE1", "HD1", "HE2" -> true;
            default -> false;
        });

        // Apply ring flip: ND1 exchanges coordinates with CD2, CE1 with NE2
        if (flipped && nd1 != null && cd2 != null && ce1 != null && ne2 != null) {
            replaceAtom(atoms, "ND1", atom -> atom.toBuilder().position(cd2.getPosition()).build());
            replaceAtom(atoms, "CD2", atom -> atom.toBuilder().position(nd1.getPosition()).build());
            replaceAtom(atoms, "CE1", atom -> atom.toBuilder().position(ne2.getPosition()).build());
            replaceAtom(atoms, "NE2", atom -> atom.toBuilder().position(ce1.getPosition()).build());
        }

        Atom cgRef = findByName(atoms, "CG");
        Atom nd1Ref = findByName(atoms, "ND1");
        Atom cd2Ref = findByName(atoms, "CD2");
        Atom ce1Ref = findByName(atoms, "CE1");
        Atom ne2Ref = findByName(atoms, "NE2");

        if (cb != null && ca != null && cgRef != null) {
            addMethylene(cb, ca, cgRef, "HB", atoms);
        }
        if (cd2Ref != null && cgRef != null && ne2Ref != null) {
            atoms.add(aromaticH("HD2", cd2Ref, cgRef, ne2Ref));
        }
        if (ce1Ref != null && nd1Ref != null && ne2Ref != null) {
            atoms.add(aromaticH("HE1", ce1Ref, nd1Ref, ne2Ref));
        }

        switch (state) {
            case HIE -> {
                if (ne2Ref != null && ce1Ref != null && cd2Ref != null) {
                    atoms.add(hisNitrogenH("HE2", ne2Ref, ce1Ref, cd2Ref));
                }
            }
            case HID -> {
                if (nd1Ref != null && cgRef != null && ce1Ref != null) {
                    atoms.add(hisNitrogenH("HD1", nd1Ref, cgRef, ce1Ref));
                }
            }
            case HIP -> {
                if (nd1Ref != null && cgRef != null && ce1Ref != null) {
                    atoms.add(hisNitrogenH("HD1", nd1Ref, cgRef, ce1Ref));
                }
                if (ne2Ref != null && ce1Ref != null && cd2Ref != null) {
                    atoms.add(hisNitrogenH("HE2", ne2Ref, ce1Ref, cd2Ref));
                }
            }
        }
        return atoms;
    }

    // ==================== HYDROXYL ROTATION ====================

    private List<Atom> optimizeHydroxyl(Residue r, List<Residue> env) {
        String resName = r.getName();
        Atom oAtom = switch (resName) {
            case "SER" -> r.getAtom("OG");
            case "THR" -> r.getAtom("OG1");
            case "TYR" -> r.getAtom("OH");
            default -> null;
        };
        Atom cAtom = switch (resName) {
            case "SER" -> r.getAtom("CB");
            case "THR" -> r.getAtom("CB");
            case "TYR" -> r.getAtom("CZ");
            default -> null;
        };
        Atom refAtom = switch (resName) {
            case "SER" -> r.getAtom("CA");
            case "THR" -> r.getAtom("CA");
            case "TYR" -> r.getAtom("CE1");
            default -> null;
        };

        if (oAtom == null || cAtom == null || refAtom == null) {
            return new ArrayList<>(r.getAtoms());
        }

        String hName = switch (resName) {
            case "SER" -> "HG";
            case "THR" -> "HG1";
            case "TYR" -> "HH";
            default -> "H";
        };

        // Metal guard: same rule as the first pass — no hydroxyl H near metals
        if (scorer.isNearMetal(oAtom.getPosition(), env)) {
            List<Atom> atoms = new ArrayList<>(r.getAtoms());
            atoms.removeIf(a -> hName.equals(a.getName()));
            return atoms;
        }

        // Deprotonated input (e.g., high pH): never add a proton that was absent
        if (r.getAtom(hName) == null) {
            return new ArrayList<>(r.getAtoms());
        }

        List<Atom> best = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (double angle : ROTAMER_ANGLES) {
            List<Atom> trial = new ArrayList<>(r.getAtoms());
            trial.removeIf(a -> hName.equals(a.getName()));

            Point3D hPos = ZMatrixMath.calculatePosition(
                    oAtom.getPosition(), cAtom.getPosition(), refAtom.getPosition(),
                    0.96, Math.toRadians(108.5), Math.toRadians(angle)
            );
            trial.add(hAtom(hName, hPos, oAtom.getBFactor()));

            double score = scorer.score(trial, r, env);
            if (score < bestScore) {
                bestScore = score;
                best = trial;
            }
        }
        return best != null ? best : new ArrayList<>(r.getAtoms());
    }

    // ==================== CYS (THIOL + DISULFIDE) ====================

    private List<Atom> optimizeCys(Residue r, List<Residue> env) {
        Atom sg = r.getAtom("SG");
        if (sg == null) return new ArrayList<>(r.getAtoms());

        // Disulfide check: is there another CYS SG within 2.2 Å?
        boolean inDisulfide = false;
        for (Residue er : env) {
            if (shareSameResidue(r, er)) continue;
            if (!"CYS".equals(er.getName())) continue;
            Atom otherSg = er.getAtom("SG");
            if (otherSg != null && distance(sg.getPosition(), otherSg.getPosition()) <= 2.2) {
                inDisulfide = true;
                break;
            }
        }

        // Metal guard: SG within 4 Å of any metal?
        boolean nearMetal = scorer.isNearMetal(sg.getPosition(), env);

        if (inDisulfide || nearMetal) {
            // Remove HG if present
            List<Atom> atoms = new ArrayList<>();
            for (Atom a : r.getAtoms()) {
                if (!"HG".equals(a.getName())) atoms.add(a);
            }
            return atoms;
        }

        // Free thiol: rotate HG — but never add a proton that was absent
        // in the input (deprotonated at high pH)
        if (r.getAtom("HG") == null) return new ArrayList<>(r.getAtoms());

        Atom cb = r.getAtom("CB");
        Atom ca = r.getAtom("CA");
        if (cb == null || ca == null) return new ArrayList<>(r.getAtoms());

        List<Atom> best = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (double angle : ROTAMER_ANGLES) {
            List<Atom> trial = new ArrayList<>();
            for (Atom a : r.getAtoms()) {
                if (!"HG".equals(a.getName())) trial.add(a);
            }
            Point3D hPos = ZMatrixMath.calculatePosition(
                    sg.getPosition(), cb.getPosition(), ca.getPosition(),
                    1.34, Math.toRadians(109.5), Math.toRadians(angle)
            );
            trial.add(hAtom("HG", hPos, sg.getBFactor()));

            double s = scorer.score(trial, r, env);
            if (s < bestScore) {
                bestScore = s;
                best = trial;
            }
        }
        return best != null ? best : new ArrayList<>(r.getAtoms());
    }

    // ==================== ASP / GLU (PROTONATED) ====================

    private List<Atom> optimizeAsp(Residue r, List<Residue> env) {
        return optimizeAcidic(r, env, "OD2", "CG", "CB", "HD2", 0.96, 120.0);
    }

    private List<Atom> optimizeGlu(Residue r, List<Residue> env) {
        return optimizeAcidic(r, env, "OE2", "CD", "CG", "HE2", 0.96, 120.0);
    }

    private List<Atom> optimizeAcidic(Residue r, List<Residue> env,
                                      String oName, String c1Name, String c2Name,
                                      String hName, double bondLen, double bondAngle) {
        Atom o = r.getAtom(oName);
        Atom c1 = r.getAtom(c1Name);
        Atom c2 = r.getAtom(c2Name);
        if (o == null || c1 == null || c2 == null) {
            return new ArrayList<>(r.getAtoms());
        }

        // Metal guard
        if (scorer.isNearMetal(o.getPosition(), env)) {
            List<Atom> atoms = new ArrayList<>();
            for (Atom a : r.getAtoms()) {
                if (!hName.equals(a.getName())) atoms.add(a);
            }
            return atoms;
        }

        // Deprotonated input (e.g., high pH): never add a proton that was absent
        if (r.getAtom(hName) == null) {
            return new ArrayList<>(r.getAtoms());
        }

        List<Atom> best = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (double angle : ROTAMER_ANGLES) {
            List<Atom> trial = new ArrayList<>();
            for (Atom a : r.getAtoms()) {
                if (!hName.equals(a.getName())) trial.add(a);
            }
            Point3D hPos = ZMatrixMath.calculatePosition(
                    o.getPosition(), c1.getPosition(), c2.getPosition(),
                    bondLen, Math.toRadians(bondAngle), Math.toRadians(angle)
            );
            trial.add(hAtom(hName, hPos, o.getBFactor()));

            double s = scorer.score(trial, r, env);
            if (s < bestScore) {
                bestScore = s;
                best = trial;
            }
        }
        return best != null ? best : new ArrayList<>(r.getAtoms());
    }

    // ==================== LYS / ARG / TRP ====================

    private List<Atom> optimizeLys(Residue r, List<Residue> env) {
        Atom nz = r.getAtom("NZ");
        Atom ce = r.getAtom("CE");
        Atom cd = r.getAtom("CD");
        if (nz == null || ce == null || cd == null) {
            return new ArrayList<>(r.getAtoms());
        }

        // Only protonated ammonium (HZ1/HZ2/HZ3) is rotatable here; a neutral
        // NH2 (high pH) must pass through without gaining a proton
        if (r.getAtom("HZ1") == null || r.getAtom("HZ2") == null || r.getAtom("HZ3") == null) {
            return new ArrayList<>(r.getAtoms());
        }

        List<Atom> best = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (double angle : ROTAMER_ANGLES) {
            List<Atom> trial = new ArrayList<>();
            for (Atom a : r.getAtoms()) {
                if (!a.getName().startsWith("HZ")) trial.add(a);
            }
            double[] dihedrals = {angle, angle + 120, angle + 240};
            String[] names = {"HZ1", "HZ2", "HZ3"};
            for (int i = 0; i < 3; i++) {
                Point3D hPos = ZMatrixMath.calculatePosition(
                        nz.getPosition(), ce.getPosition(), cd.getPosition(),
                        1.01, Math.toRadians(109.5), Math.toRadians(dihedrals[i])
                );
                trial.add(hAtom(names[i], hPos, nz.getBFactor()));
            }

            double s = scorer.score(trial, r, env);
            if (s < bestScore) {
                bestScore = s;
                best = trial;
            }
        }
        return best != null ? best : new ArrayList<>(r.getAtoms());
    }

    private List<Atom> optimizeArg(Residue r, List<Residue> env) {
        // ARG guanidinium is planar; only minor HE rotation matters.
        // For now, pass-through to keep scoring fast.
        return new ArrayList<>(r.getAtoms());
    }

    private List<Atom> optimizeTrp(Residue r, List<Residue> env) {
        // TRP indole HE1 is fixed by ring geometry; pass-through.
        return new ArrayList<>(r.getAtoms());
    }

    // ==================== METHYL PASS-THROUGH ====================

    private List<Atom> optimizeMethyl(Residue r, List<Residue> env) {
        return new ArrayList<>(r.getAtoms());
    }

    private boolean shareSameResidue(Residue scored, Residue er) {
        return scored == er;
    }

    // ==================== GEOMETRY HELPERS ====================

    private void addAmideNH2(Atom center, Atom a1, Atom a2, String prefix, List<Atom> atoms) {
        double[] dihedrals = {0, 180};
        String[] names = {prefix + "1", prefix + "2"};
        for (int i = 0; i < 2; i++) {
            Point3D h = ZMatrixMath.calculatePosition(
                    center.getPosition(), a1.getPosition(), a2.getPosition(),
                    1.00, Math.toRadians(120.0), Math.toRadians(dihedrals[i])
            );
            atoms.add(hAtom(names[i], h, center.getBFactor()));
        }
    }

    private void addMethylene(Atom center, Atom a1, Atom a2, String prefix, List<Atom> atoms) {
        double[] dihedrals = {120, -120};
        String[] names = {prefix + "2", prefix + "3"};
        for (int i = 0; i < 2; i++) {
            Point3D h = ZMatrixMath.calculatePosition(
                    center.getPosition(), a1.getPosition(), a2.getPosition(),
                    1.09, Math.toRadians(109.5), Math.toRadians(dihedrals[i])
            );
            atoms.add(hAtom(names[i], h, center.getBFactor()));
        }
    }

    private Atom aromaticH(String name, Atom carbon, Atom n1, Atom n2) {
        Point3D c = carbon.getPosition();
        Point3D v1 = normalize(sub(n1.getPosition(), c));
        Point3D v2 = normalize(sub(n2.getPosition(), c));
        Point3D bisector = normalize(new Point3D(v1.x() + v2.x(), v1.y() + v2.y(), v1.z() + v2.z()));
        Point3D hPos = new Point3D(
                c.x() - bisector.x() * 1.08,
                c.y() - bisector.y() * 1.08,
                c.z() - bisector.z() * 1.08
        );
        return hAtom(name, hPos, carbon.getBFactor());
    }

    private Atom hisNitrogenH(String name, Atom n, Atom a1, Atom a2) {
        Point3D h = ZMatrixMath.calculatePosition(
                n.getPosition(), a1.getPosition(), a2.getPosition(),
                1.00, Math.toRadians(125.0), Math.toRadians(180.0)
        );
        return hAtom(name, h, n.getBFactor());
    }

    private Atom hAtom(String name, Point3D pos, double bFactor) {
        return ChemicalAtomFactory.hydrogen(name, pos, bFactor);
    }

    private Atom findByName(List<Atom> atoms, String name) {
        for (Atom a : atoms) {
            if (a.getName().equals(name)) return a;
        }
        return null;
    }

    private void replaceAtom(List<Atom> atoms, String name, java.util.function.Function<Atom, Atom> replacement) {
        for (int i = 0; i < atoms.size(); i++) {
            Atom atom = atoms.get(i);
            if (atom.getName().equals(name)) {
                atoms.set(i, replacement.apply(atom));
                return;
            }
        }
    }

    private List<Atom> pickBest(List<Atom> a, List<Atom> b, Residue scored, List<Residue> env) {
        double scoreA = scorer.score(a, scored, env);
        double scoreB = scorer.score(b, scored, env);
        return scoreB < scoreA ? b : a;
    }

    // ==================== MATH HELPERS ====================

    private Point3D sub(Point3D a, Point3D b) {
        return new Point3D(a.x() - b.x(), a.y() - b.y(), a.z() - b.z());
    }

    private Point3D normalize(Point3D v) {
        double len = Math.sqrt(v.x()*v.x() + v.y()*v.y() + v.z()*v.z());
        if (len < 1e-12) return new Point3D(0, 0, 0);
        return new Point3D(v.x()/len, v.y()/len, v.z()/len);
    }

    private double distance(Point3D a, Point3D b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

    private enum HisState { HIE, HID, HIP }
}
