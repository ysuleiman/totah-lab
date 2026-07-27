package totah.lab.pocket.fpocket;

import org.junit.jupiter.api.Test;
import totah.lab.pocket.*;
import totah.lab.fpocket.FPocketParser;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class ReaderTest {

    @Test
    public void readFolder() throws  Exception{
        Path folder = Paths.get(
                Objects.requireNonNull(
                                getClass().getResource("/AF-Q6UX53-F1-model_v6_out"))
                        .toURI());
        List<Pocket>pockets = FPocketParser.parse(folder);
        assertEquals(15, pockets.size());
        Pocket pocket = pockets.get(0);
        assertEquals(1,pocket.getId());
        assertNotNull(pocket.getResidues());
        assertEquals(0.001, pocket.getDruggabilityScore());
        AlphaSphereGeometry geometry = pocket.getGeometry();

        assertNotNull(geometry);
        assertEquals(707.754,pocket.getVolume());
        assertEquals(3.688,pocket.getVolumeScore());
        assertEquals(15.965,geometry.getCentOfMassAlphaSphereMaxDist());
        ChemicalProperties chemicalProperties = pocket.getChemistry();
        assertNotNull(chemicalProperties);
        Sasa sasa = pocket.getSasa();

        List<Residue>residues=pocket.getResidues();
        assertEquals(16, residues.size());
        Residue first = residues.get(0);
        assertEquals("SER", first.getName());
        assertEquals("A", first.getChainId());
        assertEquals("110", first.getPosition());

        assertFalse(first.getAtoms().isEmpty());
        Atom atom = first.getAtoms().get(0);

        assertEquals("OG", atom.getName());
        assertEquals(-5.021, atom.getX());
        assertEquals(11.771,atom.getY());
        assertEquals(8.226,atom.getZ());
        assertEquals("O", atom.getElement());
    }
    @Test
    public void readInfoFile() throws  Exception{

        Path infoFile = Paths.get(
                Objects.requireNonNull(
                                getClass().getResource("/AF-Q6UX53-F1-model_v6_out/AF-Q6UX53-F1-model_v6_info.txt"))
                        .toURI());
        List<Pocket> pockets = FPocketParser.parseInfoFile(infoFile);
        assertNotNull(pockets);
        assertEquals(15, pockets.size());
        Pocket first = pockets.get(0);
        assertEquals(1,first.getId());
        assertTrue(first.getResidues().isEmpty());
        assertEquals(0.001, first.getDruggabilityScore());
        AlphaSphereGeometry geometry = first.getGeometry();

        assertNotNull(geometry);
        assertEquals(707.754,first.getVolume());
        assertEquals(3.688,first.getVolumeScore());
        assertEquals(15.965,first.getGeometry().getCentOfMassAlphaSphereMaxDist());
        ChemicalProperties chemicalProperties = first.getChemistry();
        assertNotNull(chemicalProperties);
        System.out.println(chemicalProperties);
        Sasa sasa = first.getSasa();
        System.out.println(sasa);
    }

    @Test
    public void readResidues() throws  Exception{
        Path p1File = Paths.get(
                Objects.requireNonNull(
                                getClass().getResource("/AF-Q6UX53-F1-model_v6_out/pockets/pocket1_atm.pdb"))
                        .toURI());
        List<Residue> residues = FPocketParser.readResidues(p1File);
        assertNotNull(residues);
        assertEquals(16, residues.size());
        Residue first = residues.get(0);
        assertEquals("SER", first.getName());
        assertEquals("A", first.getChainId());
        assertEquals("110", first.getPosition());

        assertFalse(first.getAtoms().isEmpty());
        Atom atom = first.getAtoms().get(0);

        assertEquals("OG", atom.getName());
        assertEquals(-5.021, atom.getX());
        assertEquals(11.771,atom.getY());
        assertEquals(8.226,atom.getZ());
        assertEquals("O", atom.getElement());
    }
    @Test
    public void readAlphaSpheres() throws  Exception{
        Path p1File = Paths.get(
                Objects.requireNonNull(
                                getClass().getResource("/AF-Q6UX53-F1-model_v6_out/pockets/pocket1_vert.pqr"))
                        .toURI());
        List<AlphaSphere> list = FPocketParser.readAlphaSpheres(p1File);
        assertNotNull(list);
        assertFalse(list.isEmpty());
        assertEquals(78, list.size());
        AlphaSphere first = list.get(0);
        assertEquals(1, first.getId());
        assertEquals(-3.651, first.getX());
        assertEquals(14.617, first.getY());
        assertEquals(4.983, first.getZ());
        assertEquals(0, first.getRadius());
    }
}
