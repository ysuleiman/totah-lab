package totah.lab.web.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.AlphaSphereSet;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketMetricType;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.pocket.FPocketParser;
import totah.lab.hermes.file.pdb.reader.PdbReader;
import totah.lab.hermes.file.api.StructureReader;
import totah.lab.web.persistence.ArtifactEntity;
import totah.lab.web.persistence.ArtifactRepository;
import totah.lab.web.persistence.PipelineRunEntity;
import totah.lab.web.persistence.PipelineRunRepository;
import totah.lab.web.persistence.PocketAlphaSphereEntity;
import totah.lab.web.persistence.PocketAtomEntity;
import totah.lab.web.persistence.PocketEntity;
import totah.lab.web.persistence.PocketRepository;
import totah.lab.web.persistence.PocketResidueEntity;
import totah.lab.web.persistence.ReceptorEntity;
import totah.lab.web.persistence.ReceptorRepository;
import totah.lab.web.persistence.ResidueEntity;
import totah.lab.web.persistence.ResidueRepository;
import totah.lab.web.persistence.StructureEntity;
import totah.lab.web.persistence.StructureRepository;
import totah.lab.web.persistence.TargetEntity;
import totah.lab.web.persistence.TargetRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

/**
 * Imports one AlphaFold structure (.pdb.gz) together with its existing
 * fpocket output directory into the docking schema.
 *
 * This service never runs fpocket and never traverses directories beyond
 * the fpocket pockets/ files named by the parsed info file. Everything is
 * parsed and validated before the first database write, and the whole
 * import runs in one transaction so a failure leaves no partial rows.
 */
@Service
public class AlphaFoldPocketImportService {

    public static final String STRUCTURE_SOURCE = "ALPHAFOLD";
    public static final String PREPARATION_STATE = "RAW";
    public static final String STRUCTURE_ARTIFACT_LABEL = "RAW_PDB_FILE";
    public static final String POCKET_ARTIFACT_LABEL = "FPOCKET_POCKET";

    private static final Logger LOG =
            LoggerFactory.getLogger(AlphaFoldPocketImportService.class);

    private static final String DEFAULT_ORGANISM = "Homo sapiens";
    private static final Pattern ALPHAFOLD_FILENAME = Pattern.compile(
            "^AF-([A-Za-z0-9]+)-F(\\d+)-model_v(\\d+)$");

    private final ReceptorRepository receptorRepository;
    private final StructureRepository structureRepository;
    private final ResidueRepository residueRepository;
    private final PocketRepository pocketRepository;
    private final ArtifactRepository artifactRepository;
    private final TargetRepository targetRepository;
    private final PipelineRunRepository pipelineRunRepository;
    private final PocketShapeDescriptorService shapeDescriptorService;

    /*
     * fpocket pockets with fewer residues than this are garbage (over half
     * the historical FPOCKET rows) and are skipped entirely: no pocket,
     * artifact, membership or atom rows. Configurable via
     * totah.import.min-pocket-residues.
     */
    private final int minPocketResidues;

    private final StructureReader structureReader =
            new PdbReader();

    public AlphaFoldPocketImportService(
            ReceptorRepository receptorRepository,
            StructureRepository structureRepository,
            ResidueRepository residueRepository,
            PocketRepository pocketRepository,
            ArtifactRepository artifactRepository,
            TargetRepository targetRepository,
            PipelineRunRepository pipelineRunRepository,
            PocketShapeDescriptorService shapeDescriptorService,
            @Value("${totah.import.min-pocket-residues:1}")
            int minPocketResidues
    ) {
        this.receptorRepository =
                Objects.requireNonNull(receptorRepository);
        this.structureRepository =
                Objects.requireNonNull(structureRepository);
        this.residueRepository =
                Objects.requireNonNull(residueRepository);
        this.pocketRepository = Objects.requireNonNull(pocketRepository);
        this.artifactRepository =
                Objects.requireNonNull(artifactRepository);
        this.targetRepository = Objects.requireNonNull(targetRepository);
        this.pipelineRunRepository =
                Objects.requireNonNull(pipelineRunRepository);
        this.shapeDescriptorService =
                Objects.requireNonNull(shapeDescriptorService);
        if (minPocketResidues < 1) {
            throw new IllegalArgumentException(
                    "minPocketResidues must be at least 1: "
                            + minPocketResidues
            );
        }
        this.minPocketResidues = minPocketResidues;
    }

    /**
     * Imports one AlphaFold structure and its existing fpocket output.
     *
     * The fpocket output directory (the AF-..._out directory holding the
     * _info.txt file and the pockets/ subdirectory) must already exist.
     */
    @Transactional(rollbackFor = IOException.class)
    public ImportResult importStructure(
            Path compressedPdb,
            Path fpocketOutputDirectory
    ) throws IOException {

        Objects.requireNonNull(compressedPdb, "compressedPdb");
        Objects.requireNonNull(
                fpocketOutputDirectory,
                "fpocketOutputDirectory"
        );

        ParsedImport parsed =
                parseAndValidate(compressedPdb, fpocketOutputDirectory);

        AlphaFoldIdentity identity = parsed.identity();
        Structure parsedStructure = parsed.structure();
        List<Pocket> parsedPockets = parsed.pockets();
        Map<Integer, Map<ResidueKey, List<Atom>>> pocketAtoms =
                parsed.pocketAtoms();

        ReceptorEntity receptor = findOrCreateReceptor(identity);
        TargetEntity target = findOrCreateTarget(identity);
        PipelineRunEntity pipelineRun = findOrCreatePipelineRun(target);

        ArtifactEntity structureArtifact = findOrCreateArtifact(
                compressedPdb.getFileName().toString(),
                STRUCTURE_ARTIFACT_LABEL,
                compressedPdb.toString(),
                target,
                pipelineRun
        );

        StructureEntity structure = findOrCreateStructure(
                receptor,
                structureArtifact,
                identity,
                parsedStructure
        );

        Map<ResidueKey, ResidueEntity> residues =
                synchronizeResidues(structure, parsedStructure);

        int importedPockets = 0;
        int skippedPockets = 0;
        int importedPocketResidues = 0;
        int importedPocketAtoms = 0;

        for (Pocket pocket : parsedPockets) {
            int pocketNumber = pocketNumber(pocket);

            if (pocket.residues().size() < minPocketResidues) {
                LOG.info("Skipping fpocket pocket {} of {}: {} residues "
                                + "below minimum {}",
                        pocketNumber,
                        identity.structureAccession(),
                        pocket.residues().size(),
                        minPocketResidues);
                skippedPockets++;
                continue;
            }

            PocketImportCounts counts = importPocket(
                    receptor,
                    structure,
                    target,
                    pipelineRun,
                    pocket,
                    pocketNumber,
                    fpocketOutputDirectory,
                    residues,
                    pocketAtoms.getOrDefault(pocketNumber, Map.of())
            );

            importedPockets++;
            importedPocketResidues += counts.residues();
            importedPocketAtoms += counts.atoms();
        }

        return new ImportResult(
                receptor.getId(),
                structure.getId(),
                residues.size(),
                importedPockets,
                skippedPockets,
                importedPocketResidues,
                importedPocketAtoms
        );
    }

    /**
     * Parses and validates the AlphaFold PDB and the fpocket output
     * without touching the database. Package-private so the bulk import
     * runner can reuse exactly the same validation for its dry run;
     * {@link #importStructure} performs this phase before any DB write.
     */
    ParsedImport parseAndValidate(
            Path compressedPdb,
            Path fpocketOutputDirectory
    ) throws IOException {

        Objects.requireNonNull(compressedPdb, "compressedPdb");
        Objects.requireNonNull(
                fpocketOutputDirectory,
                "fpocketOutputDirectory"
        );

        AlphaFoldIdentity identity = AlphaFoldIdentity.from(compressedPdb);

        Structure parsedStructure = readCompressedPdb(compressedPdb);
        List<Pocket> parsedPockets =
                FPocketParser.parse(fpocketOutputDirectory);
        Map<Integer, Map<ResidueKey, List<Atom>>> pocketAtoms =
                readAllPocketAtoms(fpocketOutputDirectory, parsedPockets);

        validate(parsedStructure, parsedPockets, identity);

        return new ParsedImport(
                identity,
                parsedStructure,
                parsedPockets,
                pocketAtoms
        );
    }

    /*
     * PdbReader works on plain .pdb files, so the gzip stream
     * is decompressed to a temporary file first (PDB parser, not mmCIF).
     */
    private Structure readCompressedPdb(Path compressedPdb)
            throws IOException {

        Path temporary = Files.createTempFile("alphafold-import-", ".pdb");
        try {
            try (InputStream in = new GZIPInputStream(
                    Files.newInputStream(compressedPdb));
                 OutputStream out = Files.newOutputStream(temporary)) {
                in.transferTo(out);
            } catch (IOException exception) {
                throw new IOException(
                        "Cannot decompress AlphaFold PDB: "
                                + compressedPdb,
                        exception
                );
            }
            return structureReader.read(temporary);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /*
     * FPocketParser exposes pocket residues but not their atoms, so the
     * pocketN_atm.pdb files are parsed with the regular PDB reader.
     */
    private Map<Integer, Map<ResidueKey, List<Atom>>> readAllPocketAtoms(
            Path fpocketOutputDirectory,
            List<Pocket> pockets
    ) throws IOException {

        Map<Integer, Map<ResidueKey, List<Atom>>> atomsByPocketNumber =
                new LinkedHashMap<>();

        for (Pocket pocket : pockets) {
            int pocketNumber = pocketNumber(pocket);
            Path atomFile = fpocketOutputDirectory
                    .resolve("pockets")
                    .resolve("pocket" + pocketNumber + "_atm.pdb");

            Structure pocketStructure = structureReader.read(atomFile);

            Map<ResidueKey, List<Atom>> atomsByResidue =
                    new LinkedHashMap<>();
            for (Chain chain : pocketStructure.getChains()) {
                for (Residue residue : chain.residues()) {
                    atomsByResidue.put(
                            key(chain.id(), residue),
                            residue.getAtoms()
                    );
                }
            }
            atomsByPocketNumber.put(pocketNumber, atomsByResidue);
        }

        return atomsByPocketNumber;
    }

    private void validate(
            Structure structure,
            List<Pocket> pockets,
            AlphaFoldIdentity identity
    ) {
        if (structure.getChains().isEmpty()) {
            throw new IllegalArgumentException(
                    "AlphaFold structure contains no chains: "
                            + identity.structureAccession()
            );
        }

        /*
         * An empty pocket list is valid: fpocket legitimately reports zero
         * pockets for tiny proteins/peptides. Such structures are imported
         * (receptor, structure, residues) with no pocket rows.
         */
        Set<Integer> pocketNumbers = new HashSet<>();
        for (Pocket pocket : pockets) {
            if (!pocketNumbers.add(pocketNumber(pocket))) {
                throw new IllegalArgumentException(
                        "Duplicate fpocket pocket number "
                                + pocket.id().value()
                                + " for "
                                + identity.structureAccession()
                );
            }
        }
    }

    private ReceptorEntity findOrCreateReceptor(
            AlphaFoldIdentity identity
    ) {
        return receptorRepository
                .findByUniProtId(identity.uniprotAccession())
                .orElseGet(() -> {
                    ReceptorEntity receptor = new ReceptorEntity();

                    receptor.setUniProtId(identity.uniprotAccession());
                    receptor.setTargetName(identity.uniprotAccession());
                    receptor.setOrganism(DEFAULT_ORGANISM);

                    /*
                     * proteinName and geneName can be populated later from
                     * UniProt; they are not invented from the filename.
                     */
                    return receptorRepository.save(receptor);
                });
    }

    private TargetEntity findOrCreateTarget(AlphaFoldIdentity identity) {
        return targetRepository
                .findByUniProtId(identity.uniprotAccession())
                .orElseGet(() -> targetRepository.save(new TargetEntity(
                        identity.uniprotAccession(),
                        identity.uniprotAccession()
                )));
    }

    /*
     * One FINISHED pipeline run per target, mirroring
     * tools/scripts/generate_docking_resource_import.mjs: reuse the run of
     * any artifact already stored for the target, create one otherwise.
     */
    private PipelineRunEntity findOrCreatePipelineRun(TargetEntity target) {
        return artifactRepository
                .findFirstByTargetId(target.getId())
                .map(ArtifactEntity::getPipelineRun)
                .orElseGet(() -> pipelineRunRepository.save(
                        PipelineRunEntity.finishedNow()
                ));
    }

    private ArtifactEntity findOrCreateArtifact(
            String filename,
            String label,
            String storageLocation,
            TargetEntity target,
            PipelineRunEntity pipelineRun
    ) {
        return artifactRepository
                .findByStorageLocationAndTargetId(
                        storageLocation,
                        target.getId()
                )
                .orElseGet(() -> artifactRepository.save(
                        new ArtifactEntity(
                                filename,
                                label,
                                storageLocation,
                                pipelineRun,
                                target
                        )
                ));
    }

    private StructureEntity findOrCreateStructure(
            ReceptorEntity receptor,
            ArtifactEntity artifact,
            AlphaFoldIdentity identity,
            Structure parsedStructure
    ) {
        return structureRepository
                .findBySourceAndSourceAccession(
                        STRUCTURE_SOURCE,
                        identity.structureAccession()
                )
                .map(existing -> {
                    if (!Objects.equals(
                            existing.getReceptor().getId(),
                            receptor.getId()
                    )) {
                        throw new IllegalStateException(
                                ("AlphaFold structure %s already belongs to"
                                        + " another receptor")
                                        .formatted(
                                                identity.structureAccession()
                                        )
                        );
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    StructureEntity structure = new StructureEntity();

                    structure.setReceptor(receptor);
                    structure.setArtifact(artifact);
                    structure.setSource(STRUCTURE_SOURCE);
                    structure.setSourceAccession(
                            identity.structureAccession()
                    );
                    structure.setPreparationState(PREPARATION_STATE);
                    structure.setModelNumber(identity.modelVersion());
                    if (parsedStructure.getChains().size() == 1) {
                        structure.setChain(
                                parsedStructure.getChains()
                                        .get(0)
                                        .id()
                        );
                    }

                    return structureRepository.save(structure);
                });
    }

    private Map<ResidueKey, ResidueEntity> synchronizeResidues(
            StructureEntity structure,
            Structure parsedStructure
    ) {
        Map<ResidueKey, ResidueEntity> residues =
                residueRepository
                        .findAllByStructureId(structure.getId())
                        .stream()
                        .collect(Collectors.toMap(
                                this::key,
                                Function.identity(),
                                (first, duplicate) -> {
                                    throw new IllegalStateException(
                                            "Duplicate residue in database:"
                                                    + " " + key(first)
                                    );
                                },
                                LinkedHashMap::new
                        ));

        for (Chain chain : parsedStructure.getChains()) {
            for (Residue residue : chain.residues()) {
                ResidueKey key = key(chain.id(), residue);

                ResidueEntity entity = residues.get(key);
                if (entity == null) {
                    entity = residueRepository.save(new ResidueEntity(
                            structure,
                            chain.id(),
                            residue.getNumber(),
                            normalizeInsertionCode(
                                    residue.getInsertionCode()
                            ),
                            residue.getName()
                    ));
                    residues.put(key, entity);
                } else {
                    verifyResidueIdentity(entity, residue);
                }
            }
        }

        return residues;
    }

    private PocketImportCounts importPocket(
            ReceptorEntity receptor,
            StructureEntity structure,
            TargetEntity target,
            PipelineRunEntity pipelineRun,
            Pocket pocket,
            int pocketNumber,
            Path fpocketOutputDirectory,
            Map<ResidueKey, ResidueEntity> residues,
            Map<ResidueKey, List<Atom>> atomsByResidue
    ) {
        String pocketFileName = "pocket" + pocketNumber + "_atm.pdb";
        Path pocketFile = fpocketOutputDirectory
                .resolve("pockets")
                .resolve(pocketFileName);

        ArtifactEntity pocketArtifact = findOrCreateArtifact(
                pocketFileName,
                POCKET_ARTIFACT_LABEL,
                pocketFile.toString(),
                target,
                pipelineRun
        );

        PocketEntity entity = pocketRepository
                .findByStructureIdAndSourceAndPocketNumber(
                        structure.getId(),
                        PocketSource.FPOCKET,
                        pocketNumber
                )
                .orElseGet(PocketEntity::new);

        entity.setReceptor(receptor);
        entity.setStructure(structure);
        entity.setArtifact(pocketArtifact);
        entity.setSource(PocketSource.FPOCKET);
        entity.setPocketNumber(pocketNumber);
        entity.setFpocketFile(pocketFileName);
        entity.setVolume(metric(pocket, PocketMetricType.VOLUME));
        entity.setScore(metric(pocket, PocketMetricType.FPOCKET_SCORE));
        entity.setDruggabilityScore(
                metric(pocket, PocketMetricType.FPOCKET_DRUGGABILITY)
        );
        entity.setProbability(
                metric(pocket, PocketMetricType.P2RANK_PROBABILITY)
        );

        /*
         * Reimport replaces the membership, atom and alpha-sphere
         * children: orphan removal deletes the previous rows, the rebuilt
         * collections are inserted afterwards, so no duplicates remain.
         * An existing pocket is flushed right after clearing so the
         * deletes reach the database before the replacement rows are
         * inserted (the unique constraints on the child tables would
         * otherwise collide).
         */
        entity.clearResidues();
        entity.clearAlphaSpheres();
        if (entity.getId() != null) {
            pocketRepository.saveAndFlush(entity);
        }

        for (ResidueId pocketResidue : pocket.residues()) {
            ResidueKey key = key(pocketResidue);

            ResidueEntity canonicalResidue = residues.get(key);
            if (canonicalResidue == null) {
                throw new IllegalStateException(
                        ("fpocket pocket %d references residue %s that is"
                                + " not part of structure %s")
                                .formatted(
                                        pocketNumber,
                                        key,
                                        structure.getSourceAccession()
                                )
                );
            }
            if (canonicalResidue.getChain().length() != 1) {
                throw new IllegalStateException(
                        ("pocket_residue.chain is character(1) but residue"
                                + " %s of structure %s has chain '%s'")
                                .formatted(
                                        key,
                                        structure.getSourceAccession(),
                                        canonicalResidue.getChain()
                                )
                );
            }

            PocketResidueEntity membership = new PocketResidueEntity();
            membership.setResidue(canonicalResidue);
            membership.setChain(canonicalResidue.getChain());
            membership.setResidueNumber(
                    canonicalResidue.getResidueNumber()
            );
            membership.setResidueName(
                    canonicalResidue.getResidueName()
            );

            for (Atom atom : atomsByResidue.getOrDefault(
                    key,
                    List.of()
            )) {
                PocketAtomEntity pocketAtom = new PocketAtomEntity();

                pocketAtom.setAtomName(atom.getName());
                pocketAtom.setElement(
                        atom.getElement() == null
                                ? null
                                : atom.getElement().symbol()
                );
                pocketAtom.setX(atom.getPosition().x());
                pocketAtom.setY(atom.getPosition().y());
                pocketAtom.setZ(atom.getPosition().z());

                membership.addAtom(pocketAtom);
            }

            entity.addResidue(membership);
        }

        /*
         * Alpha spheres come from fpocket output only (the _vert.pqr file
         * the parser already read); sphere_index is the 0-based parser
         * order, not the PQR serial. Spheres are never inferred or
         * fabricated; P2Rank pockets have none by construction.
         */
        List<AlphaSphere> spheres = pocket.alphaSphereSet()
                .map(AlphaSphereSet::spheres)
                .orElse(List.of());
        for (int index = 0; index < spheres.size(); index++) {
            AlphaSphere sphere = spheres.get(index);
            entity.addAlphaSphere(new PocketAlphaSphereEntity(
                    index,
                    sphere.center().x(),
                    sphere.center().y(),
                    sphere.center().z(),
                    sphere.radius()
            ));
        }

        pocketRepository.save(entity);

        /*
         * The Stage 1 shape descriptor is computed from the in-memory
         * spheres just persisted (no extra database reads) so newly
         * imported pockets are immediately retrievable by shape. P2Rank
         * pockets have no spheres and stay descriptor-less.
         */
        if (!spheres.isEmpty()) {
            shapeDescriptorService.computeAndPersistFromCenters(
                    entity.getId(),
                    spheres.stream()
                            .map(AlphaSphere::center)
                            .toList()
            );
        }

        int atomCount = entity.getResidues().stream()
                .mapToInt(membership -> membership.getAtoms().size())
                .sum();

        LOG.info("Imported pocket {} of {}: source={}, alphaSpheres={}, "
                        + "residues={}, atoms={}",
                pocketNumber,
                structure.getSourceAccession(),
                PocketSource.FPOCKET,
                spheres.size(),
                entity.getResidues().size(),
                atomCount);

        return new PocketImportCounts(
                entity.getResidues().size(),
                atomCount,
                spheres.size()
        );
    }

    private void verifyResidueIdentity(
            ResidueEntity entity,
            Residue parsedResidue
    ) {
        String parsedName = parsedResidue.getName()
                .trim()
                .toUpperCase();
        if (!Objects.equals(entity.getResidueName(), parsedName)) {
            throw new IllegalStateException(
                    "Residue identity mismatch at %s: database=%s, file=%s"
                            .formatted(
                                    key(entity),
                                    entity.getResidueName(),
                                    parsedName
                            )
            );
        }
    }

    private static int pocketNumber(Pocket pocket) {
        try {
            return Math.toIntExact(
                    Long.parseLong(pocket.id().value())
            );
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "fpocket pocket id is not numeric: "
                            + pocket.id().value(),
                    exception
            );
        }
    }

    private static Double metric(Pocket pocket, PocketMetricType type) {
        OptionalDouble value = pocket.metric(type);
        return value.isPresent() ? value.getAsDouble() : null;
    }

    private ResidueKey key(ResidueEntity residue) {
        return new ResidueKey(
                residue.getChain(),
                residue.getResidueNumber(),
                normalizeInsertionCode(residue.getInsertionCode())
        );
    }

    private ResidueKey key(ResidueId residueId) {
        return new ResidueKey(
                residueId.chainId(),
                residueId.residueNumber(),
                normalizeInsertionCode(residueId.insertionCode())
        );
    }

    private ResidueKey key(String chain, Residue residue) {
        return new ResidueKey(
                chain.trim(),
                residue.getNumber(),
                normalizeInsertionCode(residue.getInsertionCode())
        );
    }

    private static String normalizeInsertionCode(Character insertionCode) {
        if (insertionCode == null
                || Character.isWhitespace(insertionCode)) {
            return "";
        }
        return String.valueOf(insertionCode);
    }

    private static String normalizeInsertionCode(String insertionCode) {
        return insertionCode == null ? "" : insertionCode.trim();
    }

    public record ImportResult(
            long receptorId,
            long structureId,
            int structureResidues,
            int pockets,
            int skippedPockets,
            int pocketResidues,
            int pocketAtoms
    ) {
    }

    private record PocketImportCounts(
            int residues,
            int atoms,
            int alphaSpheres
    ) {
    }

    record ParsedImport(
            AlphaFoldIdentity identity,
            Structure structure,
            List<Pocket> pockets,
            Map<Integer, Map<ResidueKey, List<Atom>>> pocketAtoms
    ) {
    }

    private record ResidueKey(
            String chain,
            int residueNumber,
            String insertionCode
    ) {
    }

    private record AlphaFoldIdentity(
            String structureAccession,
            String uniprotAccession,
            int modelVersion
    ) {
        private static AlphaFoldIdentity from(Path file) {
            String filename = file.getFileName() == null
                    ? ""
                    : file.getFileName().toString();

            if (!filename.endsWith(".pdb.gz")) {
                throw new IllegalArgumentException(
                        "Expected an AlphaFold .pdb.gz file: " + filename
                );
            }

            String accession = filename.substring(
                    0,
                    filename.length() - ".pdb.gz".length()
            );

            Matcher matcher = ALPHAFOLD_FILENAME.matcher(accession);
            if (!matcher.matches()) {
                throw new IllegalArgumentException(
                        ("Invalid AlphaFold filename, expected"
                                + " AF-<UniProt>-F<fragment>-model_v"
                                + "<version>.pdb.gz: ")
                                + filename
                );
            }

            return new AlphaFoldIdentity(
                    accession,
                    matcher.group(1),
                    Integer.parseInt(matcher.group(3))
            );
        }
    }
}
