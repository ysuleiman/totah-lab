package totah.lab.hephaestus.receptor.hydrogen;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.ZMatrixMath;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;
import totah.lab.hephaestus.receptor.protonation.CTerminusState;
import totah.lab.hephaestus.receptor.protonation.NTerminusState;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static totah.lab.hephaestus.receptor.hydrogen.HydrogenGeometry.*;

final class BackboneHydrogenator {

    private static final Set<String> STANDARD_AMINO_ACIDS = Set.of(
            "ALA", "ARG", "ASN", "ASP", "CYS",
            "GLN", "GLU", "GLY", "HIS", "ILE",
            "LEU", "LYS", "MET", "PHE", "PRO",
            "SER", "THR", "TRP", "TYR", "VAL",
            "TYS");

    private BackboneHydrogenator() {
    }

    static void hydrogenate(
            String chainId,
            Residue residue,
            int residueIndex,
            List<Atom> atoms,
            HydrogenationContext context) {

        Objects.requireNonNull(chainId, "chainId");
        Objects.requireNonNull(residue, "residue");
        Objects.requireNonNull(atoms, "atoms");
        Objects.requireNonNull(context, "context");

        Atom nitrogen = atom(residue, "N");
        Atom alphaCarbon = atom(residue, "CA");
        Atom carbonylCarbon = atom(residue, "C");

        if (nitrogen == null
                || alphaCarbon == null
                || carbonylCarbon == null) {
            return;
        }

        List<Residue> chainResidues = context.chainResidues();

        if (residueIndex < 0
                || residueIndex >= chainResidues.size()) {
            throw new IndexOutOfBoundsException(
                    "residueIndex is outside chain "
                            + chainId
                            + ": "
                            + residueIndex);
        }

        boolean standardResidue =
                STANDARD_AMINO_ACIDS.contains(
                        residue.getName());

        boolean chainNTerminus =
                standardResidue
                        && isNTerminus(
                        chainResidues,
                        residueIndex);

        boolean chainCTerminus =
                standardResidue
                        && isCTerminus(
                        chainResidues,
                        residueIndex);

        boolean useNTerminalTemplate =
                chainNTerminus
                        && context.usesNTerminalTemplate(
                        chainId,
                        residue);

        boolean useCTerminalTemplate =
                chainCTerminus
                        && context.usesCTerminalTemplate(
                        chainId,
                        residue);

        if (useNTerminalTemplate) {
            addNTerminalHydrogens(
                    residue,
                    atoms,
                    context,
                    nitrogen,
                    alphaCarbon,
                    carbonylCarbon);
        } else if (!"PRO".equals(residue.getName())) {
            addStandardBackboneHydrogen(
                    chainResidues,
                    residueIndex,
                    atoms,
                    context);
        }

        if (useCTerminalTemplate) {
            addCTerminalOxygen(
                    residue,
                    atoms,
                    context,
                    alphaCarbon,
                    carbonylCarbon);
        }

        addAlphaHydrogen(
                residue,
                atoms,
                context,
                nitrogen,
                alphaCarbon,
                carbonylCarbon);
    }

    private static void addNTerminalHydrogens(
            Residue residue,
            List<Atom> atoms,
            HydrogenationContext context,
            Atom nitrogen,
            Atom alphaCarbon,
            Atom carbonylCarbon) {

        NTerminusState state =
                context.config().nTerminusState();

        switch (state) {
            case NH3 -> {
                if ("PRO".equals(residue.getName())) {
                    Atom deltaCarbon =
                            atom(residue, "CD");

                    if (deltaCarbon != null) {
                        addSecondaryAmmonium(
                                nitrogen,
                                alphaCarbon,
                                deltaCarbon,
                                "H",
                                atoms,
                                context);
                    }
                } else {
                    Atom thirdReference =
                            atom(residue, "CB");

                    if (thirdReference == null) {
                        thirdReference = carbonylCarbon;
                    }

                    addAmmonium(
                            nitrogen,
                            alphaCarbon,
                            thirdReference,
                            "H",
                            atoms,
                            context);
                }
            }

            case ACE -> throw new UnsupportedOperationException(
                    "ACE N-terminal capping is not implemented.");
        }
    }

    private static void addCTerminalOxygen(
            Residue residue,
            List<Atom> atoms,
            HydrogenationContext context,
            Atom alphaCarbon,
            Atom carbonylCarbon) {

        CTerminusState state =
                context.config().cTerminusState();

        switch (state) {
            case COO -> {
                Atom carbonylOxygen =
                        atom(residue, "O");

                Atom existingOxt =
                        atom(residue, "OXT");

                if (carbonylOxygen == null
                        || existingOxt != null) {
                    return;
                }

                Point3D position =
                        ZMatrixMath.calculatePosition(
                                carbonylCarbon.getPosition(),
                                alphaCarbon.getPosition(),
                                carbonylOxygen.getPosition(),
                                C_OXT,
                                TRIGONAL_ANGLE,
                                Math.PI);

                Atom oxt =
                        context.atomFactory()
                                .createTerminalOxygen(
                                        "OXT",
                                        position,
                                        carbonylOxygen.getBFactor());

                context.insertion()
                        .tryAdd(
                                atoms,
                                oxt,
                                carbonylCarbon);
            }

            case NME -> {
                // NME capping requires adding a complete cap residue.
                // It should be implemented as a separate capping operation.
            }
        }
    }

    private static void addAlphaHydrogen(
            Residue residue,
            List<Atom> atoms,
            HydrogenationContext context,
            Atom nitrogen,
            Atom alphaCarbon,
            Atom carbonylCarbon) {

        if ("GLY".equals(residue.getName())) {
            return;
        }

        Atom betaCarbon =
                atom(residue, "CB");

        Point3D position = null;

        if (betaCarbon != null) {
            position =
                    context.positionCalculator()
                            .tetrahedralFourthPosition(
                                    alphaCarbon,
                                    nitrogen,
                                    carbonylCarbon,
                                    betaCarbon,
                                    C_H_SP3);
        }

        if (position == null) {
            position =
                    ZMatrixMath.calculatePosition(
                            alphaCarbon.getPosition(),
                            nitrogen.getPosition(),
                            carbonylCarbon.getPosition(),
                            C_H_SP3,
                            TETRAHEDRAL_ANGLE,
                            Math.PI);
        }

        Atom hydrogen =
                context.atomFactory()
                        .createHydrogen(
                                "HA",
                                position,
                                alphaCarbon.getBFactor());

        context.insertion()
                .tryAdd(
                        atoms,
                        hydrogen,
                        alphaCarbon);
    }

    private static void addStandardBackboneHydrogen(
            List<Residue> chainResidues,
            int residueIndex,
            List<Atom> atoms,
            HydrogenationContext context) {

        if (residueIndex == 0) {
            return;
        }

        Residue residue =
                chainResidues.get(residueIndex);

        Residue previous =
                chainResidues.get(residueIndex - 1);

        if (!isConsecutive(previous, residue)) {
            return;
        }

        Atom nitrogen =
                atom(residue, "N");

        Atom alphaCarbon =
                atom(residue, "CA");

        Atom previousCarbonylCarbon =
                atom(previous, "C");

        if (nitrogen == null
                || alphaCarbon == null
                || previousCarbonylCarbon == null) {
            return;
        }

        Point3D position =
                ZMatrixMath.calculatePosition(
                        nitrogen.getPosition(),
                        alphaCarbon.getPosition(),
                        previousCarbonylCarbon.getPosition(),
                        N_H_SP2,
                        PLANAR_N_H_ANGLE,
                        Math.PI);

        Atom hydrogen =
                context.atomFactory()
                        .createHydrogen(
                                "H",
                                position,
                                nitrogen.getBFactor());

        context.insertion()
                .tryAdd(
                        atoms,
                        hydrogen,
                        nitrogen);
    }

    private static void addAmmonium(
            Atom center,
            Atom firstAnchor,
            Atom secondAnchor,
            String prefix,
            List<Atom> atoms,
            HydrogenationContext context) {

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

    private static void addSecondaryAmmonium(
            Atom center,
            Atom firstAnchor,
            Atom secondAnchor,
            String prefix,
            List<Atom> atoms,
            HydrogenationContext context) {

        List<Point3D> positions =
                context.positionCalculator()
                        .secondaryAmmoniumPositions(
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

    private static void addHydrogens(
            Atom parent,
            String prefix,
            List<String> suffixes,
            List<Point3D> positions,
            List<Atom> atoms,
            HydrogenationContext context) {

        if (positions.size() != suffixes.size()) {
            throw new IllegalStateException(
                    "Hydrogen name and position counts differ.");
        }

        for (int index = 0;
             index < positions.size();
             index++) {

            Atom hydrogen =
                    context.atomFactory()
                            .createHydrogen(
                                    prefix + suffixes.get(index),
                                    positions.get(index),
                                    parent.getBFactor());

            context.insertion()
                    .tryAdd(
                            atoms,
                            hydrogen,
                            parent);
        }
    }

    static boolean isConsecutive(
            Residue previous,
            Residue current) {

        if (previous == null || current == null) {
            return false;
        }

        return current.getNumber()
                == previous.getNumber() + 1;
    }

    private static Atom atom(
            Residue residue,
            String atomName) {

        return residue.findAtom(atomName).orElse(null);
    }

    private static boolean isNTerminus(
            List<Residue> residues,
            int index) {

        if (index == 0) {
            return true;
        }

        return !isConsecutive(
                residues.get(index - 1),
                residues.get(index));
    }

    private static boolean isCTerminus(
            List<Residue> residues,
            int index) {

        if (index == residues.size() - 1) {
            return true;
        }

        return !isConsecutive(
                residues.get(index),
                residues.get(index + 1));
    }
}
