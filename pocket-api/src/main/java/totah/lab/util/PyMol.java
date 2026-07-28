package totah.lab.util;

import totah.lab.pocket.Pocket;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;

import java.io.File;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.List;

public class PyMol {

    public static void generateInstantView(Pocket pocket, String targetPdbPath, String outputCxcPath) throws IOException {
        if (pocket == null) return;

        // Use try-with-resources to guarantee the file handles close safely
        try (PrintWriter writer = new PrintWriter(new File(outputCxcPath))) {

            // 1. Open your local PDB file structure directly
            writer.println("open " + targetPdbPath);

            // 2. Clear out the default complex cartoon ribbons to avoid blindness
            //writer.println("hide everything");
            //writer.println("hide target atoms,cartoons,surfaces,ribbons");
            writer.println("hide target acsr");   // atoms, cartoons, surfaces, ribbons

            // 3. Loop through your pocket residues to display and style them cleanly
            List<Residue> residues = pocket.getResidues();
            for (Residue r : residues) {
                // ChimeraX absolute target specifier format: /chain:number (e.g. /A:45)
                String spec = "/" + r.getChain() + ":" + r.getNumber();

                // Show atoms explicitly
                //writer.println("show " + spec + " atoms");
                writer.println("show " + spec + " target a");


                // Stick styling: 'stickRadius' controls bond thickness (not 'bondRadius')
                writer.println("size " + spec + " atomRadius 0.3 stickRadius 0.3");

                if ("ASP".equals(r.getName()) || "GLU".equals(r.getName())) {
                    writer.println("color " + spec + " red target a");
                } else if ("LYS".equals(r.getName()) || "ARG".equals(r.getName())) {
                    writer.println("color " + spec + " blue target a");
                } else if ("PHE".equals(r.getName()) || "TRP".equals(r.getName()) || "TYR".equals(r.getName()) ||
                        "LEU".equals(r.getName()) || "ILE".equals(r.getName()) || "VAL".equals(r.getName())) {
                    writer.println("color " + spec + " yellow target a");
                } else {
                    writer.println("color " + spec + " cyan target a");
                }

                // Generate clean native 3D text labels anchored to Alpha Carbon vectors
                writer.println("label " + spec + "@CA text \"" + r.getChain() + "_" + r.getNumber() + "_" + r.getName() + "\" color white height 1.2");
            }

            // 4. ChimeraX uses 'camera mode' instead of 'camera fly'
            //writer.println("camera mode back-and-forth");


            // 5. Set center of rotation with 'cofr' (comma-separated point; 'view center' isn't a real command)
            Point3D center = pocket.getCenter();
            writer.println("cofr " + center.x() + "," + center.y() + "," + center.z());
            writer.println("zoom 2.5");

            writer.flush();
        }
    }
}
