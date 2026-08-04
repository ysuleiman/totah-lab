package totah.lab;

import totah.lab.hermes.file.pocket.FPocketParser;
import totah.lab.gaia.pocket.Pocket;


import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

public class AlphaFoldImporter {


    public AlphaFoldImporter(){}

    public void importDirectory(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".pdb.gz"))
                    .forEach(this::importStructure);
        }
    }

    private void importStructure(Path compressedFile) {
        try {
            Path pocketRoot = compressedFile.getParent().getParent()
                    .resolve("UP000005640_9606_HUMAN_v6_pockets")
                    .resolve("fpocket-human");

            String proteinFileName = compressedFile.getFileName().toString();
            String id = proteinFileName.substring(
                    0,
                    proteinFileName.indexOf(".pdb.gz"));
            System.out.println(compressedFile);
            System.out.println(pocketRoot);
            Path runDirectory = runDirectory(pocketRoot, id);
            System.out.println(runDirectory);
            String accession = accession(compressedFile);
            System.out.println(accession);



            //Path temporaryPdb =
                    //Files.createTempFile(accession, ".pdb");
            //decompress(compressedFile, temporaryPdb);


            //Files.deleteIfExists(temporaryPdb);
        } catch (Exception e) {
            System.err.println(
                    "Failed: " + compressedFile.getFileName());
            e.printStackTrace();
        }
    }

    private static void decompress(Path source, Path target)
            throws IOException {

        try (InputStream in =
                     new GZIPInputStream(
                             Files.newInputStream(source))) {

            Files.copy(
                    in,
                    target,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private static String accession(Path file) {
        String name = file.getFileName().toString();
        // AF-Q6UX53-F1-model_v6.pdb.gz
        int first = name.indexOf('-');
        int second = name.indexOf('-', first + 1);
        return name.substring(first + 1, second);
    }


    static Path runDirectory(Path pocketRoot, String id) throws IOException {
        return Files.list(pocketRoot)
                        .filter(Files::isDirectory)
                        .filter(p -> p.getFileName()
                                .toString()
                                .startsWith(id))
                        .findFirst()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "No fpocket directory for " + id));
    }
    public static void main(String[] args) throws Exception {

        new AlphaFoldImporter().
                importDirectory(Path.of("/Users/yazan/artifacts/UP000005640_9606_HUMAN_v6"));
    }
}
