package totah.lab.athena.relaxation;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.athena.energy.EnergyComponent;
import totah.lab.athena.energy.MolecularState;
import totah.lab.athena.energy.openmm.OpenMmForceGroupMap;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessOpenMmLocalRelaxationEngineIntegrationTest {
    @TempDir Path directory;

    @Test
    void minimizesUsingOnlyExplicitMaskAndProtocolValues() throws Exception {
        String python = System.getProperty("athena.openmm.python");
        Assumptions.assumeTrue(python != null && Files.isExecutable(Path.of(python)));
        Path builder = Path.of(getClass().getResource("/openmm/build_controlled_fixture.py").toURI());
        Process process = new ProcessBuilder(python, builder.toString(), directory.toString(),
                "NO_FROZEN_RESTRAINT")
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor()).as(output).isZero();
        for (String name : List.of("receptor", "ligand", "parameters"))
            Files.writeString(directory.resolve(name + ".txt"), name);
        SystemAtomMapping mapping = mapping();
        Path manifest = TestOpenMmManifest.write(directory, mapping.sha256(), false);
        LocalRelaxationProtocol protocol = new LocalRelaxationProtocol("fixture-min-v1", 3.0,
                LocalRelaxationProtocol.BackbonePolicy.FIXED, Optional.empty(),
                LocalRelaxationProtocol.SideChainPolicy.WITHIN_RADIUS_MOBILE, Optional.empty(),
                restraint(), restraint(), mapping, rules(), "LocalEnergyMinimizer", 10.0,
                500, 8, true);
        var engine = new ProcessOpenMmLocalRelaxationEngine(Path.of(python),
                Path.of("src/main/resources/openmm/athena_openmm_runner.py"),
                directory.resolve("receipts"), groups());
        var result = engine.relax(state(manifest), protocol);
        double pre = Double.parseDouble(result.provenance().get("pre.energy.kj_per_mol"));
        double post = Double.parseDouble(result.provenance().get("post.energy.kj_per_mol"));
        assertThat(post).isLessThan(pre);
        assertThat(result.provenance()).containsKeys("selection.mask.sha256",
                "execution.receipt.sha256", "pre.force.groups.kj_per_mol",
                "post.force.groups.kj_per_mol");
        assertThat(result.displacements().maximumAtomDisplacementAngstroms()).isGreaterThan(0);
        SystemAtomMapping differentMappingIdentity = SystemAtomMapping.create(mapping.entries(),
                "different-provenance");
        LocalRelaxationProtocol mismatched = new LocalRelaxationProtocol("fixture-min-v1", 3.0,
                LocalRelaxationProtocol.BackbonePolicy.FIXED, Optional.empty(),
                LocalRelaxationProtocol.SideChainPolicy.WITHIN_RADIUS_MOBILE, Optional.empty(),
                restraint(), restraint(), differentMappingIdentity, rules(), "LocalEnergyMinimizer",
                10.0, 500, 8, true);
        assertThatThrownBy(() -> engine.relax(state(manifest), mismatched))
                .hasMessageContaining("not the mapping frozen");
    }

    private static LocalRelaxationProtocol.Restraint restraint() {
        return new LocalRelaxationProtocol.Restraint(LocalRelaxationProtocol.Policy.POSITIONAL,
                Optional.of(new HarmonicForceConstant(10,
                        HarmonicForceConstant.Unit.KILOJOULES_PER_MOLE_NANOMETRE_SQUARED)), 5);
    }

    private static AtomSelectionRules rules() {
        return new AtomSelectionRules(AtomSelectionRules.Mobility.FIXED,
                AtomSelectionRules.Mobility.POSITION_RESTRAINED,
                AtomSelectionRules.Mobility.LOCAL_MOBILE,
                AtomSelectionRules.Mobility.POSITION_RESTRAINED,
                AtomSelectionRules.Mobility.FIXED,
                AtomSelectionRules.Mobility.UNRESTRAINED,
                AtomSelectionRules.Mobility.POSITION_RESTRAINED,
                AtomSelectionRules.Mobility.UNRESTRAINED,
                AtomSelectionRules.Mobility.POSITION_RESTRAINED,
                AtomSelectionRules.Mobility.UNRESTRAINED,
                AtomSelectionRules.Mobility.UNRESTRAINED,
                AtomSelectionRules.RadiusReference.LIGAND_HEAVY_ATOMS, "fixture-rules-v1");
    }

    private static SystemAtomMapping mapping() {
        return SystemAtomMapping.create(List.of(
                entry("A", "CA", 0, SystemAtomMapping.ComponentRole.PROTEIN_BACKBONE),
                entry("A", "CB", 1, SystemAtomMapping.ComponentRole.PROTEIN_SIDE_CHAIN),
                entry("L", "C1", 2, SystemAtomMapping.ComponentRole.LIGAND),
                entry("S", "S", 3, SystemAtomMapping.ComponentRole.SAM)), "fixture-map-v1");
    }

    private static SystemAtomMapping.Entry entry(String chain, String atom, int index,
            SystemAtomMapping.ComponentRole role) {
        return new SystemAtomMapping.Entry(new AtomReference(chain, 1, ' ', atom), index, role);
    }

    private static MolecularState state(Path manifest) {
        Structure receptor = structure("A", "ALA", List.of(atom(1, "CA", 0, 0, 0),
                atom(2, "CB", 2.5, 0, 0)));
        Structure ligand = structure("L", "LIG", List.of(atom(3, "C1", 4.5, 1.5, 0)));
        Structure sam = structure("S", "SAM", List.of(atom(4, "S", 7.0, 1.0, 2.0)));
        return new MolecularState("fixture", "R", "L", receptor, ligand, Optional.of(sam),
                "explicit", "controlled", "controlled", "vacuum", "explicit",
                Map.of("openmm.system.manifest", manifest.toString()));
    }

    private static Structure structure(String chain, String residue, List<Atom> atoms) {
        return new Structure(List.of(new Chain(chain, List.of(new Residue(residue, 1, atoms)))));
    }

    private static Atom atom(int serial, String name, double xAngstrom, double yAngstrom,
            double zAngstrom) {
        return Atom.builder().pdbSerial(serial).name(name)
                .position(new Point3D(xAngstrom, yAngstrom, zAngstrom))
                .element(name.equals("S") ? Element.S : Element.C).build();
    }

    private static OpenMmForceGroupMap groups() {
        return new OpenMmForceGroupMap(Map.of(
                1, group("NonbondedForce", EnergyComponent.NONBONDED),
                2, group("HarmonicBondForce", EnergyComponent.BONDED),
                3, group("HarmonicAngleForce", EnergyComponent.BONDED),
                4, group("PeriodicTorsionForce", EnergyComponent.BONDED),
                5, group("CustomExternalForce", EnergyComponent.RESTRAINT)));
    }

    private static OpenMmForceGroupMap.ForceGroup group(String name, EnergyComponent component) {
        return new OpenMmForceGroupMap.ForceGroup(name, component, "controlled fixture");
    }
}
