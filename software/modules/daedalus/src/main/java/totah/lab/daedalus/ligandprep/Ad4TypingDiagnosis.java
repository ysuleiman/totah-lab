package totah.lab.daedalus.ligandprep;

import totah.lab.hermes.file.pdbqt.PdbqtModel;
import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;
import totah.lab.hermes.file.pdbqt.PdbqtAtom;
import totah.lab.hermes.file.pdbqt.PdbqtModel;
import totah.lab.gaia.chemistry.ChemicalBond;
import totah.lab.gaia.structure.Atom;
import totah.lab.hephaestus.client.HephaestusClient;
import totah.lab.hephaestus.ligand.LigandPreparationOptions;
import totah.lab.hephaestus.ligand.LigandPreparationResult;
import totah.lab.hermes.file.sdf.SdfLigand;
import totah.lab.hermes.file.sdf.reader.SdfLigandReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Phase 1 diagnosis: groups AD4 typing mismatches between hephaestus
 * and Meeko by (our type, Meeko type) pair and prints the local
 * chemical context of representative atoms (from the source SDF bond
 * table), so each group's rule difference can be named. Output is a
 * Markdown document.
 */
public final class Ad4TypingDiagnosis {

    private final LigandPrepSampler sampler;
    private final HephaestusClient hephaestus;
    private final Path workDirectory;
    private final SdfLigandReader sdfReader = new SdfLigandReader();

    public Ad4TypingDiagnosis(
            LigandPrepSampler sampler,
            HephaestusClient hephaestus,
            Path workDirectory
    ) {
        this.sampler = Objects.requireNonNull(sampler, "sampler");
        this.hephaestus = Objects.requireNonNull(hephaestus, "hephaestus");
        this.workDirectory = Objects.requireNonNull(
                workDirectory, "workDirectory");
    }

    public String diagnose(int count) throws Exception {
        List<LigandPrepSample> samples = sampler.sample(count);
        Files.createDirectories(workDirectory);

        Map<String, Group> groups = new LinkedHashMap<>();
        int compared = 0;
        int failed = 0;

        int index = 0;
        for (LigandPrepSample sample : samples) {
            try {
                collect(sample, index, groups);
                compared++;
            } catch (Exception exception) {
                failed++;
            }
            index++;
        }

        StringBuilder markdown = new StringBuilder();
        markdown.append("# AD4 typing diagnosis: hephaestus vs Meeko\n\n");
        markdown.append("Sample: ").append(samples.size())
                .append(" ligands (").append(compared)
                .append(" compared, ").append(failed)
                .append(" failed preparation). Mismatched heavy atoms"
                        + " grouped by (hephaestus type -> Meeko type)."
                        + " Context comes from the source SDF bond"
                        + " table.\n\n");

        groups.entrySet().stream()
                .sorted(Map.Entry.<String, Group>comparingByValue(
                        Comparator.<Group>comparingInt(
                                group -> group.examples.size()).reversed()))
                .forEach(entry -> {
                    Group group = entry.getValue();
                    markdown.append("## ").append(entry.getKey())
                            .append(" (").append(group.examples.size())
                            .append(" atoms)\n\n");
                    group.examples.stream().limit(3).forEach(example ->
                            markdown.append("- ").append(example)
                                    .append('\n'));
                    markdown.append('\n');
                });

        return markdown.toString();
    }

    private void collect(
            LigandPrepSample sample,
            int index,
            Map<String, Group> groups
    ) throws Exception {
        SdfLigand sdf = sdfReader.readModel(sample.sdf());
        LigandPreparationResult preparation = hephaestus.prepareLigand(
                sample.sdf(), LigandPreparationOptions.defaults());
        if (!preparation.successful()) {
            throw new IllegalStateException("preparation failed");
        }
        Path ourPath = workDirectory.resolve("diag-" + index + ".pdbqt");
        hephaestus.writePreparedLigand(
                preparation.preparedLigand(), ourPath);

        PdbqtModel ours = new PdbqtReader().read(ourPath).firstModel();
        PdbqtModel meeko = new PdbqtReader().read(sample.meekoPdbqt()).firstModel();

        List<PdbqtAtom> ourHeavy = ours.heavyAtoms();
        List<PdbqtAtom> meekoHeavy = meeko.heavyAtoms();

        List<Atom> sdfAtoms = sdf.ligand().structure().getChains()
                .getFirst().residues().getFirst().getAtoms();

        for (int[] match : LigandPrepComparator.matchHeavyAtoms(
                ourHeavy, meekoHeavy)) {
            PdbqtAtom ourAtom = ourHeavy.get(match[0]);
            PdbqtAtom meekoAtom = meekoHeavy.get(match[1]);
            if (ourAtom.autodockType().equals(meekoAtom.autodockType())) {
                continue;
            }

            int sdfIndex = nearestSdfAtom(sdfAtoms, ourAtom);
            String context = context(
                    sdf, sdfAtoms, sdfIndex, sample, ourAtom,
                    meekoAtom);
            groups.computeIfAbsent(
                    ourAtom.autodockType() + " -> " + meekoAtom.autodockType(),
                    key -> new Group()).examples.add(context);
        }
    }

    private static int nearestSdfAtom(
            List<Atom> sdfAtoms, PdbqtAtom pdbqtAtom) {
        int best = -1;
        double bestDistance = 0.02;
        for (int index = 0; index < sdfAtoms.size(); index++) {
            var position = sdfAtoms.get(index).getPosition();
            double dx = position.x() - pdbqtAtom.position().x();
            double dy = position.y() - pdbqtAtom.position().y();
            double dz = position.z() - pdbqtAtom.position().z();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = index;
            }
        }
        return best;
    }

    private static String context(
            SdfLigand sdf,
            List<Atom> sdfAtoms,
            int sdfIndex,
            LigandPrepSample sample,
            PdbqtAtom ourAtom,
            PdbqtAtom meekoAtom
    ) {
        if (sdfIndex < 0) {
            return sample.id() + " atom " + ourAtom.atomName()
                    + ": no SDF atom matched (coordinate lookup failed)";
        }

        List<String> neighbors = new ArrayList<>();
        boolean aromaticBond = false;
        int attachedHydrogens = 0;
        for (ChemicalBond bond : sdf.bonds()) {
            Integer neighbor = null;
            if (bond.atomIndexA() == sdfIndex) {
                neighbor = bond.atomIndexB();
            } else if (bond.atomIndexB() == sdfIndex) {
                neighbor = bond.atomIndexA();
            }
            if (neighbor == null) {
                continue;
            }
            Atom neighborAtom = sdfAtoms.get(neighbor);
            neighbors.add(neighborAtom.getElement().symbol()
                    + "(" + bond.order() + ")");
            aromaticBond |= bond.aromatic();
            if (neighborAtom.isHydrogen()) {
                attachedHydrogens++;
            }
        }

        Atom atom = sdfAtoms.get(sdfIndex);
        return sample.id()
                + " atom " + atom.getName()
                + ": element=" + atom.getElement().symbol()
                + ", formalCharge=" + sdf.formalCharges().get(sdfIndex)
                + ", degree=" + neighbors.size()
                + ", attachedH=" + attachedHydrogens
                + ", aromaticBond=" + aromaticBond
                + ", neighbors=" + neighbors;
    }

    private static final class Group {
        private final List<String> examples = new ArrayList<>();
    }
}
