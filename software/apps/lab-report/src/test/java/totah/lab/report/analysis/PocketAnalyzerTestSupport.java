package totah.lab.report.analysis;

import totah.lab.pocket.Pocket;
import totah.lab.pocket.PocketSource;
import totah.lab.pocket.ResidueRef;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

final class PocketAnalyzerTestSupport {

    private PocketAnalyzerTestSupport() {
    }

    static Residue residue(
            String name,
            int number,
            Point3D... positions
    ) {
        List<Atom> atoms = java.util.stream.IntStream
                .range(0, positions.length)
                .mapToObj(index -> Atom.builder()
                        .name(index == 0 ? "CA" : "X" + index)
                        .position(positions[index])
                        .element(Element.C)
                        .build())
                .toList();
        return Residue.builder()
                .name(name)
                .number(number)
                .atoms(atoms)
                .build();
    }

    static Structure structure(Residue... residues) {
        return new Structure(List.of(
                new Chain("A", Arrays.asList(residues))));
    }

    static Pocket pocket(
            Map<String, Object> attributes,
            Residue... residues
    ) {
        List<ResidueRef> references = Arrays.stream(residues)
                .map(residue -> new ResidueRef(
                        "A",
                        residue.getNumber(),
                        residue.getName()))
                .toList();
        return Pocket.builder()
                .id(1)
                .name("generic-pocket")
                .source(PocketSource.FPOCKET)
                .residueRefs(references)
                .attributes(attributes)
                .build();
    }
}
