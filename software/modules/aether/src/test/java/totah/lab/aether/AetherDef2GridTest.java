package totah.lab.aether;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import totah.lab.aether.basis.*;
import totah.lab.aether.matrix.*;
import totah.lab.aether.model.*;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.gaia.geometry.Point3D;
import static org.junit.jupiter.api.Assertions.*;

class AetherDef2GridTest {
    static Point3D rotate(Point3D p) {
        double x=1/StrictMath.sqrt(14),y=2/StrictMath.sqrt(14),z=3/StrictMath.sqrt(14),c=StrictMath.cos(.513),s=StrictMath.sin(.513),dot=x*p.x()+y*p.y()+z*p.z();
        return new Point3D(c*p.x()+(1-c)*x*dot+s*(y*p.z()-z*p.y()),c*p.y()+(1-c)*y*dot+s*(z*p.x()-x*p.z()),c*p.z()+(1-c)*z*dot+s*(x*p.y()-y*p.x()));
    }
    static java.util.stream.Stream<String> referenceSystems() {
        return Arrays.stream(System.getProperty("aether.def2.gridSystem","h2o ch4 h2s").split(" "));
    }
    @ParameterizedTest @MethodSource("referenceSystems") void unchangedGridLadderAndRotatedReference(String name) throws Exception {
        var system=DiisReceiptReplay.systems().get(name);var basis=Def2SvpBasis.load();
        byte[] bytes;try(var in=getClass().getResourceAsStream("reference/def2-grid-"+name+".csv")){bytes=Objects.requireNonNull(in).readAllBytes();}
        try(var in=getClass().getResourceAsStream("reference/def2-grid-"+name+".sha256")){assertEquals(new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.US_ASCII).trim(),ContentHash.sha256(bytes));}
        double nativeEnergy=0;var evidence=new StringBuilder();
        for(var line:new String(bytes,StandardCharsets.US_ASCII).lines().skip(1).toList()) {
            var c=line.split(",");var actualSystem=c[0].equals("native")?system:new QuantumSystem(system.nuclei().stream().map(n->new NuclearCenter(rotate(n.centerBohr()),n.charge())).toList(),system.molecularCharge(),system.multiplicity());
            var result=KohnShamCalculator.solve(actualSystem,basis.forSystem(actualSystem),new GridDefinition(Integer.parseInt(c[1]),Integer.parseInt(c[2])),LdaFunctional.EXCHANGE_PZ81);
            assertEquals(RhfScfResult.Status.CONVERGED,result.status(),line);var state=result.convergedState().orElseThrow();
            assertEquals(Double.parseDouble(c[3]),state.energy().totalHartree(),1e-8,line);
            assertEquals(Double.parseDouble(c[4]),state.xc().energyHartree(),1e-8,line);
            assertEquals(Double.parseDouble(c[5]),state.xc().integratedElectrons(),1e-8,line);
            if(c[0].equals("native")&&c[1].equals("120"))nativeEnergy=state.energy().totalHartree();
            String row="M13 GRID "+name+" "+c[0]+" "+c[1]+"x"+c[2]+" E="+state.energy().totalHartree()+" Exc="+state.xc().energyHartree()+" electronError="+Math.abs(state.xc().integratedElectrons()-(system.nuclei().stream().mapToDouble(n->n.charge()).sum()-system.molecularCharge()))+" rotationDelta="+(c[0].equals("rotated")?state.energy().totalHartree()-nativeEnergy:0);
            evidence.append(row).append('\n');System.out.println(row);
        }
        var dir=Path.of("target/def2-validation");Files.createDirectories(dir);Files.writeString(dir.resolve(name+"-grid.txt"),evidence.toString());
    }
    @Test void rhfRotationTranslationAndBasisPermutation() throws Exception {
        var system=DiisReceiptReplay.systems().get("h2o");var lib=Def2SvpBasis.load();var basis=lib.forSystem(system);
        var nativeState=RhfScfCalculator.solve(system,basis,ScfPolicy.DIIS).convergedState().orElseThrow();
        var rotated=new QuantumSystem(system.nuclei().stream().map(n->new NuclearCenter(rotate(n.centerBohr()),n.charge())).toList(),system.molecularCharge(),system.multiplicity());
        var rotatedState=RhfScfCalculator.solve(rotated,lib.forSystem(rotated),ScfPolicy.DIIS).convergedState().orElseThrow();
        var translated=new QuantumSystem(system.nuclei().stream().map(n->new NuclearCenter(new Point3D(n.centerBohr().x()+.31,n.centerBohr().y()-1.27,n.centerBohr().z()+.44),n.charge())).toList(),system.molecularCharge(),system.multiplicity());
        var translatedState=RhfScfCalculator.solve(translated,lib.forSystem(translated),ScfPolicy.DIIS).convergedState().orElseThrow();
        var reversed=new ArrayList<>(basis);Collections.reverse(reversed);
        var permutedState=RhfScfCalculator.solve(system,reversed,ScfPolicy.DIIS).convergedState().orElseThrow();
        double e=nativeState.energy().totalHartree();
        assertEquals(e,rotatedState.energy().totalHartree(),1e-9);assertEquals(e,translatedState.energy().totalHartree(),1e-9);assertEquals(e,permutedState.energy().totalHartree(),1e-9);
        for(int a=0;a<basis.size();a++)for(int b=0;b<basis.size();b++)assertEquals(nativeState.density().get(a,b),permutedState.density().get(basis.size()-1-a,basis.size()-1-b),1e-8);
        System.out.println("M13 RHF INVARIANCE rotation="+(rotatedState.energy().totalHartree()-e)+" translation="+(translatedState.energy().totalHartree()-e)+" basis="+(permutedState.energy().totalHartree()-e));
    }
    @Test void translationBasisAndAtomPermutation() throws Exception {
        var system=DiisReceiptReplay.systems().get("h2o");var lib=Def2SvpBasis.load();var basis=lib.forSystem(system);var grid=new GridDefinition(120,590);
        var nativeResult=KohnShamCalculator.solve(system,basis,grid,LdaFunctional.EXCHANGE_PZ81);var state=nativeResult.convergedState().orElseThrow();
        var reversed=new ArrayList<>(basis);Collections.reverse(reversed);
        var permuted=KohnShamCalculator.solve(system,reversed,grid,LdaFunctional.EXCHANGE_PZ81).convergedState().orElseThrow();
        assertEquals(state.energy().totalHartree(),permuted.energy().totalHartree(),1e-9);
        for(int i=0;i<basis.size();i++)for(int j=0;j<basis.size();j++)assertEquals(state.density().get(i,j),permuted.density().get(basis.size()-1-i,basis.size()-1-j),1e-8);
        var atoms=new ArrayList<>(system.nuclei());Collections.reverse(atoms);
        var reordered=new QuantumSystem(atoms,system.molecularCharge(),system.multiplicity());
        var atomResult=KohnShamCalculator.solve(reordered,lib.forSystem(reordered),grid,LdaFunctional.EXCHANGE_PZ81).convergedState().orElseThrow();
        assertEquals(state.energy().totalHartree(),atomResult.energy().totalHartree(),1e-9);
        var translated=new QuantumSystem(system.nuclei().stream().map(n->new NuclearCenter(new Point3D(n.centerBohr().x()+.31,n.centerBohr().y()-1.27,n.centerBohr().z()+.44),n.charge())).toList(),system.molecularCharge(),system.multiplicity());
        var translatedResult=KohnShamCalculator.solve(translated,lib.forSystem(translated),grid,LdaFunctional.EXCHANGE_PZ81).convergedState().orElseThrow();
        assertEquals(state.energy().totalHartree(),translatedResult.energy().totalHartree(),1e-9);
        System.out.println("M13 INVARIANCE basis="+(permuted.energy().totalHartree()-state.energy().totalHartree())+" atoms="+(atomResult.energy().totalHartree()-state.energy().totalHartree())+" translation="+(translatedResult.energy().totalHartree()-state.energy().totalHartree()));
    }
}
