package totah.lab.mettl7.design;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.design.backend.ocl.OclLigandFeaturePerceiver;
import totah.lab.athena.design.feature.LigandFeature;
import totah.lab.athena.design.grammar.ExecutableScaffoldGrammar;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Mettl7GeneratedFeatureMappingTest {
    @Test void parentAndPreservingEditRetainO7WhileDestructiveEditLosesIt() throws Exception {
        Path root=Path.of(System.getProperty("user.dir")).resolve("../../../").normalize();
        var frozen=new ObjectMapper().readValue(root.resolve("analysis/mettl7/mettl7b_design_campaign_v1/METTL7B_BRICS0040_EXECUTABLE_SCAFFOLD_GRAMMAR.json").toFile(),ExecutableScaffoldGrammar.class);
        MolecularGraph parent=graph(frozen.parentGraph());var perceiver=new OclLigandFeaturePerceiver();
        assertThat(perceiver.perceive(parent).features()).anyMatch(f->f.type()== LigandFeature.Type.H_BOND_ACCEPTOR&&f.atomIds().contains("O7"));
        assertThat(perceiver.perceive(parent).features()).anyMatch(f->f.type()== LigandFeature.Type.HYDROPHOBE&&f.atomIds().contains("C1"));
        var preserving=replace(parent,"C1","F");
        assertThat(perceiver.perceive(preserving).features()).anyMatch(f->f.type()==LigandFeature.Type.H_BOND_ACCEPTOR&&f.atomIds().contains("O7"));
        var destroying=replace(parent,"O7","C");
        assertThat(perceiver.perceive(destroying).features()).noneMatch(f->f.type()==LigandFeature.Type.H_BOND_ACCEPTOR&&f.atomIds().contains("O7"));
    }
    private static MolecularGraph replace(MolecularGraph graph,String id,String element){return new MolecularGraph(graph.atoms().stream().map(a->a.id().equals(id)?new MolecularGraph.Atom(a.id(),element,a.isotope(),a.formalCharge(),a.explicitHydrogens(),false,a.stereochemistry(),a.coordinates(),a.properties()):a).toList(),graph.bonds(),graph.properties());}
    private static MolecularGraph graph(ExecutableScaffoldGrammar.IndexedGraph graph){var atoms=graph.atoms().stream().map(a->new MolecularGraph.Atom(a.id(),a.element(),null,a.formalCharge()==null?0:a.formalCharge(),0,a.aromatic(),a.stereochemistry(),new MolecularGraph.Coordinates(a.sourceIndex(),0,0),Map.of())).toList();List<MolecularGraph.Bond>bonds=new ArrayList<>();int i=0;for(var b:graph.bonds())bonds.add(new MolecularGraph.Bond("b"+(++i),b.firstAtomId(),b.secondAtomId(),MolecularGraph.BondOrder.valueOf(b.order()),b.aromatic(),b.stereochemistry(),Map.of()));return new MolecularGraph(atoms,bonds,Map.of());}
}
