package totah.lab.topology;

import totah.lab.protein.Atom;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

final class HydrogenScorer {

    private static final double COULOMB_FACTOR = 332.0;
    private static final double SCORE_CUTOFF = 10.0;
    private static final double CLASH_PENALTY = 50.0;

    // Metal elements (same set as ProtonationConfig)
    private static final Set<String> METAL_ELEMENTS = Set.of(
            "LI", "NA", "K", "RB", "CS", "BE", "MG", "CA", "SR", "BA",
            "SC", "TI", "V", "CR", "MN", "FE", "CO", "NI", "CU", "ZN",
            "Y", "ZR", "NB", "MO", "TC", "RU", "RH", "PD", "AG", "CD",
            "LU", "HF", "TA", "W", "RE", "OS", "IR", "PT", "AU", "HG",
            "AL", "GA", "IN", "SN", "TL", "PB", "BI");

    private final ResidueTemplateProvider amberLib;
    private final AmberParameterSet ljParams;
    private final double clashCutoff;

    HydrogenScorer(ResidueTemplateProvider amberLib, AmberParameterSet ljParams, double clashCutoff) {
        this.amberLib = Objects.requireNonNull(amberLib);
        this.ljParams = ljParams;
        this.clashCutoff = clashCutoff;
    }

    double score(List<Atom> trialAtoms, Residue scoredResidue, List<Residue> env) {
        double coulomb = 0.0;
        double lj = 0.0;
        double clashPenalty = 0.0;
        String scoredLookup = resolveLookupName(scoredResidue.getName(), trialAtoms);

        for (Atom a : trialAtoms) {
            double q1 = getCharge(a, scoredLookup);
            String type1 = getAmberType(a, scoredLookup);
            Point3D p1 = a.getPosition();

            for (Residue er : env) {
                if (shareSameResidue(scoredResidue, er)) continue;
                String envLookup = resolveLookupName(er);
                for (Atom b : er.getAtoms()) {
                    if (a == b) continue;
                    double dist = distance(p1, b.getPosition());
                    if (dist > SCORE_CUTOFF) continue;

                    double q2 = getCharge(b, envLookup);

                    if (dist < clashCutoff) {
                        // A short, attractive donor-H...acceptor contact is a
                        // (possibly forming) hydrogen bond, not a clash — score
                        // it by Coulomb. Everything else this close is a steric
                        // clash, penalized by overlap depth so clash magnitude
                        // discriminates states (a marginal 0.9 Å contact vs a
                        // 0.3 Å fusion). At such distances the pair's LJ term
                        // explodes non-physically, so the penalty replaces it.
                        boolean hbContact = q1 * q2 < -1e-12 &&
                                ((isDonorHydrogen(a, trialAtoms) && isAcceptor(b, er.getAtoms()))
                                        || (isDonorHydrogen(b, er.getAtoms()) && isAcceptor(a, trialAtoms)));
                        if (hbContact) {
                            coulomb += COULOMB_FACTOR * q1 * q2 / dist;
                        } else {
                            clashPenalty += CLASH_PENALTY * (clashCutoff - dist) / clashCutoff;
                        }
                        continue;
                    }

                    if (Math.abs(q1) > 1e-6) {
                        coulomb += COULOMB_FACTOR * q1 * q2 / dist;
                    }

                    if (ljParams != null && type1 != null) {
                        String type2 = getAmberType(b, envLookup);
                        if (type2 != null) {
                            // The repulsive LJ wall is already handled by the clash
                            // penalty above; keep only the dispersion branch so one
                            // short contact cannot drown out the electrostatics and
                            // H-bond terms that discriminate orientations.
                            lj += Math.min(0.0, ljParams.ljEnergy(type1, type2, dist));
                        }
                    }
                }
            }
        }

        double hbond = scoreHbondAngles(trialAtoms, scoredResidue, env);
        return coulomb + lj + clashPenalty + hbond;
    }

    boolean isNearMetal(Point3D pos, List<Residue> env) {
        for (Residue er : env) {
            if (shareSameResidue(null, er)) continue; // env is full list, check all
            for (Atom a : er.getAtoms()) {
                if (METAL_ELEMENTS.contains(
                        a.getElement().getSymbol().toUpperCase(Locale.ROOT))) {
                    if (distance(pos, a.getPosition()) <= 4.0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private double scoreHbondAngles(List<Atom> trialAtoms, Residue scoredResidue, List<Residue> env) {
        double bonus = 0.0;

        for (Atom donorHeavy : trialAtoms) {
            if (!isDonor(donorHeavy, trialAtoms)) continue;

            Atom hAtom = findAttachedH(donorHeavy, trialAtoms);
            if (hAtom == null) continue;

            Point3D dPos = donorHeavy.getPosition();
            Point3D hPos = hAtom.getPosition();

            for (Residue er : env) {
                if (shareSameResidue(scoredResidue, er)) continue;
                for (Atom acc : er.getAtoms()) {
                    if (!isAcceptor(acc, er.getAtoms())) continue;
                    double dist = distance(hPos, acc.getPosition());
                    if (dist < 1.5 || dist > 3.5) continue;

                    double angle = angle(dPos, hPos, acc.getPosition());
                    double deviation = angle - 180.0;
                    double quality = Math.exp(-deviation * deviation / 450.0);
                    bonus -= 2.0 * quality;
                }
            }
        }
        return bonus;
    }

    private boolean isDonor(Atom a, List<Atom> trialAtoms) {
        String elem = a.getElement().getSymbol();
        if ("N".equals(elem) || "O".equals(elem)) {
            return findAttachedH(a, trialAtoms) != null;
        }
        return false;
    }

    private boolean isAcceptor(Atom a, List<Atom> atoms) {
        String elem = a.getElement().getSymbol();
        if ("O".equals(elem)) return true;
        if ("N".equals(elem)) {
            return findAttachedH(a, atoms) == null;
        }
        return false;
    }

    private Atom findAttachedH(Atom heavy, List<Atom> atoms) {
        Point3D hp = heavy.getPosition();
        Atom best = null;
        double bestDist = Double.MAX_VALUE;
        for (Atom a : atoms) {
            if (!"H".equals(a.getElement().getSymbol())) continue;
            double d = distance(hp, a.getPosition());
            if (d < 1.2 && d < bestDist) {
                bestDist = d;
                best = a;
            }
        }
        return best;
    }

    /** True when h is a hydrogen bonded (<= 1.2 Å) to an N or O in the given atom list. */
    private boolean isDonorHydrogen(Atom h, List<Atom> atoms) {
        if (!"H".equals(h.getElement().getSymbol())) return false;
        Point3D hp = h.getPosition();
        for (Atom heavy : atoms) {
            String elem = heavy.getElement().getSymbol();
            if (!"N".equals(elem) && !"O".equals(elem)) continue;
            if (distance(hp, heavy.getPosition()) < 1.2) return true;
        }
        return false;
    }

    private String resolveLookupName(Residue r) {
        return resolveLookupName(r.getName(), r.getAtoms());
    }

    private String resolveLookupName(String name, List<Atom> atoms) {
        if (!"HIS".equals(name)) return name;
        boolean hasHD1 = atoms.stream().anyMatch(a -> "HD1".equals(a.getName()));
        boolean hasHE2 = atoms.stream().anyMatch(a -> "HE2".equals(a.getName()));
        if (hasHD1 && hasHE2) return "HIP";
        if (hasHD1) return "HID";
        return "HIE";
    }

    private double getCharge(Atom atom, String residueName) {
        var tpl = amberLib.getTemplate(residueName);
        if (tpl == null) return 0.0;
        var atomTpl = tpl.getAtom(atom.getName());
        return atomTpl != null ? atomTpl.getCharge() : 0.0;
    }

    private String getAmberType(Atom atom, String residueName) {
        var tpl = amberLib.getTemplate(residueName);
        if (tpl == null) return null;
        var atomTpl = tpl.getAtom(atom.getName());
        return atomTpl != null ? atomTpl.getAmberType() : null;
    }

    private boolean shareSameResidue(Residue scored, Residue er) {
        return scored == er;
    }

    private Point3D sub(Point3D a, Point3D b) {
        return new Point3D(a.x() - b.x(), a.y() - b.y(), a.z() - b.z());
    }

    private double distance(Point3D a, Point3D b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

    private double angle(Point3D a, Point3D b, Point3D c) {
        Point3D ba = sub(a, b);
        Point3D bc = sub(c, b);
        double dot = ba.x()*bc.x() + ba.y()*bc.y() + ba.z()*bc.z();
        double magBA = Math.sqrt(ba.x()*ba.x() + ba.y()*ba.y() + ba.z()*ba.z());
        double magBC = Math.sqrt(bc.x()*bc.x() + bc.y()*bc.y() + bc.z()*bc.z());
        if (magBA < 1e-12 || magBC < 1e-12) return 180.0;
        double cos = dot / (magBA * magBC);
        cos = Math.max(-1.0, Math.min(1.0, cos));
        return Math.toDegrees(Math.acos(cos));
    }
}
