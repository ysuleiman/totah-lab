package totah.lab.aether;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import totah.lab.aether.matrix.*;
import totah.lab.aether.model.*;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.gaia.geometry.Point3D;
import static org.junit.jupiter.api.Assertions.*;

class AetherInteractionTest {
    private static final Map<String,InteractionEnergyResult> RUNS=new TreeMap<>();
    private static InteractionEnergyResult run(String name)throws Exception {
        if(!RUNS.containsKey(name)) {
            System.out.println("INTERACTION_START="+name);
            var r=InteractionEnergyCalculator.calculate(InteractionReceiptReplay.pairs().get(name));RUNS.put(name,r);
            System.out.println("INTERACTION_DONE="+name+" status="+r.status()+" uncorrected="+r.uncorrectedHartree()+" CP="+r.counterpoiseHartree());
            for(var c:r.components())System.out.println("INTERACTION_COMPONENT="+name+" "+c.role()+" "+c.calculation().status()+" iterations="+c.calculation().iterations().size()+" reason="+c.calculation().receipt().reason());
        }
        return RUNS.get(name);
    }
    @Test void everyReferenceComponentAndBothInteractionEnergies()throws Exception {
        var errors=new TreeMap<String,Double>();int entries=0;
        String fixture=InteractionReceiptReplay.fixture("interaction.csv","4ca891c5a142340bae95198b273c47a89fa2d4cb745a8291f875331533f91e7c");
        var mismatches=new java.util.TreeSet<String>();
        for(var line:fixture.lines().skip(1).toList()) {
            var c=line.split(",");var r=run(c[0]);assertEquals(InteractionEnergyResult.Status.EVALUATED,r.status(),c[0]);double actual;
            if(c[1].equals("INTERACTION"))actual=c[2].equals("uncorrected")?r.uncorrectedHartree().orElseThrow():r.counterpoiseHartree().orElseThrow();
            else {
                var state=r.component(InteractionEnergyResult.Role.valueOf(c[1])).calculation().convergedState().orElseThrow();
                int i=Integer.parseInt(c[3]),j=Integer.parseInt(c[4]);actual=switch(c[2]) {
                    case "total"->state.energy().totalHartree();case "nuclear"->state.energy().nuclearHartree();case "electronic"->state.energy().electronicHartree();
                    case "density"->state.density().get(i,j);case "Fock"->state.physicalOrbitals().fock().get(i,j);
                    case "basis_functions"->state.density().size();case "electrons"->OccupiedDensityCalculator.occupation(state.density().system(),state.density().size()).electrons();
                    default->throw new AssertionError(c[2]);
                };
            }
            double error=Math.abs(actual-Double.parseDouble(c[5]));errors.merge(c[2],error,Math::max);
            if(error>1e-8)mismatches.add(c[0]+":"+c[1]+":"+c[2]);entries++;
        }
        System.out.println("INTERACTION_REFERENCE_ENTRIES="+entries+" ERRORS="+errors);
        var folder=Path.of("target/interaction-validation");Files.createDirectories(folder);
        for(var item:RUNS.entrySet())Files.writeString(folder.resolve(item.getKey()+".receipt"),InteractionReceiptReplay.receipts(item.getValue()));
        assertEquals(6,RUNS.size());assertTrue(mismatches.isEmpty(),mismatches.toString());
    }
    @Test void scalarSubtractionsAreExplicitAndCounterpoiseIsSeparate()throws Exception {
        var r=run("water_dimer");var c=r.components();
        double ab=energy(c.get(0)),a=energy(c.get(1)),b=energy(c.get(2)),ag=energy(c.get(3)),bg=energy(c.get(4));
        assertEquals((ab-a)-b,r.uncorrectedHartree().orElseThrow());assertEquals((ab-ag)-bg,r.counterpoiseHartree().orElseThrow());
        assertNotEquals(r.uncorrectedHartree(),r.counterpoiseHartree());
    }
    private static double energy(InteractionEnergyResult.Component c){return c.calculation().convergedState().orElseThrow().energy().totalHartree();}
    @Test void fragmentExchangeAndTranslationPreserveBothEnergies()throws Exception {
        var pair=InteractionReceiptReplay.pairs().get("water_dimer");var ref=run("water_dimer");
        var swap=InteractionEnergyCalculator.calculate(new FragmentPair(pair.b(),pair.a()));
        assertEquals(ref.uncorrectedHartree().orElseThrow(),swap.uncorrectedHartree().orElseThrow(),1e-9);
        assertEquals(ref.counterpoiseHartree().orElseThrow(),swap.counterpoiseHartree().orElseThrow(),1e-9);
        var moved=new FragmentPair(move(pair.a()),move(pair.b()));var r=InteractionEnergyCalculator.calculate(moved);
        assertEquals(ref.uncorrectedHartree().orElseThrow(),r.uncorrectedHartree().orElseThrow(),1e-9);
        assertEquals(ref.counterpoiseHartree().orElseThrow(),r.counterpoiseHartree().orElseThrow(),1e-9);
        assertNotEquals(ref.receipt().receiptHash(),r.receipt().receiptHash());
    }
    private static MolecularFragment move(MolecularFragment f) {
        var nuclei=f.system().nuclei().stream().map(n->{var p=n.centerBohr();return new NuclearCenter(new Point3D(-p.y()+3,p.x()-2,p.z()+.5),n.charge());}).toList();
        return new MolecularFragment(f.id(),new QuantumSystem(nuclei,f.system().molecularCharge(),1));
    }
    @Test void nonconvergedComponentsNeverProduceInteractionValues()throws Exception {
        var r=InteractionEnergyCalculator.calculate(InteractionReceiptReplay.pairs().get("water_dimer"),1);
        assertEquals(InteractionEnergyResult.Status.SCF_NOT_CONVERGED,r.status());assertTrue(r.uncorrectedHartree().isEmpty());assertTrue(r.counterpoiseHartree().isEmpty());
        assertTrue(r.components().stream().allMatch(c->c.calculation().status()==RhfScfResult.Status.MAX_ITERATIONS));
    }
    @Test void allReceiptsReplayInFreshJava21Processes()throws Exception {
        for(String name:InteractionReceiptReplay.pairs().keySet()) {
            var expected=InteractionReceiptReplay.receipts(run(name)).getBytes(StandardCharsets.UTF_8);var output=Files.createTempFile("aether-interaction-",".receipt");
            try {
                var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-Xmx512m","-cp",System.getProperty("java.class.path"),InteractionReceiptReplay.class.getName(),name)
                        .redirectOutput(output.toFile()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
                assertEquals(0,process.waitFor());assertArrayEquals(expected,Files.readAllBytes(output),name);
                System.out.println("INTERACTION_REPLAY="+name+" SHA256="+ContentHash.sha256(expected));
            }finally{Files.deleteIfExists(output);}
        }
    }
    @Test void distantNeutralFragmentsApproachZero()throws Exception {
        var a=new QuantumSystem(List.of(new NuclearCenter(new Point3D(0,0,0),1),new NuclearCenter(new Point3D(1.4,0,0),1)),0,1);
        var b=new QuantumSystem(List.of(new NuclearCenter(new Point3D(1000,0,0),1),new NuclearCenter(new Point3D(1001.4,0,0),1)),0,1);
        var r=InteractionEnergyCalculator.calculate(new FragmentPair(new MolecularFragment("A",a),new MolecularFragment("B",b)));
        assertEquals(0,r.uncorrectedHartree().orElseThrow(),1e-9);assertEquals(0,r.counterpoiseHartree().orElseThrow(),1e-9);
    }
}
