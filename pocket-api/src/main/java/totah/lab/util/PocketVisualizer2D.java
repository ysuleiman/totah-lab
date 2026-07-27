package totah.lab.util;


import totah.lab.protein.Pocket;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;

import java.util.List;

public final class PocketVisualizer2D {

    private PocketVisualizer2D() {}

    /**
     * Prints a comprehensive 2D contact matrix of pocket residues to the console.
     * Automatically annotates chemically active polar anchors and outputs their
     * calculated center-of-mass for targeted molecular docking configurations.
     */
    public static void printAnatomicalPocketMap2D(Pocket pocket) {
        List<Residue> residues = pocket.getResidues();
        int n = residues.size();
        if (n == 0) {
            System.out.println("Empty pocket - cannot generate anatomical visualization.");
            return;
        }

        System.out.println("\n=========================================================================");
        System.out.println("--- 2D CHEMO-ANATOMICAL PROXIMITY MAP FOR: " + pocket.getName() + " ---");
        System.out.println("=========================================================================");
        System.out.println("Legend: [(-) Negative Anchor] | [(+) Positive Anchor] | [* Polar/Catalytic] | [SH Covalent Cys]");
        System.out.println("        █ = Tightly Packed (<5Å)  ░ = Neighborhood (<8Å)  . = Cavity Void (>8Å)\n");

        // 1. Print Horizontal Sequence Header Tracking Labels
        System.out.print("        ");
        for (int i = 0; i < n; i++) {
            Residue r = residues.get(i);
            String shortName = r.getName().substring(0, Math.min(3, r.getName().length())).toUpperCase();
            // Use the exact same formatting width (4 spaces) as the cell outputs below
            System.out.print(String.format("%-4s", shortName));
        }
        System.out.println();

        // Variables to calculate a targeted center of mass for active polar anchors
        double totalX = 0, totalY = 0, totalZ = 0;
        int activeAnchorCount = 0;

        // 2. Iterate and Build the 2D Interaction Grid
        for (int i = 0; i < n; i++) {
            Residue resA = residues.get(i);
            String nameA = resA.getName().toUpperCase();

            // Assign biochemical structural classification prefixes
            String annotationTag = " ";
            if ("ASP".equals(nameA) || "GLU".equals(nameA)) {
                annotationTag = "(-)";
            } else if ("LYS".equals(nameA) || "ARG".equals(nameA)) {
                annotationTag = "(+)";
            } else if ("HIS".equals(nameA) || "SER".equals(nameA) || "THR".equals(nameA) || "ASN".equals(nameA) || "GLN".equals(nameA)) {
                annotationTag = " * ";
            } else if ("CYS".equals(nameA)) {
                annotationTag = "SH ";
            }

            // Accumulate spatial coordinates if the residue matches an interaction anchor profile
            if (!" ".equals(annotationTag) && resA.getAlphaCarbonPosition() != null) {
                Point3D posA = resA.getAlphaCarbonPosition();
                totalX += posA.x();
                totalY += posA.y();
                totalZ += posA.z();
                activeAnchorCount++;
            }

            // Print left vertical index row margin tracking labels
            String rowHeader = String.format("%s%s", resA.getChain(), resA.getNumber());
            System.out.print(String.format("%-5s%-3s", rowHeader, annotationTag));

            for (int j = 0; j < n; j++) {
                Residue resB = residues.get(j);

                if (resA.getAlphaCarbonPosition() != null && resB.getAlphaCarbonPosition() != null) {
                    Point3D posA = resA.getAlphaCarbonPosition();
                    Point3D posB = resB.getAlphaCarbonPosition();

                    double dx = posA.x() - posB.x();
                    double dy = posA.y() - posB.y();
                    double dz = posA.z() - posB.z();
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                    // Map spatial metrics onto structural matrix visual anchors
                    if (distance < 5.0) {
                        System.out.print(" █  "); // Close contact / structural wall loop fold
                    } else if (distance < 8.0) {
                        System.out.print(" ░  "); // Proximal shell margin boundaries
                    } else {
                        System.out.print(" .  "); // Cavity binding void / opposite channel walls
                    }
                } else {
                    System.out.print(" ?  ");
                }
            }
            System.out.println();
        }
        System.out.println("=========================================================================");

        // 3. Output Calculated Target Vectors for Downstream Docking Grids
        System.out.println("--- GEOMETRIC ANCHOR METRICS ---");
        System.out.println("Total Pocket Wall Residues Indexed: " + n);
        System.out.println("Identified Chemically Active Polar Anchors: " + activeAnchorCount);

        if (activeAnchorCount > 0) {
            double avgX = totalX / activeAnchorCount;
            double avgY = totalY / activeAnchorCount;
            double avgZ = totalZ / activeAnchorCount;
            System.out.print(String.format("Calculated Polar Center-of-Mass Coordinates: [X: %.4f, Y: %.4f, Z: %.4f]\n", avgX, avgY, avgZ));
        } else {
            System.out.println("Calculated Polar Center-of-Mass Coordinates: N/A (Pure Hydrophobic Basin)");
        }
        System.out.println("=========================================================================\n");
    }
}
