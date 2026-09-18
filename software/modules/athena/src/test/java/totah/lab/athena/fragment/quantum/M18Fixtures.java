package totah.lab.athena.fragment.quantum;

import java.nio.charset.StandardCharsets;
import java.util.*;
import totah.lab.athena.energy.MolecularState;
import totah.lab.athena.interaction.perception.FormalChargeAssignments;
import totah.lab.gaia.chemistry.*;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.*;

final class M18Fixtures {
    static final ResidueId RESIDUE=new ResidueId("R",149,null);
    static Atom atom(String name,Element element,double x,double y,double z,int serial) {
        return Atom.builder().name(name).element(element).position(new Point3D(x,y,z)).pdbSerial(serial).charge(.123).build();
    }
    static Structure structure(String chain,int residue,String name,List<Atom> atoms,int[][] connections) {
        var bonds=new ArrayList<Bond>();
        for(var edge:connections)bonds.add(new Bond(new AtomReference(chain,residue,' ',atoms.get(edge[0]).getName()),
                new AtomReference(chain,residue,' ',atoms.get(edge[1]).getName()),BondOrder.SINGLE));
        return new Structure(List.of(new Chain(chain,List.of(new Residue(name,residue,atoms)))),bonds);
    }
    static QuantumEnvironment.ChemistryEvidence evidence(Structure structure,Map<String,Integer> nonzero) {
        var charges=new HashMap<AtomReference,Integer>();QuantumEnvironment.atoms(structure).forEach((ref,a)->charges.put(ref,nonzero.getOrDefault(ref.atomName(),0)));
        return new QuantumEnvironment.ChemistryEvidence(QuantumEnvironment.ChargeStatus.VERIFIED,QuantumEnvironment.structureHash(structure),
                "M18 independent explicit molecular fixture","All hydrogens and per-atom formal charges specified",new FormalChargeAssignments(charges),true,true,1);
    }
    static QuantumEnvironment environment(Structure receptor,Structure ligand,boolean water,boolean verified,QuantumEnvironment.InteractionClass label) {
        var state=new MolecularState("M18_CONTROL","CONTROL_RECEPTOR","CONTROL_LIGAND",receptor,ligand,Optional.empty(),"Explicit control fixture",
                "NOT_USED","NOT_USED","GAS_PHASE_FRAGMENT_MODEL","NO_RESTRAINT_OR_OPTIMIZATION",Map.of("source","M18 controlled fixture"));
        return new QuantumEnvironment(state,List.of(),List.of(new QuantumEnvironment.ClassAnnotation(RESIDUE,label,"Independent control fixture identity")),
                verified?Map.of("receptor",evidence(receptor,Map.of()),"ligand",evidence(ligand,Map.of())):Map.of(),
                water?Set.of(RESIDUE):Set.of(),Set.of(),true,true);
    }
    static QuantumEnvironment water(boolean verified)throws Exception {
        try(var input=M18Fixtures.class.getResourceAsStream("/quantum/m18/water-dimer.atoms")) {
            var lines=new String(Objects.requireNonNull(input).readAllBytes(),StandardCharsets.UTF_8).lines().toList();
            var atoms=new ArrayList<Atom>();
            for(int i=1;i<lines.size();i++) {
                var row=lines.get(i).split(",");int local=(i-1)%3;double scale=QuantumFragmentBuilder.ANGSTROM_PER_BOHR;
                atoms.add(atom(local==0?"O":"H"+local,local==0?Element.O:Element.H,
                        Double.parseDouble(row[1])*scale,Double.parseDouble(row[2])*scale,Double.parseDouble(row[3])*scale,i));
            }
            var ligand=structure("L",1,"HOH",atoms.subList(0,3),new int[][]{{0,1},{0,2}});
            var receptor=structure("R",149,"HOH",atoms.subList(3,6),new int[][]{{0,1},{0,2}});
            return environment(receptor,ligand,true,verified,QuantumEnvironment.InteractionClass.hydrogen_bond);
        }
    }
    static Structure ethane() {
        return structure("R",149,"ETH",List.of(atom("CA",Element.C,0,0,0,1),atom("CB",Element.C,1.54,0,0,2),
                atom("HA1",Element.H,-.36,1.02,0,3),atom("HA2",Element.H,-.36,-.51,.883,4),atom("HA3",Element.H,-.36,-.51,-.883,5),
                atom("HB1",Element.H,1.9,1.02,0,6),atom("HB2",Element.H,1.9,-.51,.883,7),atom("HB3",Element.H,1.9,-.51,-.883,8)),
                new int[][]{{0,1},{0,2},{0,3},{0,4},{1,5},{1,6},{1,7}});
    }
    static Structure transform(Structure original) {
        var chains=new ArrayList<Chain>();
        for(var chain:original.getChains()) {
            var residues=new ArrayList<Residue>();
            for(var residue:chain.residues())residues.add(residue.toBuilder().atoms(residue.getAtoms().stream().map(a->{var p=a.getPosition();return a.toBuilder().position(new Point3D(-p.y()+2,p.x()-3,p.z()+1)).build();}).toList()).build());
            chains.add(new Chain(chain.id(),residues));
        }
        return new Structure(chains,original.getBonds(),original.getConnectivityMetadata());
    }
}
