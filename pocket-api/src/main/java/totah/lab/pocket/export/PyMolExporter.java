package totah.lab.pocket.export;

import totah.lab.pocket.Pocket;
import totah.lab.pocket.PocketBox;
import totah.lab.pocket.Sphere;
import totah.lab.protein.Residue;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class PyMolExporter {
    public static void writeScript(
            Path scriptFile,
            Path proteinFile,
            Pocket pocket,
            PocketBox dockingBox,
            Path ligandFile) throws IOException {

        try (BufferedWriter writer = Files.newBufferedWriter(scriptFile)) {

            writer.write("reinitialize\n");
            writer.write("load " + pymolPath(proteinFile) + ", protein\n");

            if (ligandFile != null) {
                writer.write("load " + pymolPath(ligandFile) + ", ligand\n");
            }

            writer.write("""
                    hide everything
                    show cartoon, protein
                    color gray70, protein
                    set cartoon_transparency, 0.35
                    """);

            writePocketResidues(writer, pocket);
            writeAlphaSpheres(writer, pocket);
            writeDockingBox(writer, dockingBox);

            if (ligandFile != null) {
                writer.write("""
                        show sticks, ligand
                        color cyan, ligand
                        zoom pocket_residues or ligand, 6
                        """);
            } else {
                writer.write("zoom pocket_residues, 6\n");
            }

            writer.write("""
                    bg_color white
                    set depth_cue, 0
                    set ray_opaque_background, off
                    """);
        }
    }

    private static void writePocketResidues(
            BufferedWriter writer,
            Pocket pocket) throws IOException {

        String selection = pocket.getResidues().stream()
                .map(PyMolExporter::toSelection)
                .collect(Collectors.joining(" or "));

        writer.write("select pocket_residues, " + selection + "\n");
        writer.write("""
                show sticks, pocket_residues
                color orange, pocket_residues
                show surface, pocket_residues
                set transparency, 0.55, pocket_residues
                label pocket_residues and name CA, "%s%s" % (resn, resi)
                """);
    }

    private static String toSelection(Residue residue) {
        return String.format(
                "(chain %s and resi %d)",
                residue.getChain(),
                residue.getNumber());
    }

    private static void writeAlphaSpheres(
            BufferedWriter writer,
            Pocket pocket) throws IOException {

        writer.write("delete pocket_spheres\n");

        int index = 1;
        List<Sphere> spheres = pocket.getAttribute("alpha_spheres");
        for (Sphere sphere : spheres) {
            writer.write(String.format(
                    Locale.US,
                    "pseudoatom pocket_spheres, " +
                            "name PS%d, pos=[%.4f, %.4f, %.4f], " +
                            "vdw=%.4f%n",
                    index++,
                    sphere.x(),
                    sphere.y(),
                    sphere.z(),
                    sphere.radius()));
        }

        writer.write("""
                show spheres, pocket_spheres
                color marine, pocket_spheres
                set sphere_transparency, 0.55, pocket_spheres
                """);
    }

    private static void writeDockingBox(
            BufferedWriter writer,
            PocketBox box) throws IOException {

        if (box == null) {
            return;
        }

        double minX = box.getMin().x();
        double minY = box.getMin().y();
        double minZ = box.getMin().z();

        double maxX = box.getMax().x();
        double maxY = box.getMax().y();
        double maxZ = box.getMax().z();

        writer.write("python\n");
        writer.write("from pymol.cgo import BEGIN, LINES, COLOR, VERTEX, END\n");
        writer.write("from pymol import cmd\n");
        writer.write("box = [BEGIN, LINES, COLOR, 1.0, 0.2, 0.2,\n");

        double[][] corners = {
                {minX, minY, minZ},
                {maxX, minY, minZ},
                {maxX, maxY, minZ},
                {minX, maxY, minZ},
                {minX, minY, maxZ},
                {maxX, minY, maxZ},
                {maxX, maxY, maxZ},
                {minX, maxY, maxZ}
        };

        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };

        for (int[] edge : edges) {
            double[] first = corners[edge[0]];
            double[] second = corners[edge[1]];

            writer.write(String.format(
                    Locale.US,
                    "VERTEX, %.4f, %.4f, %.4f, " +
                            "VERTEX, %.4f, %.4f, %.4f,%n",
                    first[0], first[1], first[2],
                    second[0], second[1], second[2]));
        }

        writer.write("END]\n");
        writer.write("cmd.load_cgo(box, 'docking_box')\n");
        writer.write("python end\n");
    }

    private static String pymolPath(Path path) {
        return path.toAbsolutePath()
                .toString()
                .replace("\\", "/");
    }
}
