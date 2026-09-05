package totah.lab.mettl7.campaign.v2;

import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.pdbqt.PdbqtGaiaMapper;
import totah.lab.hermes.file.pdbqt.PdbqtWriteOptions;
import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;
import totah.lab.hermes.file.pdbqt.writer.PdbqtWriter;
import totah.lab.proteus.protein.mutation.AmbiguousCovalentTopologyPolicy;
import totah.lab.proteus.protein.mutation.Mutation;
import totah.lab.proteus.protein.mutation.MutationContext;
import totah.lab.proteus.protein.mutation.ProteinMutationOperation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the clean campaign's fixed-backbone reciprocal-mutant receptor panel.
 * Proteus supplies deterministic rotamer construction; Hermes preserves the
 * prepared AutoDock atom types and charges. No docking or scoring occurs here.
 */
public final class Mettl7ReceptorPanelBuilder {
    private static final Pattern SPEC = Pattern.compile("^([A-Z])(\\d+)([A-Z])$");
    private static final List<String> BACKBONE = List.of(
            "N", "CA", "C", "O", "OXT", "H", "HA", "HA2", "HA3");
    private static final Map<String, String> RESIDUES = Map.ofEntries(
            Map.entry("A", "ALA"), Map.entry("C", "CYS"), Map.entry("D", "ASP"),
            Map.entry("E", "GLU"), Map.entry("F", "PHE"), Map.entry("G", "GLY"),
            Map.entry("H", "HIS"), Map.entry("I", "ILE"), Map.entry("K", "LYS"),
            Map.entry("L", "LEU"), Map.entry("M", "MET"), Map.entry("N", "ASN"),
            Map.entry("P", "PRO"), Map.entry("Q", "GLN"), Map.entry("R", "ARG"),
            Map.entry("S", "SER"), Map.entry("T", "THR"), Map.entry("V", "VAL"),
            Map.entry("W", "TRP"), Map.entry("Y", "TYR"));

    private final ProteinMutationOperation mutationOperation;
    private final PdbqtReader reader;
    private final PdbqtWriter writer;

    public Mettl7ReceptorPanelBuilder() {
        this(new ProteinMutationOperation(), new PdbqtReader(), new PdbqtWriter());
    }

    Mettl7ReceptorPanelBuilder(ProteinMutationOperation mutationOperation,
                               PdbqtReader reader, PdbqtWriter writer) {
        this.mutationOperation = Objects.requireNonNull(mutationOperation);
        this.reader = Objects.requireNonNull(reader);
        this.writer = Objects.requireNonNull(writer);
    }

    /** Builds one receptor while leaving the source file untouched. */
    public BuildReceipt build(Path wildTypePdbqt, ReceptorBackground background,
                              Path outputPdbqt) throws IOException {
        Objects.requireNonNull(wildTypePdbqt, "wildTypePdbqt");
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(outputPdbqt, "outputPdbqt");
        Structure wildType = PdbqtGaiaMapper.toStructure(reader.read(wildTypePdbqt));
        int sourceAtoms = wildType.getAtomCount();
        long sourceSamAtoms = samAtomCount(wildType);
        Structure current = wildType;
        List<MutationReceipt> receipts = new ArrayList<>();
        for (String specification : background.substitutions()) {
            Mutation mutation = parse(specification, current);
            var result = mutationOperation.apply(current, mutation, new MutationContext(
                    AmbiguousCovalentTopologyPolicy.WARN_AND_PROCEED, false, false, 0.0));
            var applied = result.appliedMutation().orElseThrow(() ->
                    new IllegalStateException("Proteus rejected " + specification + ": "
                            + result.validation().issues()));
            current = typeNewSideChain(result.structure(), wildType, mutation);
            receipts.add(new MutationReceipt(specification, applied.rotamerId(),
                    applied.stericScore()));
        }
        if (samAtomCount(current) != sourceSamAtoms) {
            throw new IllegalStateException("SAM atom count changed while building "
                    + background.id());
        }
        Path parent = outputPdbqt.toAbsolutePath().normalize().getParent();
        if (parent != null) Files.createDirectories(parent);
        writer.write(PdbqtGaiaMapper.fromStructure(current), outputPdbqt,
                PdbqtWriteOptions.defaults());
        return new BuildReceipt(background.id(), wildTypePdbqt.toAbsolutePath().normalize(),
                outputPdbqt.toAbsolutePath().normalize(), sourceAtoms,
                current.getAtomCount(), sourceSamAtoms, List.copyOf(receipts));
    }

    private static Mutation parse(String value, Structure structure) {
        String normalized = Objects.requireNonNull(value).trim().toUpperCase(Locale.ROOT);
        Matcher matcher = SPEC.matcher(normalized);
        if (!matcher.matches()) throw new IllegalArgumentException("Invalid mutation: " + value);
        int position = Integer.parseInt(matcher.group(2));
        String expected = residueName(matcher.group(1));
        String replacement = residueName(matcher.group(3));
        List<ResidueId> matches = new ArrayList<>();
        for (Chain chain : structure.getChains()) {
            for (Residue residue : chain.residues()) {
                if (residue.getNumber() == position
                        && residue.getName().equalsIgnoreCase(expected)) {
                    matches.add(new ResidueId(chain.id(), position, residue.getInsertionCode()));
                }
            }
        }
        if (matches.size() != 1) {
            throw new IllegalStateException(normalized + " resolved to " + matches.size()
                    + " residues; expected exactly one");
        }
        return new Mutation(matches.getFirst(), expected, replacement);
    }

    private static Structure typeNewSideChain(Structure mutant, Structure wildType,
                                              Mutation mutation) {
        Residue exemplar = wildType.getChains().stream().flatMap(c -> c.residues().stream())
                .filter(r -> r.getName().equalsIgnoreCase(mutation.replacementResidueName()))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "No prepared " + mutation.replacementResidueName()
                                + " typing exemplar in WT receptor"));
        Map<String, Atom> exemplarAtoms = new LinkedHashMap<>();
        exemplar.getAtoms().forEach(a -> exemplarAtoms.putIfAbsent(a.getName(), a));
        List<Chain> chains = new ArrayList<>();
        for (Chain chain : mutant.getChains()) {
            List<Residue> residues = new ArrayList<>();
            for (Residue residue : chain.residues()) {
                if (same(residue, chain.id(), mutation.target())) {
                    List<Atom> atoms = new ArrayList<>();
                    for (Atom atom : residue.getAtoms()) {
                        if (BACKBONE.contains(atom.getName())) {
                            atoms.add(atom);
                        } else {
                            Atom template = exemplarAtoms.get(atom.getName());
                            if (template == null) throw new IllegalStateException(
                                    "Typing exemplar lacks atom " + atom.getName());
                            atoms.add(atom.toBuilder().charge(template.getCharge())
                                    .autoDockType(template.getAutoDockType()).build());
                        }
                    }
                    residues.add(new Residue(residue.getName(), residue.getNumber(),
                            residue.getInsertionCode(), residue.getClassificationEvidence(), atoms));
                } else residues.add(residue);
            }
            chains.add(new Chain(chain.id(), residues));
        }
        return new Structure(chains, mutant.bonds(), mutant.getConnectivityMetadata());
    }

    private static boolean same(Residue residue, String chain, ResidueId target) {
        return chain.equals(target.chainId()) && residue.getNumber() == target.residueNumber()
                && Objects.equals(residue.getInsertionCode(), target.insertionCode());
    }

    private static long samAtomCount(Structure structure) {
        return structure.getChains().stream().flatMap(c -> c.residues().stream())
                .filter(r -> r.getName().equalsIgnoreCase("SAM"))
                .mapToLong(Residue::getAtomCount).sum();
    }

    private static String residueName(String oneLetter) {
        String name = RESIDUES.get(oneLetter);
        if (name == null) throw new IllegalArgumentException("Unsupported residue: " + oneLetter);
        return name;
    }

    public record MutationReceipt(String specification, String rotamerId, double stericScore) {}

    public record BuildReceipt(String receptorId, Path source, Path output,
                               int sourceAtomCount, int outputAtomCount, long samAtomCount,
                               List<MutationReceipt> mutations) {
        public BuildReceipt { mutations = List.copyOf(mutations); }
    }
}
