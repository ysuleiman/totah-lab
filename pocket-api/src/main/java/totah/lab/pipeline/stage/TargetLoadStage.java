package totah.lab.pipeline.stage;

import totah.lab.io.StructureIO;
import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.pipeline.Stage;
import totah.lab.protein.Residue;
import totah.lab.protein.Structure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class TargetLoadStage implements Stage {


    @Override
    public void run(PipelineContext context) throws Exception {
        Objects.requireNonNull(context, "context is null");
        Path targetPath = context.require(ContextKeys.TARGET_PDB_PATH);
        validateTargetPath(targetPath);

        Structure structure = StructureIO.load(targetPath);
        List<Residue> residues = structure.getResidues();
        if (residues.isEmpty()) {
            throw new IllegalStateException("No residues loaded from " + targetPath);
        }
        context.put(ContextKeys.PROTEIN_RESIDUES, residues);
    }

    private void validateTargetPath(Path targetPath) throws IOException {
        Objects.requireNonNull(targetPath, "targetPath is null");
        if (!Files.exists(targetPath)) {
            throw new IOException("Target structure file does not exist: " + targetPath);
        }
        if (!Files.isRegularFile(targetPath)) {
            throw new IOException("Target structure path is not a regular file: " + targetPath);
        }
        if (!Files.isReadable(targetPath)) {
            throw new IOException("Target structure file is not readable: " + targetPath);
        }
    }
}
