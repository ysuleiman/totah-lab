package totah.lab.structure.io.pdbqt;

import java.util.List;
import org.junit.jupiter.api.Test;
import totah.lab.io.ProteinIO;
import totah.lab.pocket.Pocket;
import totah.lab.pocket.geometry.PocketGeometry;
import totah.lab.protein.Protein;
import totah.lab.protein.Residue;

import java.nio.file.Path;

public class PocketGeometryTest {

    @Test
    public void testCalculateBoundingBox() throws Exception {
        Path proteinPath = Path.of(getClass().getResource("/Q6UX53").toURI());

        Protein protein = ProteinIO.load(proteinPath);
        System.out.println(protein);

        Pocket pocket = protein.getPockets().get(0);
        System.out.println(pocket);
        Residue cys202 = null;
        for(Residue residue : pocket.getResidues()) {
            //System.out.println(residue);
            if("A".equals(residue.getChain()) && 202 == residue.getNumber()) {
                System.out.println(residue.getChain());
                cys202 = residue;
            }
        }

        List<Residue> neighbors = PocketGeometry.residueNeighbors(pocket, cys202, 4);
        for(Residue residue : neighbors) {
            System.out.println(residue);
        }
        System.out.println("===========");
        neighbors = PocketGeometry.residueNeighbors(protein.getStructure().getResidues(), cys202, 8);
        for(Residue residue : neighbors) {
            System.out.println(residue);
        }

        System.out.println("===========");
        for(Residue residue : protein.getStructure().getResidues()) {
            neighbors = PocketGeometry.residueNeighbors(protein.getStructure().getResidues(), residue, 8);
            System.out.println(residue);
            for(Residue n: neighbors) {
                System.out.println("\t"+n);
            }
        }

        System.out.println("===========");
        for(Residue residue : pocket.getResidues()) {
            neighbors = PocketGeometry.residueNeighbors(protein.getStructure().getResidues(), residue, 8);
            System.out.println(residue);
            for(Residue n: neighbors) {
                System.out.println("\t"+n);
            }
        }

    }

    @Test
    public void testCalculateBoundingBox1() throws Exception {
        Path proteinPath = Path.of(getClass().getResource("/Q6UX53").toURI());

        Protein protein = ProteinIO.load(proteinPath);
        System.out.println(protein);

        Pocket pocket = protein.getPockets().get(0);
        System.out.println(pocket);


        System.out.println("===========");
        for(Residue residue : pocket.getResidues()) {
            List<Residue> neighbors = PocketGeometry.residueNeighbors(protein.getStructure().getResidues(), residue, 8);
            System.out.println(residue);
            neighbors.forEach(r -> System.out.print(r.getName() + r.getNumber() + " "));
            System.out.println();
        }

        proteinPath = Path.of(getClass().getResource("/Q9H8H3").toURI());

        protein = ProteinIO.load(proteinPath);
        System.out.println(protein);

        for(Residue residue : pocket.getResidues()) {
            List<Residue> neighbors = PocketGeometry.residueNeighbors(protein.getStructure().getResidues(), residue, 8);
            System.out.println(residue);
            neighbors.forEach(r -> System.out.print(r.getName() + r.getNumber() + " "));
            System.out.println();
        }

    }

    @Test
    public void testCalculateBoundingBox2() throws Exception {
        Path proteinPath = Path.of(getClass().getResource("/Q9H8H3").toURI());

        Protein protein = ProteinIO.load(proteinPath);
        System.out.println(protein);
        Pocket pocket = protein.getPockets().get(0);
        //System.out.println("===========");

        for(Residue residue : pocket.getResidues()) {
            List<Residue> neighbors = PocketGeometry.residueNeighbors(protein.getStructure().getResidues(), residue, 8);
            System.out.println(residue);
            neighbors.forEach(r -> System.out.print(r.getName() + r.getNumber() + " "));
            System.out.println();
        }

    }
}
