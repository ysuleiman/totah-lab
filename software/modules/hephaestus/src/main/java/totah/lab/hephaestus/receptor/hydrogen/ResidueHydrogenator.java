package totah.lab.hephaestus.receptor.hydrogen;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.ZMatrixMath;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;
import totah.lab.hephaestus.receptor.protonation.HistidineState;
import totah.lab.hephaestus.receptor.protonation.ProtonationConfig;

import java.util.List;
import java.util.Objects;

import static totah.lab.hephaestus.receptor.hydrogen.HydrogenGeometry.C_H_SP3;
import static totah.lab.hephaestus.receptor.hydrogen.HydrogenGeometry.N_H_SP2;
import static totah.lab.hephaestus.receptor.hydrogen.HydrogenGeometry.O_H;
import static totah.lab.hephaestus.receptor.hydrogen.HydrogenGeometry.O_H_ANGLE;
import static totah.lab.hephaestus.receptor.hydrogen.HydrogenGeometry.PLANAR_N_H_ANGLE;
import static totah.lab.hephaestus.receptor.hydrogen.HydrogenGeometry.S_H;
import static totah.lab.hephaestus.receptor.hydrogen.HydrogenGeometry.TETRAHEDRAL_ANGLE;
import static totah.lab.hephaestus.receptor.hydrogen.HydrogenGeometry.TRIGONAL_ANGLE;

/**
 * Adds backbone and side-chain hydrogens to a single residue.
 *
 * <p>This class is stateless. All per-run state and services are supplied by
 * {@link HydrogenationContext}.</p>
 */
public final class ResidueHydrogenator {

    private ResidueHydrogenator() {
    }

    public static void hydrogenateBackbone(
            String chainId,
            Residue residue,
            int index,
            List<Atom> atoms,
            HydrogenationContext context) {

        BackboneHydrogenator.hydrogenate(
                chainId,
                residue,
                index,
                atoms,
                context);
    }

    public static boolean isConsecutive(
            Residue previous,
            Residue current) {

        return BackboneHydrogenator.isConsecutive(
                previous,
                current);
    }

    public static void hydrogenateSideChain(
            String chainId,
            Residue residue,
            List<Atom> atoms,
            HydrogenationContext context) {

        Objects.requireNonNull(chainId, "chainId");
        Objects.requireNonNull(residue, "residue");
        Objects.requireNonNull(atoms, "atoms");
        Objects.requireNonNull(context, "context");

        String templateName =
                context.baseTemplateName(chainId, residue);

        String residueName =
                templateName != null
                        ? templateName
                        : residue.getName();

        if ("MSE".equals(residueName)) {
            residueName = "MET";
        }

        double ph = context.config().ph();

        HistidineState histidineState =
                context.config().histidineState();

        switch (residueName) {
            case "ALA" -> addAla(residue, atoms, context);
            case "VAL" -> addVal(residue, atoms, context);
            case "LEU" -> addLeu(residue, atoms, context);
            case "ILE" -> addIle(residue, atoms, context);
            case "PRO" -> addPro(residue, atoms, context);
            case "GLY" -> addGly(residue, atoms, context);
            case "SER" -> addSer(residue, atoms, context);
            case "THR" -> addThr(residue, atoms, context);

            case "CYS", "CYM", "CYX" ->
                    addCys(
                            chainId,
                            residue,
                            atoms,
                            context,
                            residueName);

            case "MET" -> addMet(residue, atoms, context);
            case "PHE" -> addPhe(residue, atoms, context);
            case "TYR" -> addTyr(residue, atoms, context, ph);
            case "TYS" -> addTyrRing(residue, atoms, context);
            case "TRP" -> addTrp(residue, atoms, context);

            case "HIS", "HID", "HIE", "HIP" ->
                    addHis(
                            residue,
                            atoms,
                            context,
                            histidineState,
                            residueName);

            case "LYS", "LYN" ->
                    addLys(
                            residue,
                            atoms,
                            context,
                            ph,
                            residueName);

            case "ARG" -> addArg(residue, atoms, context);

            case "ASP", "ASH" ->
                    addAsp(
                            residue,
                            atoms,
                            context,
                            ph,
                            residueName);

            case "GLU", "GLH" ->
                    addGlu(
                            residue,
                            atoms,
                            context,
                            ph,
                            residueName);

            case "ASN" -> addAsn(residue, atoms, context);
            case "GLN" -> addGln(residue, atoms, context);

            default -> throw new IllegalArgumentException(
                    "Unsupported residue type '"
                            + residue.getName()
                            + "' at "
                            + context.residueKey(chainId, residue));
        }
    }

    private static void addAla(
            Residue residue,
            List<Atom> atoms,
            HydrogenationContext context) {

        Atom betaCarbon = atom(residue, "CB");
        Atom alphaCarbon = atom(residue, "CA");
        Atom nitrogen = atom(residue, "N");

        if (betaCarbon != null
                && alphaCarbon != null
                && nitrogen != null) {

            addMethyl(
                    betaCarbon,
                    alphaCarbon,
                    nitrogen,
                    "HB",
                    atoms,
                    context);
        }
    }

    private static void addVal(
            Residue residue,
            List<Atom> atoms,
            HydrogenationContext context) {

        Atom betaCarbon = atom(residue, "CB");
        Atom alphaCarbon = atom(residue, "CA");
        Atom gammaCarbon1 = atom(residue, "CG1");
        Atom gammaCarbon2 = atom(residue, "CG2");

        if (betaCarbon == null || alphaCarbon == null) {
            return;
        }

        if (gammaCarbon1 != null && gammaCarbon2 != null) {
            Point3D position =
                    context.positionCalculator()
                            .tetrahedralFourthPosition(
                                    betaCarbon,
                                    alphaCarbon,
                                    gammaCarbon1,
                                    gammaCarbon2,
                                    C_H_SP3);

            addHydrogen(
                    "HB",
                    position,
                    betaCarbon,
                    atoms,
                    context);
        }

        if (gammaCarbon1 != null) {
            addMethyl(
                    gammaCarbon1,
                    betaCarbon,
                    alphaCarbon,
                    "HG1",
                    atoms,
                    context);
        }

        if (gammaCarbon2 != null) {
            addMethyl(
                    gammaCarbon2,
                    betaCarbon,
                    alphaCarbon,
                    "HG2",
                    atoms,
                    context);
        }
    }

    private static void addLeu(
            Residue residue,
            List<Atom> atoms,
            HydrogenationContext context) {

        Atom betaCarbon = atom(residue, "CB");
        Atom alphaCarbon = atom(residue, "CA");
        Atom gammaCarbon = atom(residue, "CG");
        Atom deltaCarbon1 = atom(residue, "CD1");
        Atom deltaCarbon2 = atom(residue, "CD2");

        if (betaCarbon != null
                && alphaCarbon != null
                && gammaCarbon != null) {

            addMethylene(
                    betaCarbon,
                    alphaCarbon,
                    gammaCarbon,
                    "HB",
                    atoms,
                    context);

            if (deltaCarbon1 != null && deltaCarbon2 != null) {
                Point3D position =
                        context.positionCalculator()
                                .tetrahedralFourthPosition(
                                        gammaCarbon,
                                        betaCarbon,
                                        deltaCarbon1,
                                        deltaCarbon2,
                                        C_H_SP3);

                addHydrogen(
                        "HG",
                        position,
                        gammaCarbon,
                        atoms,
                        context);
            }
        }

        if (deltaCarbon1 != null
                && gammaCarbon != null
                && betaCarbon != null) {

            addMethyl(
                    deltaCarbon1,
                    gammaCarbon,
                    betaCarbon,
                    "HD1",
                    atoms,
                    context);
        }

        if (deltaCarbon2 != null
                && gammaCarbon != null
                && betaCarbon != null) {

            addMethyl(
                    deltaCarbon2,
                    gammaCarbon,
                    betaCarbon,
                    "HD2",
                    atoms,
                    context);
        }
    }

    private static void addIle(
            Residue residue,
            List<Atom> atoms,
            HydrogenationContext context) {

        Atom betaCarbon = atom(residue, "CB");
        Atom alphaCarbon = atom(residue, "CA");
        Atom gammaCarbon1 = atom(residue, "CG1");
        Atom gammaCarbon2 = atom(residue, "CG2");
        Atom deltaCarbon1 = atom(residue, "CD1");

        if (betaCarbon == null || alphaCarbon == null) {
            return;
        }

        if (gammaCarbon1 != null) {
            Point3D position =
                    ZMatrixMath.calculatePosition(
                            betaCarbon.getPosition(),
                            alphaCarbon.getPosition(),
                            gammaCarbon1.getPosition(),
                            C_H_SP3,
                            TETRAHEDRAL_ANGLE,
                            Math.toRadians(120.0));

            addHydrogen(
                    "HB",
                    position,
                    betaCarbon,
                    atoms,
                    context);
        }

        if (gammaCarbon2 != null) {
            addMethyl(
                    gammaCarbon2,
                    betaCarbon,
                    alphaCarbon,
                    "HG2",
                    atoms,
                    context);
        }

        if (gammaCarbon1 != null && deltaCarbon1 != null) {
            addMethylene(
                    gammaCarbon1,
                    betaCarbon,
                    deltaCarbon1,
                    "HG1",
                    atoms,
                    context);

            addMethyl(
                    deltaCarbon1,
                    gammaCarbon1,
                    betaCarbon,
                    "HD1",
                    atoms,
                    context);
        }
    }

    private static void addPro(
            Residue residue,
            List<Atom> atoms,
            HydrogenationContext context) {

        Atom betaCarbon = atom(residue, "CB");
        Atom gammaCarbon = atom(residue, "CG");
        Atom deltaCarbon = atom(residue, "CD");
        Atom alphaCarbon = atom(residue, "CA");
        Atom nitrogen = atom(residue, "N");

        if (betaCarbon != null
                && alphaCarbon != null
                && gammaCarbon != null) {

            addMethylene(
                    betaCarbon,
                    alphaCarbon,
                    gammaCarbon,
                    "HB",
                    atoms,
                    context);
        }

        if (gammaCarbon != null
                && betaCarbon != null
                && deltaCarbon != null) {

            addMethylene(
                    gammaCarbon,
                    betaCarbon,
                    deltaCarbon,
                    "HG",
                    atoms,
                    context);
        }

        if (deltaCarbon != null
                && gammaCarbon != null
                && nitrogen != null) {

            addMethylene(
                    deltaCarbon,
                    gammaCarbon,
                    nitrogen,
                    "HD",
                    atoms,
                    context);
        }
    }

    private static void addGly(
            Residue residue,
            List<Atom> atoms,
            HydrogenationContext context) {

        Atom alphaCarbon = atom(residue, "CA");
        Atom nitrogen = atom(residue, "N");
        Atom carbonylCarbon = atom(residue, "C");

        if (alphaCarbon == null
                || nitrogen == null
                || carbonylCarbon == null) {
            return;
        }

        Point3D firstPosition =
                ZMatrixMath.calculatePosition(
                        alphaCarbon.getPosition(),
                        nitrogen.getPosition(),
                        carbonylCarbon.getPosition(),
                        C_H_SP3,
                        TETRAHEDRAL_ANGLE,
                        Math.toRadians(120.0));

        Point3D secondPosition =
                ZMatrixMath.calculatePosition(
                        alphaCarbon.getPosition(),
                        nitrogen.getPosition(),
                        carbonylCarbon.getPosition(),
                        C_H_SP3,
                        TETRAHEDRAL_ANGLE,
                        Math.toRadians(-120.0));

        addHydrogen(
                "HA2",
                firstPosition,
                alphaCarbon,
                atoms,
                context);

        addHydrogen(
                "HA3",
                secondPosition,
                alphaCarbon,
                atoms,
                context);
    }

    private static void addSer(
            Residue residue,
            List<Atom> atoms,
            HydrogenationContext context) {

        Atom betaCarbon = atom(residue, "CB");
        Atom alphaCarbon = atom(residue, "CA");
        Atom oxygen = atom(residue, "OG");

        if (betaCarbon == null
                || alphaCarbon == null
                || oxygen == null) {
            return;
        }

        addMethylene(
                betaCarbon,
                alphaCarbon,
                oxygen,
                "HB",
                atoms,
                context);

        if (!context.isNearMetal(oxygen.getPosition())) {
            Point3D position =
                    ZMatrixMath.calculatePosition(
                            oxygen.getPosition(),
                            betaCarbon.getPosition(),
                            alphaCarbon.getPosition(),
                            O_H,
                            O_H_ANGLE,
                            Math.PI);

            addHydrogen(
                    "HG",
                    position,
                    oxygen,
                    atoms,
                    context);
        }
    }

    private static void addThr(
            Residue residue,
            List<Atom> atoms,
            HydrogenationContext context) {

        Atom betaCarbon = atom(residue, "CB");
        Atom alphaCarbon = atom(residue, "CA");
        Atom oxygen = atom(residue, "OG1");
        Atom gammaCarbon = atom(residue, "CG2");

        if (betaCarbon == null || alphaCarbon == null) {
            return;
        }

        if (oxygen != null) {
            Point3D betaHydrogen =
                    ZMatrixMath.calculatePosition(
                            betaCarbon.getPosition(),
                            alphaCarbon.getPosition(),
                            oxygen.getPosition(),
                            C_H_SP3,
                            TETRAHEDRAL_ANGLE,
                            Math.toRadians(120.0));

            addHydrogen(
                    "HB",
                    betaHydrogen,
                    betaCarbon,
                    atoms,
                    context);

            if (!context.isNearMetal(oxygen.getPosition())) {
                Point3D hydroxylHydrogen =
                        ZMatrixMath.calculatePosition(
                                oxygen.getPosition(),
                                betaCarbon.getPosition(),
                                alphaCarbon.getPosition(),
                                O_H,
                                O_H_ANGLE,
                                Math.PI);

                addHydrogen(
                        "HG1",
                        hydroxylHydrogen,
                        oxygen,
                        atoms,
                        context);
            }
        }

        if (gammaCarbon != null) {
            addMethyl(
                    gammaCarbon,
                    betaCarbon,
                    alphaCarbon,
                    "HG2",
                    atoms,
                    context);
        }
    }

    private static void addCys(
            String chainId,
            Residue residue,
            List<Atom> atoms,
            HydrogenationContext context,
            String templateName) {

        Atom betaCarbon = atom(residue, "CB");
        Atom alphaCarbon = atom(residue, "CA");
        Atom sulfur = atom(residue, "SG");

        if (betaCarbon == null
                || alphaCarbon == null
                || sulfur == null) {
            return;
        }

        addMethylene(
                betaCarbon,
                alphaCarbon,
                sulfur,
                "HB",
                atoms,
                context);

        boolean disulfide =
                context.isDisulfideCysteine(chainId, residue)
                        || "CYX".equals(templateName);

        boolean deprotonated =
                "CYM".equals(templateName)
                        || context.config().ph()
                        > ProtonationConfig.PKA_CYS + 1.0;

        if (disulfide
                || deprotonated
                || context.isNearMetal(sulfur.getPosition())) {
            return;
        }

        Point3D position =
                ZMatrixMath.calculatePosition(
                        sulfur.getPosition(),
                        betaCarbon.getPosition(),
                        alphaCarbon.getPosition(),
                        S_H,
                        TETRAHEDRAL_ANGLE,
                        Math.PI);

        addHydrogen(
                "HG",
                position,
                sulfur,
                atoms,
                context);
    }

    private static void addMet(
            Residue residue,
            List<Atom> atoms,
            HydrogenationContext context) {

        Atom betaCarbon = atom(residue, "CB");
        Atom alphaCarbon = atom(residue, "CA");
        Atom gammaCarbon = atom(residue, "CG");
        Atom sulfur = atom(residue, "SD");
        Atom epsilonCarbon = atom(residue, "CE");

        if (betaCarbon != null
                && alphaCarbon != null
                && gammaCarbon != null
                && sulfur != null) {

            addMethylene(
                    betaCarbon,
                    alphaCarbon,
                    gammaCarbon,
                    "HB",
                    atoms,
                    context);

            addMethylene(
                    gammaCarbon,
                    betaCarbon,
                    sulfur,
                    "HG",
                    atoms,
                    context);
        }

        if (epsilonCarbon != null
                && sulfur != null
                && gammaCarbon != null) {

            addMethyl(
                    epsilonCarbon,
                    sulfur,
                    gammaCarbon,
                    "HE",
                    atoms,
                    context);
        }
    }

    private static void addPhe(
            Residue residue,
            List<Atom> atoms,
            HydrogenationContext context) {

        addAromaticRing(
                residue,
                atoms,
                context,
                true);
    }

    private static void addTyr(
            Residue residue,
            List<Atom> atoms,
            HydrogenationContext context,
            double ph) {

        addTyrRing(residue, atoms, context);

        Atom hydroxylOxygen = atom(residue, "OH");
        Atom zetaCarbon = atom(residue, "CZ");
        Atom epsilonCarbon = atom(residue, "CE1");

        if (hydroxylOxygen != null
                && zetaCarbon != null
                && epsilonCarbon != null
                && ph < ProtonationConfig.PKA_TYR - 1.0
                && !context.isNearMetal(
                hydroxylOxygen.getPosition())) {

            Point3D position =
                    ZMatrixMath.calculatePosition(
                            hydroxylOxygen.getPosition(),
                            zetaCarbon.getPosition(),
                            epsilonCarbon.getPosition(),
                            O_H,
                            O_H_ANGLE,
                            Math.PI);

            addHydrogen(
                    "HH",
                    position,
                    hydroxylOxygen,
                    atoms,
                    context);
        }
    }

    private static void addTyrRing(
            Residue residue,
            List<Atom> atoms,
            HydrogenationContext context) {

        Atom betaCarbon = atom(residue, "CB");
        Atom alphaCarbon = atom(residue, "CA");
        Atom gammaCarbon = atom(residue, "CG");

        if (betaCarbon != null
                && alphaCarbon != null
                && gammaCarbon != null) {

            addMethylene(
                    betaCarbon,
                    alphaCarbon,
                    gammaCarbon,
                    "HB",
                    atoms,
                    context);
        }

        addAromaticHydrogen(
                residue,
                atoms,
                context,
                "CD1",
                "CG",
                "CE1",
                "HD1");

        addAromaticHydrogen(
                residue,
                atoms,
                context,
                "CD2",
                "CG",
                "CE2",
                "HD2");

        addAromaticHydrogen(
                residue,
                atoms,
                context,
                "CE1",
                "CD1",
                "CZ",
                "HE1");

        addAromaticHydrogen(
                residue,
                atoms,
                context,
                "CE2",
                "CD2",
                "CZ",
                "HE2");
    }

    private static void addAromaticRing(
            Residue residue,
            List<Atom> atoms,
            HydrogenationContext context,
            boolean includeZetaHydrogen) {

        addTyrRing(residue, atoms, context);

        if (includeZetaHydrogen) {
            addAromaticHydrogen(
                    residue,
                    atoms,
                    context,
                    "CZ",
                    "CE1",
                    "CE2",
                    "HZ");
        }
    }

    private static void addAromaticHydrogen(
            Residue residue,
            List<Atom> atoms,
            HydrogenationContext context,
            String carbonName,
            String firstNeighborName,
            String secondNeighborName,
            String hydrogenName) {

        Atom carbon = atom(residue, carbonName);
        Atom firstNeighbor = atom(residue, firstNeighborName);
        Atom secondNeighbor = atom(residue, secondNeighborName);

        if (carbon == null
                || firstNeighbor == null
                || secondNeighbor == null) {
            return;
        }

        Point3D position =
                context.positionCalculator()
                        .aromaticHydrogenPosition(
                                carbon,
                                firstNeighbor,
                                secondNeighbor);

        addHydrogen(
                hydrogenName,
                position,
                carbon,
                atoms,
                context);
    }

    private static void addTrp(
            Residue residue,
            List<Atom> atoms,
            HydrogenationContext context) {

        Atom betaCarbon = atom(residue, "CB");
        Atom alphaCarbon = atom(residue, "CA");
        Atom gammaCarbon = atom(residue, "CG");
        Atom deltaCarbon1 = atom(residue, "CD1");
        Atom deltaCarbon2 = atom(residue, "CD2");
        Atom epsilonNitrogen = atom(residue, "NE1");
        Atom epsilonCarbon2 = atom(residue, "CE2");
        Atom epsilonCarbon3 = atom(residue, "CE3");
        Atom zetaCarbon2 = atom(residue, "CZ2");
        Atom zetaCarbon3 = atom(residue, "CZ3");
        Atom etaCarbon = atom(residue, "CH2");

        if (betaCarbon != null
                && alphaCarbon != null
                && gammaCarbon != null) {

            addMethylene(
                    betaCarbon,
                    alphaCarbon,
                    gammaCarbon,
                    "HB",
                    atoms,
                    context);
        }

        if (epsilonNitrogen != null
                && deltaCarbon1 != null
                && epsilonCarbon2 != null) {

            Point3D position =
                    ZMatrixMath.calculatePosition(
                            epsilonNitrogen.getPosition(),
                            deltaCarbon1.getPosition(),
                            epsilonCarbon2.getPosition(),
                            N_H_SP2,
                            Math.toRadians(125.0),
                            Math.PI);

            addHydrogen(
                    "HE1",
                    position,
                    epsilonNitrogen,
                    atoms,
                    context);
        }

        addAromaticHydrogen(
                residue,
                atoms,
                context,
                "CD1",
                "CG",
                "NE1",
                "HD1");

        addAromaticHydrogen(
                residue,
                atoms,
                context,
                "CE3",
                "CD2",
                "CZ3",
                "HE3");

        addAromaticHydrogen(
                residue,
                atoms,
                context,
                "CZ2",
                "CE2",
                "CH2",
                "HZ2");

        addAromaticHydrogen(
                residue,
                atoms,
                context,
                "CZ3",
                "CE3",
                "CH2",
                "HZ3");

        addAromaticHydrogen(
                residue,
                atoms,
                context,
                "CH2",
                "CZ2",
                "CZ3",
                "HH2");
    }

    private static void addHis(
            Residue residue,
            List<Atom> atoms,
            HydrogenationContext context,
            HistidineState configuredState,
            String templateName) {

        Atom betaCarbon = atom(residue, "CB");
        Atom alphaCarbon = atom(residue, "CA");
        Atom gammaCarbon = atom(residue, "CG");
        Atom deltaNitrogen = atom(residue, "ND1");
        Atom deltaCarbon = atom(residue, "CD2");
        Atom epsilonCarbon = atom(residue, "CE1");
        Atom epsilonNitrogen = atom(residue, "NE2");

        if (betaCarbon != null
                && alphaCarbon != null
                && gammaCarbon != null) {

            addMethylene(
                    betaCarbon,
                    alphaCarbon,
                    gammaCarbon,
                    "HB",
                    atoms,
                    context);
        }

        addAromaticHydrogen(
                residue,
                atoms,
                context,
                "CD2",
                "CG",
                "NE2",
                "HD2");

        addAromaticHydrogen(
                residue,
                atoms,
                context,
                "CE1",
                "ND1",
                "NE2",
                "HE1");

        HistidineState resolvedState = switch (templateName) {
            case "HID" -> HistidineState.HID;
            case "HIP" -> HistidineState.HIP;
            case "HIE", "HIS" -> HistidineState.HIE;
            default -> configuredState;
        };

        if ((resolvedState == HistidineState.HID
                || resolvedState == HistidineState.HIP)
                && deltaNitrogen != null
                && gammaCarbon != null
                && epsilonCarbon != null
                && !context.isNearMetal(
                deltaNitrogen.getPosition())) {

            Point3D position =
                    ZMatrixMath.calculatePosition(
                            deltaNitrogen.getPosition(),
                            gammaCarbon.getPosition(),
                            epsilonCarbon.getPosition(),
                            N_H_SP2,
                            Math.toRadians(125.0),
                            Math.PI);

            addHydrogen(
                    "HD1",
                    position,
                    deltaNitrogen,
                    atoms,
                    context);
        }

        if ((resolvedState == HistidineState.HIE
                || resolvedState == HistidineState.HIP)
                && epsilonNitrogen != null
                && epsilonCarbon != null
                && deltaCarbon != null
                && !context.isNearMetal(
                epsilonNitrogen.getPosition())) {

            Point3D position =
                    ZMatrixMath.calculatePosition(
                            epsilonNitrogen.getPosition(),
                            epsilonCarbon.getPosition(),
                            deltaCarbon.getPosition(),
                            N_H_SP2,
                            Math.toRadians(125.0),
                            Math.PI);

            addHydrogen(
                    "HE2",
                    position,
                    epsilonNitrogen,
                    atoms,
                    context);
        }
    }

    private static void addLys(
            Residue residue,
            List<Atom> atoms,
            HydrogenationContext context,
            double ph,
            String templateName) {

        Atom betaCarbon = atom(residue, "CB");
        Atom alphaCarbon = atom(residue, "CA");
        Atom gammaCarbon = atom(residue, "CG");
        Atom deltaCarbon = atom(residue, "CD");
        Atom epsilonCarbon = atom(residue, "CE");
        Atom zetaNitrogen = atom(residue, "NZ");

        if (betaCarbon != null
                && alphaCarbon != null
                && gammaCarbon != null) {

            addMethylene(
                    betaCarbon,
                    alphaCarbon,
                    gammaCarbon,
                    "HB",
                    atoms,
                    context);

            if (deltaCarbon != null) {
                addMethylene(
                        gammaCarbon,
                        betaCarbon,
                        deltaCarbon,
                        "HG",
                        atoms,
                        context);
            }

            if (deltaCarbon != null && epsilonCarbon != null) {
                addMethylene(
                        deltaCarbon,
                        gammaCarbon,
                        epsilonCarbon,
                        "HD",
                        atoms,
                        context);
            }

            if (epsilonCarbon != null && zetaNitrogen != null) {
                addMethylene(
                        epsilonCarbon,
                        deltaCarbon,
                        zetaNitrogen,
                        "HE",
                        atoms,
                        context);
            }
        }

        if (zetaNitrogen == null
                || epsilonCarbon == null
                || deltaCarbon == null) {
            return;
        }

        boolean neutral =
                "LYN".equals(templateName)
                        || ph > ProtonationConfig.PKA_LYS + 1.0;

        if (neutral) {
            addPlanarNh2(
                    zetaNitrogen,
                    epsilonCarbon,
                    deltaCarbon,
                    "HZ",
                    atoms,
                    context);
        } else {
            addAmmonium(
                    zetaNitrogen,
                    epsilonCarbon,
                    deltaCarbon,
                    "HZ",
                    atoms,
                    context);
        }
    }

    private static void addArg(
            Residue residue,
            List<Atom> atoms,
            HydrogenationContext context) {

        Atom betaCarbon = atom(residue, "CB");
        Atom alphaCarbon = atom(residue, "CA");
        Atom gammaCarbon = atom(residue, "CG");
        Atom deltaCarbon = atom(residue, "CD");
        Atom epsilonNitrogen = atom(residue, "NE");
        Atom zetaCarbon = atom(residue, "CZ");
        Atom etaNitrogen1 = atom(residue, "NH1");
        Atom etaNitrogen2 = atom(residue, "NH2");

        if (betaCarbon != null
                && alphaCarbon != null
                && gammaCarbon != null) {

            addMethylene(
                    betaCarbon,
                    alphaCarbon,
                    gammaCarbon,
                    "HB",
                    atoms,
                    context);

            if (deltaCarbon != null) {
                addMethylene(
                        gammaCarbon,
                        betaCarbon,
                        deltaCarbon,
                        "HG",
                        atoms,
                        context);
            }

            if (deltaCarbon != null && epsilonNitrogen != null) {
                addMethylene(
                        deltaCarbon,
                        gammaCarbon,
                        epsilonNitrogen,
                        "HD",
                        atoms,
                        context);
            }
        }

        if (epsilonNitrogen != null
                && deltaCarbon != null
                && zetaCarbon != null) {

            Point3D position =
                    ZMatrixMath.calculatePosition(
                            epsilonNitrogen.getPosition(),
                            deltaCarbon.getPosition(),
                            zetaCarbon.getPosition(),
                            N_H_SP2,
                            TRIGONAL_ANGLE,
                            Math.PI);

            addHydrogen(
                    "HE",
                    position,
                    epsilonNitrogen,
                    atoms,
                    context);
        }

        if (etaNitrogen1 != null
                && zetaCarbon != null
                && epsilonNitrogen != null) {

            addPlanarNh2(
                    etaNitrogen1,
                    zetaCarbon,
                    epsilonNitrogen,
                    "HH1",
                    atoms,
                    context);
        }

        if (etaNitrogen2 != null
                && zetaCarbon != null
                && epsilonNitrogen != null) {

            addPlanarNh2(
                    etaNitrogen2,
                    zetaCarbon,
                    epsilonNitrogen,
                    "HH2",
                    atoms,
                    context);
        }
    }

    private static void addAsp(
            Residue residue,
            List<Atom> atoms,
            HydrogenationContext context,
            double ph,
            String templateName) {

        Atom betaCarbon = atom(residue, "CB");
        Atom alphaCarbon = atom(residue, "CA");
        Atom gammaCarbon = atom(residue, "CG");

        if (betaCarbon != null
                && alphaCarbon != null
                && gammaCarbon != null) {

            addMethylene(
                    betaCarbon,
                    alphaCarbon,
                    gammaCarbon,
                    "HB",
                    atoms,
                    context);
        }

        boolean protonated =
                "ASH".equals(templateName)
                        || ph < ProtonationConfig.PKA_ASP - 1.0;

        Atom oxygen = atom(residue, "OD2");

        if (!protonated
                || oxygen == null
                || gammaCarbon == null
                || betaCarbon == null
                || context.isNearMetal(oxygen.getPosition())) {
            return;
        }

        Point3D position =
                ZMatrixMath.calculatePosition(
                        oxygen.getPosition(),
                        gammaCarbon.getPosition(),
                        betaCarbon.getPosition(),
                        O_H,
                        TRIGONAL_ANGLE,
                        0.0);

        addHydrogen(
                "HD2",
                position,
                oxygen,
                atoms,
                context);
    }

    private static void addGlu(
            Residue residue,
            List<Atom> atoms,
            HydrogenationContext context,
            double ph,
            String templateName) {

        Atom betaCarbon = atom(residue, "CB");
        Atom alphaCarbon = atom(residue, "CA");
        Atom gammaCarbon = atom(residue, "CG");
        Atom deltaCarbon = atom(residue, "CD");

        if (betaCarbon != null
                && alphaCarbon != null
                && gammaCarbon != null) {

            addMethylene(
                    betaCarbon,
                    alphaCarbon,
                    gammaCarbon,
                    "HB",
                    atoms,
                    context);
        }

        if (gammaCarbon != null
                && betaCarbon != null
                && deltaCarbon != null) {

            addMethylene(
                    gammaCarbon,
                    betaCarbon,
                    deltaCarbon,
                    "HG",
                    atoms,
                    context);
        }

        boolean protonated =
                "GLH".equals(templateName)
                        || ph < ProtonationConfig.PKA_GLU - 1.0;

        Atom oxygen = atom(residue, "OE2");

        if (!protonated
                || oxygen == null
                || deltaCarbon == null
                || gammaCarbon == null
                || context.isNearMetal(oxygen.getPosition())) {
            return;
        }

        Point3D position =
                ZMatrixMath.calculatePosition(
                        oxygen.getPosition(),
                        deltaCarbon.getPosition(),
                        gammaCarbon.getPosition(),
                        O_H,
                        TRIGONAL_ANGLE,
                        0.0);

        addHydrogen(
                "HE2",
                position,
                oxygen,
                atoms,
                context);
    }

    private static void addAsn(
            Residue residue,
            List<Atom> atoms,
            HydrogenationContext context) {

        Atom betaCarbon = atom(residue, "CB");
        Atom alphaCarbon = atom(residue, "CA");
        Atom gammaCarbon = atom(residue, "CG");
        Atom deltaNitrogen = atom(residue, "ND2");

        if (betaCarbon != null
                && alphaCarbon != null
                && gammaCarbon != null) {

            addMethylene(
                    betaCarbon,
                    alphaCarbon,
                    gammaCarbon,
                    "HB",
                    atoms,
                    context);
        }

        if (deltaNitrogen != null
                && gammaCarbon != null
                && betaCarbon != null) {

            addPlanarNh2(
                    deltaNitrogen,
                    gammaCarbon,
                    betaCarbon,
                    "HD2",
                    atoms,
                    context);
        }
    }

    private static void addGln(
            Residue residue,
            List<Atom> atoms,
            HydrogenationContext context) {

        Atom betaCarbon = atom(residue, "CB");
        Atom alphaCarbon = atom(residue, "CA");
        Atom gammaCarbon = atom(residue, "CG");
        Atom deltaCarbon = atom(residue, "CD");
        Atom epsilonNitrogen = atom(residue, "NE2");

        if (betaCarbon != null
                && alphaCarbon != null
                && gammaCarbon != null) {

            addMethylene(
                    betaCarbon,
                    alphaCarbon,
                    gammaCarbon,
                    "HB",
                    atoms,
                    context);
        }

        if (gammaCarbon != null
                && betaCarbon != null
                && deltaCarbon != null) {

            addMethylene(
                    gammaCarbon,
                    betaCarbon,
                    deltaCarbon,
                    "HG",
                    atoms,
                    context);
        }

        if (epsilonNitrogen != null
                && deltaCarbon != null
                && gammaCarbon != null) {

            addPlanarNh2(
                    epsilonNitrogen,
                    deltaCarbon,
                    gammaCarbon,
                    "HE2",
                    atoms,
                    context);
        }
    }

    /**
     * Resolves an atom through the Optional-based residue API while keeping
     * the hydrogenation methods readable and compatible with their existing
     * early-return behavior for incomplete residues.
     */
    private static Atom atom(
            Residue residue,
            String atomName) {

        return residue.findAtom(atomName).orElse(null);
    }

    private static void addMethyl(
            Atom center,
            Atom firstAnchor,
            Atom secondAnchor,
            String prefix,
            List<Atom> atoms,
            HydrogenationContext context) {

        if (center == null
                || firstAnchor == null
                || secondAnchor == null) {
            return;
        }

        List<Point3D> positions =
                context.positionCalculator()
                        .methylPositions(
                                center,
                                firstAnchor,
                                secondAnchor);

        addHydrogens(
                center,
                prefix,
                List.of("1", "2", "3"),
                positions,
                atoms,
                context);
    }

    private static void addMethylene(
            Atom center,
            Atom firstAnchor,
            Atom secondAnchor,
            String prefix,
            List<Atom> atoms,
            HydrogenationContext context) {

        if (center == null
                || firstAnchor == null
                || secondAnchor == null) {
            return;
        }

        List<Point3D> positions =
                context.positionCalculator()
                        .methylenePositions(
                                center,
                                firstAnchor,
                                secondAnchor);

        addHydrogens(
                center,
                prefix,
                List.of("2", "3"),
                positions,
                atoms,
                context);
    }

    private static void addPlanarNh2(
            Atom center,
            Atom firstAnchor,
            Atom secondAnchor,
            String prefix,
            List<Atom> atoms,
            HydrogenationContext context) {

        if (center == null
                || firstAnchor == null
                || secondAnchor == null) {
            return;
        }

        List<Point3D> positions =
                context.positionCalculator()
                        .planarNh2Positions(
                                center,
                                firstAnchor,
                                secondAnchor);

        addHydrogens(
                center,
                prefix,
                List.of("1", "2"),
                positions,
                atoms,
                context);
    }

    private static void addAmmonium(
            Atom center,
            Atom firstAnchor,
            Atom secondAnchor,
            String prefix,
            List<Atom> atoms,
            HydrogenationContext context) {

        if (center == null
                || firstAnchor == null
                || secondAnchor == null) {
            return;
        }

        List<Point3D> positions =
                context.positionCalculator()
                        .ammoniumPositions(
                                center,
                                firstAnchor,
                                secondAnchor);

        addHydrogens(
                center,
                prefix,
                List.of("1", "2", "3"),
                positions,
                atoms,
                context);
    }

    private static void addHydrogens(
            Atom parent,
            String prefix,
            List<String> suffixes,
            List<Point3D> positions,
            List<Atom> atoms,
            HydrogenationContext context) {

        if (positions.size() != suffixes.size()) {
            throw new IllegalStateException(
                    "Hydrogen name count does not match position count.");
        }

        for (int index = 0; index < positions.size(); index++) {
            addHydrogen(
                    prefix + suffixes.get(index),
                    positions.get(index),
                    parent,
                    atoms,
                    context);
        }
    }

    private static void addHydrogen(
            String name,
            Point3D position,
            Atom parent,
            List<Atom> atoms,
            HydrogenationContext context) {

        if (position == null || parent == null) {
            return;
        }

        Atom hydrogen =
                context.atomFactory()
                        .createHydrogen(
                                name,
                                position,
                                parent.getBFactor());

        context.tryAdd(
                atoms,
                hydrogen,
                parent);
    }
}
