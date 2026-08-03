package totah.lab.web.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Pairs AlphaFold {@code .pdb.gz} files with their fpocket output
 * directories across one or more fpocket roots. Pure filesystem logic, no
 * database access, so it is unit-testable in isolation.
 *
 * Pairing rule: for {@code AF-<ACC>-F1-model_v<N>.pdb.gz} the base name is
 * {@code AF-<ACC>-F1-model_v<N>}. A run directory in a fpocket root
 * matches when its name starts with {@code <base>-} (the random suffix
 * produced by the fpocket batch runner). It is complete only when
 * {@code <runDir>/<base>_out/<base>_info.txt} is a regular file and
 * {@code <runDir>/<base>_out/pockets/} is a directory — the same rule as
 * ParallelFpocketRunner. The first complete match across the roots (in
 * order) wins.
 */
final class AlphaFoldBulkImportPlanner {

    static final String PDB_SUFFIX = ".pdb.gz";

    private AlphaFoldBulkImportPlanner() {
    }

    record StructurePair(
            Path compressedPdb,
            Path fpocketOutDirectory
    ) {
    }

    record Plan(
            int totalPdbFiles,
            List<StructurePair> pairs,
            List<Path> missingFpocket,
            List<Path> incompleteRunDirectories,
            List<Path> pairedInMultipleRoots
    ) {
    }

    static Plan plan(Path pdbDirectory, List<Path> fpocketRoots)
            throws IOException {

        List<Path> pdbFiles;
        try (Stream<Path> stream = Files.list(pdbDirectory)) {
            pdbFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName()
                            .toString()
                            .endsWith(PDB_SUFFIX))
                    .sorted()
                    .toList();
        }

        List<StructurePair> pairs = new ArrayList<>();
        List<Path> missingFpocket = new ArrayList<>();
        List<Path> incompleteRunDirectories = new ArrayList<>();
        List<Path> pairedInMultipleRoots = new ArrayList<>();

        for (Path pdbFile : pdbFiles) {
            String filename = pdbFile.getFileName().toString();
            String baseName = filename.substring(
                    0,
                    filename.length() - PDB_SUFFIX.length()
            );

            List<Path> completeOutDirectories = new ArrayList<>();

            for (Path root : fpocketRoots) {
                if (!Files.isDirectory(root)) {
                    continue;
                }
                try (Stream<Path> stream = Files.list(root)) {
                    for (Path runDirectory : stream
                            .filter(Files::isDirectory)
                            .filter(path -> path.getFileName()
                                    .toString()
                                    .startsWith(baseName + "-"))
                            .toList()) {

                        Path outDirectory =
                                runDirectory.resolve(baseName + "_out");
                        if (isComplete(outDirectory, baseName)) {
                            completeOutDirectories.add(outDirectory);
                        } else {
                            incompleteRunDirectories.add(runDirectory);
                        }
                    }
                }
            }

            if (completeOutDirectories.isEmpty()) {
                missingFpocket.add(pdbFile);
            } else {
                pairs.add(new StructurePair(
                        pdbFile,
                        completeOutDirectories.get(0)
                ));
                if (completeOutDirectories.size() > 1) {
                    pairedInMultipleRoots.add(pdbFile);
                }
            }
        }

        return new Plan(
                pdbFiles.size(),
                List.copyOf(pairs),
                List.copyOf(missingFpocket),
                List.copyOf(incompleteRunDirectories),
                List.copyOf(pairedInMultipleRoots)
        );
    }

    private static boolean isComplete(Path outDirectory, String baseName) {
        return Files.isRegularFile(
                outDirectory.resolve(baseName + "_info.txt"))
                && Files.isDirectory(outDirectory.resolve("pockets"));
    }
}
