package totah.lab.hermes.file.mmcif.reader;

import org.rcsb.cif.CifIO;
import org.rcsb.cif.schema.StandardSchemata;
import org.rcsb.cif.schema.mm.AtomSite;
import org.rcsb.cif.schema.mm.MmCifBlock;
import totah.lab.hermes.file.mmcif.AssemblyChain;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads distinct polymer chain instances from an assembly atom-site table. */
public final class MmcifAssemblyChainReader {
    public List<AssemblyChain> read(Path path) throws IOException {
        MmCifBlock block = CifIO.readFromPath(path).as(StandardSchemata.MMCIF)
                .getBlocks().getFirst();
        AtomSite atoms = block.getAtomSite();
        Map<Key, AssemblyChain> result = new LinkedHashMap<>();
        for (int row = 0; row < atoms.getRowCount(); row++) {
            if (!"ATOM".equals(atoms.getGroupPDB().get(row))) {
                continue;
            }
            int model = atoms.getPdbxPDBModelNum().isDefined()
                    ? atoms.getPdbxPDBModelNum().get(row) : 1;
            AssemblyChain chain = new AssemblyChain(
                    atoms.getLabelEntityId().get(row),
                    atoms.getLabelAsymId().get(row),
                    atoms.getAuthAsymId().get(row), model);
            result.putIfAbsent(new Key(chain.entityId(), chain.labelAsymId(),
                    chain.modelNumber()), chain);
        }
        return List.copyOf(result.values());
    }

    private record Key(String entityId, String labelAsymId, int model) {}
}
