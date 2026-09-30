package totah.lab.aether;

import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import totah.lab.aether.basis.Sto3gBasis;
import totah.lab.aether.matrix.*;
import totah.lab.aether.model.*;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.gaia.geometry.Point3D;
import static org.junit.jupiter.api.Assertions.*;

class AetherKohnShamTest {
    private static final Map<String,KohnShamResult> RUNS=new TreeMap<>();
    private static KohnShamResult run(String name)throws Exception {
        if(!RUNS.containsKey(name)) {
            System.out.println("KS_START="+name);var r=KsReceiptReplay.solve(name,120,590);RUNS.put(name,r);
            System.out.println("KS_DONE="+name+" status="+r.status()+" iterations="+r.receipt().trajectory().size()+" reason="+r.receipt().reason());
        }
        return RUNS.get(name);
    }
    @Test void independentKsStatesAndGridConvergenceLadder()throws Exception {
        String key="";KohnShamResult r=null;var errors=new TreeMap<String,Double>();int entries=0;var summaries=new ArrayList<String>();
        for(var line:DftFixtures.read("ks.csv").lines().skip(1).toList()) {
            var c=line.split(",");String current=c[0]+":"+c[1]+":"+c[2];int radial=Integer.parseInt(c[1]),angular=Integer.parseInt(c[2]);
            if(!key.equals(current)) {
                r=radial==120&&angular==590?run(c[0]):KsReceiptReplay.solve(c[0],radial,angular);key=current;
                assertEquals(RhfScfResult.Status.CONVERGED,r.status(),key+" "+r.receipt().reason());
                var state=r.convergedState().orElseThrow();String summary=key+","+state.xc().integratedElectrons()+","+state.xc().energyHartree()+","+state.energy().totalHartree()+","+r.receipt().trajectory().size();
                summaries.add(summary);System.out.println("KS_GRID="+summary);
            }
            var s=r.convergedState().orElseThrow();int i=Integer.parseInt(c[4]),j=Integer.parseInt(c[5]);
            double actual=switch(c[3]) {
                case "electrons"->s.xc().integratedElectrons();case "exc"->s.xc().energyHartree();case "electronic"->s.energy().electronicHartree();case "total"->s.energy().totalHartree();case "nuclear"->s.energy().nuclearHartree();
                case "density"->s.density().get(i,j);case "vxc"->s.xc().potential().get(i,j);case "fock"->s.fock().get(i,j);case "energies"->s.orbitals().energy(i);default->throw new AssertionError(c[3]);
            };
            double expected=Double.parseDouble(c[6]);errors.merge(c[3],Math.abs(actual-expected),Math::max);entries++;assertEquals(expected,actual,1e-8,key+" "+c[3]+" "+i+","+j);
        }
        System.out.println("KS_REFERENCE_ENTRIES="+entries+" ERRORS="+errors);
        for(String name:new String[]{"h2o","n2","h2s"}) {
            var levels=summaries.stream().filter(v->v.startsWith(name+":" )).map(v->{var c=v.split(",");return new double[]{Double.parseDouble(c[1]),Double.parseDouble(c[2]),Double.parseDouble(c[3])};}).toList();
            assertEquals(4,levels.size());double exact=name.equals("h2o")?10:name.equals("n2")?14:18;
            double electronError=Math.abs(levels.get(3)[0]-exact);assertTrue(electronError<5e-8);
            assertTrue(electronError<Math.abs(levels.get(0)[0]-exact));
            double xcChange=Math.abs(levels.get(3)[1]-levels.get(2)[1]),energyChange=Math.abs(levels.get(3)[2]-levels.get(2)[2]);
            assertTrue(xcChange<2e-6);assertTrue(energyChange<2e-6);
            for(int q=1;q<=2;q++)assertTrue(Math.abs(levels.get(3)[q]-levels.get(2)[q])<Math.abs(levels.get(1)[q]-levels.get(0)[q]));
            System.out.println("KS_GRID_CONVERGENCE="+name+" finest_electron_error="+electronError+" last_xc_change="+xcChange+" last_energy_change="+energyChange);
        }
        var out=Path.of("target/ks-validation");Files.createDirectories(out);Files.write(out.resolve("grid-ladder.csv"),summaries);
        for(var e:RUNS.entrySet())Files.writeString(out.resolve(e.getKey()+".receipt"),e.getValue().receipt().toString());
        assertEquals(8,RUNS.size());
    }
    @Test void pureLdaFockContainsNoHfExchangeAndHasConsistentEnergy()throws Exception {
        var state=run("h2").convergedState().orElseThrow();var p=state.density();var h=new CoreHamiltonianCalculator(p.system(),p.functions()).calculate();
        var jk=JkCalculator.calculate(p,new ElectronRepulsionCalculator(p.system(),p.functions()).calculate());
        double one=0,hartree=0,exchangeNorm=0;
        for(int i=0;i<p.size();i++)for(int j=0;j<p.size();j++) {
            assertEquals(h.get(i,j)+jk.coulomb().get(i,j)+state.xc().potential().get(i,j),state.fock().get(i,j),0);
            one+=p.get(i,j)*h.get(i,j);hartree+=p.get(i,j)*jk.coulomb().get(i,j);exchangeNorm+=Math.abs(jk.exchange().get(i,j));
        }
        assertTrue(exchangeNorm>1);assertEquals(one+.5*hartree+state.xc().energyHartree(),state.energy().electronicHartree(),0);
        var wrong=DensityMatrix.fromRowMajor(p.system(),p.functions(),Collections.nCopies(p.size()*p.size(),0.0));
        assertThrows(IllegalArgumentException.class,()->KsFockMatrix.assemble(wrong,h,jk.coulomb(),state.xc()));
    }
    @Test void capAndUnsupportedAndCoincidentSystemsFailClosed()throws Exception {
        var s=DiisReceiptReplay.systems().get("h2");var basis=Sto3gBasis.load().forSystem(s);
        var cap=KohnShamCalculator.solve(s,basis,new GridDefinition(40,110),LdaFunctional.EXCHANGE_PZ81,1);
        assertEquals(RhfScfResult.Status.MAX_ITERATIONS,cap.status());assertTrue(cap.convergedState().isEmpty());
        var odd=new QuantumSystem(s.nuclei(),1,2);assertEquals(RhfScfResult.Status.UNSUPPORTED_SYSTEM,KohnShamCalculator.solve(odd,basis,new GridDefinition(40,110),LdaFunctional.EXCHANGE_PZ81).status());
        var same=new QuantumSystem(List.of(s.nuclei().getFirst(),s.nuclei().getFirst()),0,1);
        assertEquals(RhfScfResult.Status.NUMERICAL_FAILURE,KohnShamCalculator.solve(same,Sto3gBasis.load().forSystem(same),new GridDefinition(40,110),LdaFunctional.EXCHANGE_PZ81).status());
    }
    @Test void basisAndAtomOrderPermutation()throws Exception {
        var ref=run("h2o").convergedState().orElseThrow();var p=ref.density();var grid=new GridDefinition(120,590);
        var reversed=KohnShamCalculator.solve(p.system(),p.functions().reversed(),grid,LdaFunctional.EXCHANGE_PZ81).convergedState().orElseThrow();
        assertEquals(ref.energy().totalHartree(),reversed.energy().totalHartree(),1e-8);
        for(int i=0;i<p.size();i++)for(int j=0;j<p.size();j++)assertEquals(p.get(i,j),reversed.density().get(p.size()-1-i,p.size()-1-j),1e-8);
        var atoms=new QuantumSystem(p.system().nuclei().reversed(),0,1);var basis=Sto3gBasis.load().forSystem(atoms);
        var changed=KohnShamCalculator.solve(atoms,basis,grid,LdaFunctional.EXCHANGE_PZ81).convergedState().orElseThrow();
        assertEquals(ref.energy().totalHartree(),changed.energy().totalHartree(),1e-8);
        int[] map=new int[basis.size()];
        for(int i=0;i<basis.size();i++)for(int j=0;j<p.size();j++) {
            var a=basis.get(i);var b=p.functions().get(j);var x=a.terms().getFirst().primitive().centerBohr();var y=b.terms().getFirst().primitive().centerBohr();
            if(a.angularMomentum()==b.angularMomentum()&&x.x()==y.x()&&x.y()==y.y()&&x.z()==y.z()&&a.terms().getFirst().primitive().exponent()==b.terms().getFirst().primitive().exponent())map[i]=j;
        }
        for(int i=0;i<p.size();i++)for(int j=0;j<p.size();j++)assertEquals(p.get(map[i],map[j]),changed.density().get(i,j),1e-8);
        System.out.println("KS_BASIS_AND_ATOM_ORDER=PASS");
    }
    @Test void quantifyGeneralRotationAndTranslation()throws Exception {
        double rotation=0,translation=0;
        for(String name:new String[]{"h2o","n2","h2s"}) {
            var reference=run(name).convergedState().orElseThrow();var system=reference.density().system();
            for(boolean rotate:new boolean[]{false,true}) {
                var nuclei=system.nuclei().stream().map(n->{var p=n.centerBohr();double x=p.x(),y=p.y(),z=p.z();
                    if(rotate){double norm=Math.sqrt(14),a=1/norm,b=2/norm,c=3/norm,cos=Math.cos(.513),sin=Math.sin(.513),dot=a*x+b*y+c*z;
                        double xx=x*cos+(b*z-c*y)*sin+a*dot*(1-cos),yy=y*cos+(c*x-a*z)*sin+b*dot*(1-cos),zz=z*cos+(a*y-b*x)*sin+c*dot*(1-cos);x=xx;y=yy;z=zz;}
                    else{x+=2.25;y-=1.5;z+=.75;}return new NuclearCenter(new Point3D(x,y,z),n.charge());}).toList();
                var moved=new QuantumSystem(nuclei,system.molecularCharge(),1);var result=KohnShamCalculator.solve(moved,Sto3gBasis.load().forSystem(moved),new GridDefinition(120,590),LdaFunctional.EXCHANGE_PZ81);
                assertEquals(RhfScfResult.Status.CONVERGED,result.status());var state=result.convergedState().orElseThrow();
                double error=Math.abs(state.energy().totalHartree()-reference.energy().totalHartree());
                if(rotate){rotation=Math.max(rotation,error);assertTrue(error<1e-4,"Finite grid rotation error "+name+" "+error);}else{translation=Math.max(translation,error);assertTrue(error<1e-8);}
                System.out.println("KS_MOTION="+name+" rotation="+rotate+" energy_error="+error+" electron_error="+Math.abs(state.xc().integratedElectrons()-reference.xc().integratedElectrons())+" xc_error="+Math.abs(state.xc().energyHartree()-reference.xc().energyHartree()));
            }
        }
        System.out.println("KS_MAX_ROTATION="+rotation+" KS_MAX_TRANSLATION="+translation);
    }
    @Test void freshJava21Receipts()throws Exception {
        for(String name:DftFixtures.densities().keySet()) {
            var expected=run(name).receipt().toString().getBytes(StandardCharsets.UTF_8);var output=Files.createTempFile("aether-ks-",".receipt");
            try {
                var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-Xmx512m","-cp",System.getProperty("java.class.path"),KsReceiptReplay.class.getName(),name,"120","590").redirectOutput(output.toFile()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
                assertEquals(0,process.waitFor());assertArrayEquals(expected,Files.readAllBytes(output),name);System.out.println("KS_REPLAY="+name+" SHA256="+ContentHash.sha256(expected));
            }finally{Files.deleteIfExists(output);}
        }
    }
}
