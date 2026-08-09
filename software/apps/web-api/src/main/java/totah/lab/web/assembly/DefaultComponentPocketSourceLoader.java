package totah.lab.web.assembly;

import org.springframework.stereotype.Component;
import totah.lab.athena.pocket.component.PocketSphere;
import totah.lab.hermes.file.mmcif.BoundComponentOccurrence;
import totah.lab.hermes.file.mmcif.reader.MmcifNonPolymerReader;
import totah.lab.hermes.file.pocket.FPocketParser;
import totah.lab.hermes.file.pocket.FpocketAtomReader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public final class DefaultComponentPocketSourceLoader
        implements ComponentPocketSourceLoader {
    private final MmcifNonPolymerReader componentReader =
            new MmcifNonPolymerReader();
    private final FpocketAtomReader atomReader = new FpocketAtomReader();

    @Override
    public List<BoundComponentOccurrence> components(Path sourceMmcif,
            String pdbId, String assemblyId) throws IOException {
        return componentReader.read(sourceMmcif, pdbId,
                BoundComponentOccurrence.SourceKind.ASSEMBLY, assemblyId);
    }

    @Override
    public List<PocketSource> pockets(Path output) throws IOException {
        List<PocketSource> result = new ArrayList<>();
        for (var pocket : FPocketParser.parse(output)) {
            int number = Integer.parseInt(pocket.id().value());
            var atoms = atomReader.read(output.resolve("pockets")
                    .resolve("pocket" + number + "_atm.cif"));
            var spheres = pocket.alphaSphereSet().stream()
                    .flatMap(set -> set.spheres().stream())
                    .map(sphere -> new PocketSphere(sphere.center(), sphere.radius()))
                    .toList();
            result.add(new PocketSource(number, atoms, spheres));
        }
        return List.copyOf(result);
    }
}
