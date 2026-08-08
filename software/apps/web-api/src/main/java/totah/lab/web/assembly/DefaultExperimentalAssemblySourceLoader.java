package totah.lab.web.assembly;

import org.springframework.stereotype.Component;
import totah.lab.hermes.file.mmcif.BoundComponentOccurrence;
import totah.lab.hermes.file.mmcif.reader.MmcifAssemblyChainReader;
import totah.lab.hermes.file.mmcif.reader.MmcifEntryExperimentalMetadataReader;
import totah.lab.hermes.file.mmcif.reader.MmcifNonPolymerReader;
import totah.lab.hermes.file.mmcif.reader.MmcifStructureReferenceReader;
import totah.lab.hermes.file.pocket.FPocketParser;

import java.io.IOException;

@Component
public final class DefaultExperimentalAssemblySourceLoader
        implements ExperimentalAssemblySourceLoader {
    private final MmcifStructureReferenceReader referenceReader =
            new MmcifStructureReferenceReader();
    private final MmcifAssemblyChainReader chainReader =
            new MmcifAssemblyChainReader();
    private final MmcifEntryExperimentalMetadataReader metadataReader =
            new MmcifEntryExperimentalMetadataReader();
    private final MmcifNonPolymerReader componentReader =
            new MmcifNonPolymerReader();

    @Override
    public ParsedAssembly load(ExperimentalAssemblyImportService.ImportRequest request)
            throws IOException {
        return new ParsedAssembly(metadataReader.read(request.entryMmcif()),
                referenceReader.read(request.entryMmcif()),
                chainReader.read(request.assemblyMmcif()),
                componentReader.read(request.assemblyMmcif(), request.pdbId(),
                        BoundComponentOccurrence.SourceKind.ASSEMBLY,
                        request.assemblyId()),
                FPocketParser.parse(request.fpocketOutput()));
    }
}
