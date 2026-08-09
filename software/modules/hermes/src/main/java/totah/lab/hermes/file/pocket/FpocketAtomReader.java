package totah.lab.hermes.file.pocket;

import org.rcsb.cif.CifIO;
import org.rcsb.cif.schema.StandardSchemata;
import org.rcsb.cif.schema.mm.AtomSite;
import totah.lab.gaia.geometry.Point3D;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reads the mmCIF pocket atom files emitted by fpocket 4.0. */
public final class FpocketAtomReader {
    public List<FpocketAtomObservation> read(Path path) throws IOException {
        AtomSite atoms = CifIO.readFromPath(path).as(StandardSchemata.MMCIF)
                .getBlocks().getFirst().getAtomSite();
        List<FpocketAtomObservation> result = new ArrayList<>();
        for (int row = 0; row < atoms.getRowCount(); row++) {
            String insertion = atoms.getPdbxPDBInsCode().isDefined()
                    ? normalize(atoms.getPdbxPDBInsCode().get(row)) : null;
            result.add(new FpocketAtomObservation(
                    Integer.toString(atoms.getId().get(row)),
                    atoms.getGroupPDB().get(row),
                    atoms.getLabelAtomId().get(row), atoms.getTypeSymbol().get(row),
                    atoms.getAuthAsymId().get(row),
                    atoms.getAuthSeqId().get(row), insertion,
                    atoms.getLabelCompId().get(row), new Point3D(
                            atoms.getCartnX().get(row), atoms.getCartnY().get(row),
                            atoms.getCartnZ().get(row))));
        }
        return List.copyOf(result);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() || value.equals(".")
                || value.equals("?") ? null : value;
    }
}
