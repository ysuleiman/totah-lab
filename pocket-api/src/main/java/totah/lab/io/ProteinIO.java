package totah.lab.io;

import totah.lab.protein.Protein;
import totah.lab.protein.Structure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class ProteinIO {

    //private static final Pattern UNIPROT_ACCESSION =
            //Pattern.compile("[A-NR-Z][0-9][A-Z0-9]{3}[0-9]([.-].*)?");

    private ProteinIO(){}


    public static Protein load(Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory");
        if (!Files.isDirectory(directory)) {
            throw new IOException("Target directory not found: " + directory);
        }
        totah.lab.protein.TargetId targetId = totah.lab.protein.TargetId.of(directory.getFileName().toString());
        Path structurePath = findPdbFile(directory, targetId.uniProtId())
                .orElseThrow(() -> new IOException("Structure not found"));
        Structure structure = StructureIO.load(structurePath);
        Protein protein = new Protein(targetId, structure);
        protein.addPockets(PocketIO.load(directory));
        return protein;
    }

    private static Optional<Path> findPdbFile(Path directory, String targetId) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase();
                        return name.endsWith(".pdb") || name.endsWith(".cif") || name.endsWith(".mmcif");
                    })
                    // Prefer an exact <targetId> name match, then .pdb over .cif
                    .min(Comparator.comparingInt(path -> preference(path, targetId)));
        }
    }

    private static int preference(Path path, String targetId) {
        String name = path.getFileName().toString().toLowerCase();
        String base = name.substring(0, name.lastIndexOf('.'));
        boolean exactMatch = base.equals(targetId.toLowerCase());
        boolean pdb = name.endsWith(".pdb");
        if (exactMatch) return pdb ? 0 : 1;
        return pdb ? 2 : 3;
    }
}
