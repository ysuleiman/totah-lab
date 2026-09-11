package totah.lab.athena.regression;

import org.junit.jupiter.api.Test;
import totah.lab.athena.interaction.Interaction;
import totah.lab.athena.interaction.InteractionFingerprint;
import totah.lab.athena.interaction.InteractionProfile;
import totah.lab.athena.interaction.InteractionProfiler;
import totah.lab.athena.interaction.InteractionType;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.pdb.reader.PdbReader;
import totah.lab.hermes.file.pdbqt.PdbqtAtom;
import totah.lab.hermes.file.pdbqt.PdbqtModel;
import totah.lab.hermes.file.sdf.reader.SdfLigandReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Regression gate for the unchanged neutral family-5 netarsudil pose. */
class NetarsudilParentFingerprintTopologyTest {
    private static final String CATEGORY = "netarsudil_parent_topology";

    @Test
    void reconstructedReceptorProducesReproducibleCompleteFingerprint()
            throws IOException {
        Structure receptor = new PdbReader().read(RegressionHarness.requireInput(
                "netarsudil/METTL7B_SAM_TOPOLOGY_COMPLETE_EXACT_COORDS.pdb",
                CATEGORY, "receptor"));
        Structure sdfLigand = new SdfLigandReader().read(
                RegressionHarness.requireInput(
                        "netarsudil/netarsudil_CID66599893_neutral.sdf",
                        CATEGORY, "ligand_sdf")).structure();
        PdbqtModel acceptedPose = RegressionHarness.model(
                RegressionHarness.readPdbqt(RegressionHarness.requireInput(
                        "netarsudil/7B_neutral_seed483271.pdbqt",
                        CATEGORY, "accepted_pose")), 5);
        Structure ligand = transplantCoordinates(sdfLigand, acceptedPose);

        InteractionProfile first = new InteractionProfiler().profile(receptor, ligand);
        InteractionProfile second = new InteractionProfiler().profile(receptor, ligand);

        assertThat(first.anyPerceptionDegraded()).isFalse();
        assertThat(second.anyPerceptionDegraded()).isFalse();
        assertThat(canonicalFingerprint(second)).isEqualTo(canonicalFingerprint(first));
        assertThat(canonicalInteractions(second))
                .containsExactlyElementsOf(canonicalInteractions(first));
        assertThat(first.interactions(InteractionType.HYDROGEN_BOND)).isEmpty();
        assertThat(first.interactions(InteractionType.SALT_BRIDGE)).isEmpty();
        assertThat(first.interactions(InteractionType.HYDROPHOBIC_CONTACT))
                .extracting(interaction -> interaction.residue().residueNumber())
                .containsExactly(151, 196, 206);
        assertThat(first.interactions(InteractionType.PI_CATION))
                .singleElement()
                .satisfies(interaction -> {
                    assertThat(interaction.residue().residueNumber()).isEqualTo(196);
                    assertThat(interaction.distanceAngstroms())
                            .isCloseTo(4.498908797, org.assertj.core.data.Offset.offset(1e-9));
                });

        System.out.println("NETARSUDIL_PARENT_RESULT connectivity="
                + receptor.getConnectivityMetadata());
        System.out.println("NETARSUDIL_PARENT_RESULT fingerprint="
                + canonicalFingerprint(first));
        for (InteractionType type : InteractionType.values()) {
            long raw = first.rawInteractions().stream()
                    .filter(interaction -> interaction.type() == type).count();
            System.out.println("NETARSUDIL_PARENT_RESULT count_" + type
                    + " raw=" + raw + " refined=" + first.interactions(type).size());
        }
        first.perception().forEach(summary -> System.out.println(
                "NETARSUDIL_PARENT_RESULT perception=" + summary));
        canonicalInteractions(first).forEach(value -> System.out.println(
                "NETARSUDIL_PARENT_RESULT interaction=" + value));
    }

    private Structure transplantCoordinates(Structure source, PdbqtModel pose) {
        Map<String, PdbqtAtom> byName = new LinkedHashMap<>();
        pose.atoms().forEach(atom -> assertThat(byName.put(atom.atomName(), atom))
                .as("unique pose atom " + atom.atomName()).isNull());
        Set<String> used = new java.util.LinkedHashSet<>();
        List<Chain> chains = new ArrayList<>();
        for (Chain chain : source.getChains()) {
            List<Residue> residues = new ArrayList<>();
            for (Residue residue : chain.residues()) {
                List<Atom> atoms = residue.getAtoms().stream().map(atom -> {
                    PdbqtAtom posed = byName.get(atom.getName());
                    assertThat(posed).as("pose coordinate for " + atom.getName()).isNotNull();
                    used.add(atom.getName());
                    return atom.toBuilder().position(posed.position()).build();
                }).toList();
                residues.add(residue.toBuilder().atoms(atoms).build());
            }
            chains.add(new Chain(chain.id(), residues));
        }
        assertThat(used).containsExactlyInAnyOrderElementsOf(byName.keySet());
        return new Structure(chains, source.bonds(), source.getConnectivityMetadata());
    }

    private String canonicalFingerprint(InteractionProfile profile) {
        return InteractionFingerprint.of(profile).byResidue().entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(type -> entry.getKey() + ":" + type))
                .sorted().collect(Collectors.joining(";"));
    }

    private List<String> canonicalInteractions(InteractionProfile profile) {
        return profile.interactions().stream().map(interaction ->
                        interaction.residue() + "|" + interaction.type() + "|"
                                + String.format(java.util.Locale.ROOT, "%.9f",
                                interaction.distanceAngstroms()))
                .sorted(Comparator.naturalOrder()).toList();
    }
}
