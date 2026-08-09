package totah.lab.hermes.file.mmcif.reader;

import org.rcsb.cif.CifIO;
import org.rcsb.cif.schema.StandardSchemata;
import org.rcsb.cif.schema.mm.AtomSite;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.hermes.file.mmcif.ResidueCoordinateObservation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Reads CA and heavy side-chain centroids from authoritative mmCIF atoms. */
public final class MmcifResidueCoordinateReader {
    private static final Set<String> BACKBONE =
            Set.of("N", "CA", "C", "O", "OXT");

    public List<ResidueCoordinateObservation> read(Path path)
            throws IOException {
        AtomSite atoms = CifIO.readFromPath(path).as(StandardSchemata.MMCIF)
                .getBlocks().getFirst().getAtomSite();
        Map<Key, ResidueAtoms> residues = new LinkedHashMap<>();
        for (int row = 0; row < atoms.getRowCount(); row++) {
            if (!"ATOM".equalsIgnoreCase(atoms.getGroupPDB().get(row))) continue;
            Key key = new Key(atoms.getAuthAsymId().get(row),
                    atoms.getAuthSeqId().get(row),
                    atoms.getPdbxPDBInsCode().isDefined()
                            ? normalize(atoms.getPdbxPDBInsCode().get(row)) : "",
                    atoms.getLabelCompId().get(row));
            String atomName = atoms.getLabelAtomId().get(row).trim();
            String element = atoms.getTypeSymbol().get(row).trim();
            Point3D point = new Point3D(atoms.getCartnX().get(row),
                    atoms.getCartnY().get(row), atoms.getCartnZ().get(row));
            ResidueAtoms value = residues.computeIfAbsent(key,
                    ignored -> new ResidueAtoms());
            value.atoms.putIfAbsent(atomName, new AtomValue(element, point));
        }
        List<ResidueCoordinateObservation> result = new ArrayList<>();
        residues.forEach((key, value) -> {
            AtomValue ca = value.atoms.get("CA");
            if (ca == null) return;
            List<Point3D> side = value.atoms.entrySet().stream()
                    .filter(entry -> !BACKBONE.contains(entry.getKey()))
                    .filter(entry -> !"H".equalsIgnoreCase(
                            entry.getValue().element()))
                    .map(entry -> entry.getValue().point()).toList();
            result.add(new ResidueCoordinateObservation(key.chain(),
                    key.sequence(), key.insertion(), key.residue(), ca.point(),
                    side.isEmpty() ? Optional.empty()
                            : Optional.of(centroid(side))));
        });
        return List.copyOf(result);
    }

    private static Point3D centroid(List<Point3D> points) {
        return new Point3D(points.stream().mapToDouble(Point3D::x).average()
                .orElseThrow(), points.stream().mapToDouble(Point3D::y)
                .average().orElseThrow(), points.stream().mapToDouble(Point3D::z)
                .average().orElseThrow());
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() || value.equals(".")
                || value.equals("?") ? "" : value;
    }

    private record Key(String chain, int sequence, String insertion,
            String residue) {}
    private record AtomValue(String element, Point3D point) {}
    private static final class ResidueAtoms {
        private final Map<String, AtomValue> atoms = new LinkedHashMap<>();
    }
}
