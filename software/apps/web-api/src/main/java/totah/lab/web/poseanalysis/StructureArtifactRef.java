package totah.lab.web.poseanalysis;

/**
 * Identity of one structure-bearing artifact that coordinates depend
 * on: the artifact id (chemflow artifact-store UUID or docking
 * artifact row id), the protein accession and structure source of the
 * model it represents, the model version when it can be determined
 * (for example {@code v6} from an AlphaFold accession), and the
 * SHA-256 of the artifact file on disk (computed at load time — the
 * stores do not record hashes).
 */
public record StructureArtifactRef(
        String artifactId,
        String accession,
        String source,
        String modelVersion,
        String sha256
) {
}
