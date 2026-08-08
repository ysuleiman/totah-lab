package totah.lab.web.assembly;

import totah.lab.gaia.pocket.Pocket;
import totah.lab.hermes.file.mmcif.AssemblyChain;
import totah.lab.hermes.file.mmcif.BoundComponentOccurrence;
import totah.lab.hermes.file.mmcif.EntryExperimentalMetadata;
import totah.lab.hermes.file.mmcif.StructureReference;

import java.io.IOException;
import java.util.List;

/** Source-boundary loader used before an assembly import transaction writes rows. */
public interface ExperimentalAssemblySourceLoader {
    ParsedAssembly load(ExperimentalAssemblyImportService.ImportRequest request)
            throws IOException;

    record ParsedAssembly(EntryExperimentalMetadata metadata,
            List<StructureReference> references, List<AssemblyChain> chains,
            List<BoundComponentOccurrence> components, List<Pocket> pockets) {
        public ParsedAssembly {
            references = List.copyOf(references);
            chains = List.copyOf(chains);
            components = List.copyOf(components);
            pockets = List.copyOf(pockets);
        }
    }
}
