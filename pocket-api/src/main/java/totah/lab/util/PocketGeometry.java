package totah.lab.util;

import totah.lab.pocket.Dimensions;
import totah.lab.pocket.Sphere;
import totah.lab.protein.*;

import java.util.List;
import java.util.Objects;

public class PocketGeometry {

    private PocketGeometry(){}

    /**
     * Calculates the bounding box size dimensions of a given pocket based on its
     * underlying atom positions. Automatically includes a standard padding buffer.
     */
    public static Dimensions calculatePocketDimensions(Pocket pocket) {
        List<Residue> resolvedResidues = pocket.getResidues(); // Invokes your custom resolver magic!
        if (resolvedResidues.isEmpty()) {
            return new Dimensions(0, 0, 0);
        }

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (Residue residue : resolvedResidues) {
            if (residue.getAtoms() == null) continue;
            for (Atom atom : residue.getAtoms()) {
                Point3D pos = atom.getPosition(); // Matches your newly standardized field name
                if (pos == null) continue;

                minX = Math.min(minX, pos.x()); maxX = Math.max(maxX, pos.x());
                minY = Math.min(minY, pos.y()); maxY = Math.max(maxY, pos.y());
                minZ = Math.min(minZ, pos.z()); maxZ = Math.max(maxZ, pos.z());
            }
        }

        // Return a zero dimension if no valid atom coordinates were encountered
        if (minX == Double.MAX_VALUE) {
            return new Dimensions(0, 0, 0);
        }

        // Adds padding buffer (~4.0 Å) around raw atom edges to create a proper docking grid box size
        double sizeX = (maxX - minX) + 4.0;
        double sizeY = (maxY - minY) + 4.0;
        double sizeZ = (maxZ - minZ) + 4.0;

        return new Dimensions(sizeX, sizeY, sizeZ);
    }

    /**
     * Maps the spatial depth of each active polar anchor relative to the pocket's central point.
     * Categorizes them into the Deep Core, Mid-Wall, or Exterior Rim of the binding cavity.
     */
    public static void analyzeAnchorDepths(Pocket pocket) {
        Point3D pocketCenter = pocket.getCenter();
        if (pocketCenter == null) {
            System.out.println("Cannot analyze anchor depths: Pocket center coordinate is missing.");
            return;
        }

        List<Residue> residues = pocket.getResidues();
        System.out.println("\n=======================================================");
        System.out.println("--- SPATIAL ANCHOR DEPTH PROFILE FOR: " + pocket.getName() + " ---");
        System.out.println("=======================================================");
        System.out.println(String.format("Pocket Core Center Point: [X: %.4f, Y: %.4f, Z: %.4f]",
                pocketCenter.x(), pocketCenter.y(), pocketCenter.z()));
        System.out.println("-------------------------------------------------------");
        System.out.println(String.format("%-10s %-6s %-12s %-15s", "Residue", "Type", "Distance (Å)", "Location Zone"));
        System.out.println("-------------------------------------------------------");

        for (Residue res : residues) {
            String name = res.getName().toUpperCase();

            // Check if the residue matches an interactive polar anchor type
            boolean isAnchor = "ASP".equals(name) || "GLU".equals(name) ||
                    "LYS".equals(name) || "ARG".equals(name) ||
                    "HIS".equals(name) || "SER".equals(name) ||
                    "THR".equals(name) || "ASN".equals(name) ||
                    "GLN".equals(name) || "CYS".equals(name);

            if (isAnchor && res.getAlphaCarbonPosition() != null) {
                Point3D anchorPos = res.getAlphaCarbonPosition();

                // Compute Euclidean distance from the anchor to the core center
                double dx = anchorPos.x() - pocketCenter.x();
                double dy = anchorPos.y() - pocketCenter.y();
                double dz = anchorPos.z() - pocketCenter.z();
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                // Categorize spatial depth location zone
                String zone;
                if (distance <= 4.0) {
                    zone = "DEEP CORE BASIN";
                } else if (distance <= 8.0) {
                    zone = "MID-WALL CLEFT";
                } else {
                    zone = "EXTERIOR RIM";
                }

                String residueLabel = res.getChain() + res.getName();
                System.out.println(String.format("%-10s %-6s %-12.4f %-15s",
                        residueLabel, name, distance, zone));
            }
        }
        System.out.println("=======================================================\n");
    }

    /**
     * Checks if a residue that was excluded by the pocket finder actually has
     * a physical surface opening or structural exposure facing the cavity.
     */
    public static void checkHiddenResidueExposure(Pocket pocket, Residue hiddenResidue) {
        Objects.requireNonNull(pocket, "Pocket cannot be null");
        if (hiddenResidue == null) {
            System.out.println("Target residue not found in protein structure.");
            return;
        }

        // 1. Extract the alpha spheres representing the pocket volume space
        @SuppressWarnings("unchecked")
        List<Sphere> alphaSpheres = (List<Sphere>) pocket.getAttributes().get("alpha_spheres");

        if (alphaSpheres == null || alphaSpheres.isEmpty()) {
            System.out.println("Cannot check exposure: No alpha sphere data loaded for " + pocket.getName());
            return;
        }

        double shortestDistance = Double.MAX_VALUE;
        String closestAtomName = "";

        // 2. Loop through every single heavy atom in CYS 203 (including its reactive SG side chain)
        for (Atom atom : hiddenResidue.getAtoms()) {
            Point3D atomPos = atom.getPosition();

            for (Sphere sphere : alphaSpheres) {
                // Calculate direct 3D Euclidean distance to the pocket volume edge
                double dx = atomPos.x() - sphere.x();
                double dy = atomPos.y() - sphere.y();
                double dz = atomPos.z() - sphere.z();
                double distance = Math.sqrt(dx*dx + dy*dy + dz*dz);

                if (distance < shortestDistance) {
                    shortestDistance = distance;
                    closestAtomName = atom.getName();
                }
            }
        }

        // 3. Interpret the structural threshold results
        String resLabel = hiddenResidue.getChain() + hiddenResidue.getNumber() + " (" + hiddenResidue.getName() + ")";
        System.out.println("\n=======================================================");
        System.out.println("--- EXPOSURE PROXIMITY ANALYSER FOR: " + pocket.getName() + " ---");
        System.out.println("=======================================================");
        System.out.println("Testing Target: " + resLabel);
        System.out.println(String.format("Shortest distance to cavity space: %.4f Å (via atom %s)",
                shortestDistance, closestAtomName));

        System.out.print("Structural Verdict: ");
        if (shortestDistance <= 3.5) {
            System.out.println("EXPOSED! This residue lines a side groove or alternative opening.");
        } else if (shortestDistance <= 6.0) {
            System.out.println("PARTIALLY SHIELDED. Sits right behind the first row wall layer.");
        } else {
            System.out.println("BURIED DEEP. Points completely away into the bulk core protein.");
        }
        System.out.println("=======================================================\n");
    }

    /**
     * Tool-Agnostic Exposure Checker: Measures the shortest atomic distance between an
     * excluded residue (e.g., CYS 203) and the confirmed walls of a Pocket (P2Rank or fpocket).
     */
    public static void checkHiddenResidueExposureUniversal(Pocket pocket, Residue hiddenResidue) {
        Objects.requireNonNull(pocket, "Pocket cannot be null");
        if (hiddenResidue == null) {
            System.out.println("Target hidden residue not found in protein structure.");
            return;
        }

        // 1. Grab the heavy wall residues using your custom lambda resolver magic
        List<Residue> wallResidues = pocket.getResidues();
        if (wallResidues.isEmpty()) {
            System.out.println("Cannot calculate exposure: Pocket wall contains no resolved residues.");
            return;
        }

        double shortestDistance = Double.MAX_VALUE;
        Atom closestHiddenAtom = null;
        Atom closestWallAtom = null;
        Residue closestWallResidue = null;

        // 2. All-against-all heavy atom distance loop
        for (Atom hiddenAtom : hiddenResidue.getAtoms()) {
            Point3D posHidden = hiddenAtom.getPosition();
            if (posHidden == null) continue;

            for (Residue wallRes : wallResidues) {
                if (wallRes.getAtoms() == null) continue;

                for (Atom wallAtom : wallRes.getAtoms()) {
                    Point3D posWall = wallAtom.getPosition();
                    if (posWall == null) continue;

                    // Calculate 3D Euclidean distance between atoms
                    double dx = posHidden.x() - posWall.x();
                    double dy = posHidden.y() - posWall.y();
                    double dz = posHidden.z() - posWall.z();
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                    if (distance < shortestDistance) {
                        shortestDistance = distance;
                        closestHiddenAtom = hiddenAtom;
                        closestWallAtom = wallAtom;
                        closestWallResidue = wallRes;
                    }
                }
            }
        }

        // 3. Print the Analytical Report
        String targetLabel = hiddenResidue.getChain() + hiddenResidue.getNumber() + " (" + hiddenResidue.getName() + ")";
        System.out.println("\n=========================================================================");
        System.out.println("--- UNIVERSAL POCKET EXPOSURE REPORT FOR: " + pocket.getName() + " ---");
        System.out.println("=========================================================================");
        System.out.println("Testing Target: " + targetLabel);

        if (closestHiddenAtom == null) {
            System.out.println("No valid spatial coordinates found to measure.");
            System.out.println("=========================================================================\n");
            return;
        }

        System.out.println(String.format("Shortest Interface Distance: %.4f Å", shortestDistance));
        System.out.println(String.format("Closest Interaction: Hidden Atom [%s] <--> Wall Residue %s%s Atom [%s]",
                closestHiddenAtom.getName(), closestWallResidue.getChain(), closestWallResidue.getNumber(), closestWallAtom.getName()));

        System.out.print("Anatomical Verdict: ");

        // 4. Structural Interpretation of Chemical Exposure
        if (shortestDistance > 5.0) {
            System.out.println("COMPLETELY DISJOINT. This residue is spatially distant from this binding site.");
        } else {
            // Check if the closest atom is a backbone atom (C, O, N, CA) or a side-chain atom
            String atomName = closestHiddenAtom.getName().toUpperCase();
            boolean isSideChain = !atomName.equals("N") && !atomName.equals("CA") && !atomName.equals("C") && !atomName.equals("O");

            if (isSideChain) {
                if ("SG".equals(atomName) && "CYS".equals(hiddenResidue.getName())) {
                    System.out.println("COVALENTLY ACCESSIBLE! The reactive thiol (SG) side chain faces directly into a wall cleft.");
                } else {
                    System.out.println("SIDE-CHAIN EXPOSED. The functional group actively lines an adjacent groove or pocket recess.");
                }
            } else {
                System.out.println("BURIED / BACKBONE PACKED. Only its structural backbone touches the wall; the side chain points AWAY.");
            }
        }
        System.out.println("=========================================================================\n");
    }

    /**
     * Native Location Prover: Analyzes the immediate atomic neighborhood surrounding
     * a specific atom to determine if it is buried in the core or exposed to solvent.
     */
    public static void proveAtomEnvironment(Structure structure, Residue targetResidue, String targetAtomName) {
        Objects.requireNonNull(structure, "Structure cannot be null");
        if (targetResidue == null) {
            System.out.println("Target residue is missing from structure context.");
            return;
        }

        // 1. Locate the specific side-chain atom we want to test (e.g., "SG" for Cys)
        Atom sourceAtom = null;
        for (Atom atom : targetResidue.getAtoms()) {
            if (targetAtomName.equalsIgnoreCase(atom.getName())) {
                sourceAtom = atom;
                break;
            }
        }

        if (sourceAtom == null) {
            System.out.println(String.format("Atom '%s' not found inside residue %s%s.",
                    targetAtomName, targetResidue.getChain(), targetResidue.getNumber()));
            return;
        }

        Point3D sourcePos = sourceAtom.getPosition();
        int coordinationNumber = 0;
        double interactionRadius = 5.0; // Standard biocatalytic shell radius in Ångströms

        // 2. Scan the global structure backdrop to count neighboring heavy atoms
        for (Residue res : structure.getResidues()) {
            // Skip checking against the target residue itself to prevent self-counting
            if (res.getChain().equals(targetResidue.getChain()) && res.getNumber() == targetResidue.getNumber()) {
                continue;
            }

            if (res.getAtoms() == null) continue;
            for (Atom atom : res.getAtoms()) {
                Point3D pos = atom.getPosition();
                if (pos == null) continue;

                double dx = sourcePos.x() - pos.x();
                double dy = sourcePos.y() - pos.y();
                double dz = sourcePos.z() - pos.z();
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                // Increment if another protein atom is crowding this space
                if (distance <= interactionRadius) {
                    coordinationNumber++;
                }
            }
        }

        // 3. Print the Environment Verdict
        String label = targetResidue.getChain() + targetResidue.getNumber() + " (" + targetResidue.getName() + ") [" + targetAtomName + "]";
        System.out.println("\n=========================================================================");
        System.out.println("--- NATIVE SOLVENT EXPOSURE ANALYSIS FOR: " + label + " ---");
        System.out.println("=========================================================================");
        System.out.println("Target Atom Position: " + String.format("[X: %.4f, Y: %.4f, Z: %.4f]", sourcePos.x(), sourcePos.y(), sourcePos.z()));
        System.out.println("Local Atomic Coordination Number (Neighbors within 5.0Å): " + coordinationNumber);

        System.out.print("Physical Location Verdict: ");
        if (coordinationNumber >= 12) {
            System.out.println("BURIED DEEP IN HYDROPHOBIC CORE");
            System.out.println("Anatomical Context: Locked inside the packed interior matrix. Frozen and un-reactive.");
        } else if (coordinationNumber <= 4) {
            System.out.println("FLAT SOLVENT-EXPOSED SURFACE");
            System.out.println("Anatomical Context: Fully exposed to bulk water, but sitting on a completely flat exterior shell.");
        } else {
            System.out.println("PARTIALLY SHIELDED INTERFACE");
            System.out.println("Anatomical Context: Tucked away in a structural crevice or packing boundary layer.");
        }
        System.out.println("=========================================================================\n");
    }
}
