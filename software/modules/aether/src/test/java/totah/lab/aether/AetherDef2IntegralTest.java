package totah.lab.aether;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import totah.lab.aether.basis.*;
import totah.lab.aether.integral.*;
import totah.lab.aether.matrix.*;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.gaia.geometry.Point3D;
import static org.junit.jupiter.api.Assertions.*;

class AetherDef2IntegralTest {
    @Test void freshLibcintIntegralsAndAoValues() throws Exception {
        var systems=DiisReceiptReplay.systems();var library=Def2SvpBasis.load();
        var bases=new HashMap<String,List<ContractedGaussian>>();
        var errors=new TreeMap<String,Double>();int entries=0;
        byte[] bytes;try(var in=getClass().getResourceAsStream("reference/def2-integrals.csv")){bytes=Objects.requireNonNull(in).readAllBytes();}
        assertEquals("4abf00e2277c03a10e08dab5ec6f350373502d29e23d24fc3021937933fa7c0b",ContentHash.sha256(bytes));
        var ao=AoGrid.class.getDeclaredMethod("evaluate",ContractedGaussian.class,Point3D.class);ao.setAccessible(true);
        var points=List.of(new Point3D(.13,-.27,.41),new Point3D(1.1,.7,-.3),new Point3D(-2.1,.4,1.3),new Point3D(0,0,0));
        MolecularGrid grid=null;
        String previous="";long started=System.nanoTime();
        for(var line:new String(bytes,StandardCharsets.US_ASCII).lines().skip(1).toList()) {
            var c=line.split(",");String name=c[0];var system=systems.get(name);
            var b=bases.computeIfAbsent(name,key->library.forSystem(system));
            if(!name.equals(previous)){System.out.println("M13 integral "+name+" basis="+b.size()+" elapsed="+(System.nanoTime()-started)/1e9);previous=name;grid=null;}
            int i=Integer.parseInt(c[2]),j=Integer.parseInt(c[3]),k=Integer.parseInt(c[4]),l=Integer.parseInt(c[5]);
            double value=switch(c[1]) {
                case "S"->OverlapMatrix.compute(List.of(b.get(i),b.get(j))).get(0,1);
                case "T"->KineticMatrix.compute(List.of(b.get(i),b.get(j))).get(0,1);
                case "V"->NuclearAttractionMatrix.compute(List.of(b.get(i),b.get(j)),system.nuclei()).get(0,1);
                case "ERI"->ElectronRepulsionIntegral.between(b.get(i),b.get(j),b.get(k),b.get(l));
                case "GRID_AO"->{if(grid==null)grid=MolecularGrid.build(system,new GridDefinition(120,590));yield (double)ao.invoke(null,b.get(j),grid.point(i).coordinateBohr());}
                case "AO"->(double)ao.invoke(null,b.get(j),points.get(i));
                default->throw new AssertionError(c[1]);
            };
            double expected=Double.parseDouble(c[6]),error=Math.abs(expected-value);
            assertEquals(expected,value,2e-11,line);errors.merge(c[1],error,Math::max);entries++;
        }
        assertEquals(13,bases.size());assertEquals(133020,entries);
        System.out.println("M13 integral entries="+entries+" errors="+errors+" seconds="+(System.nanoTime()-started)/1e9);
    }
    @Test void normalizedCartesianDAndBoysOrders() throws Exception {
        for(var angular:CartesianAngularMomentum.values())if(angular.x()+angular.y()+angular.z()==2) {
            var p=new PrimitiveGaussian(new Point3D(.2,-.3,.7),.8);
            assertEquals(1,CartesianIntegrals.overlap(p,angular,p,angular),2e-14);
            // Independent one-dimensional Gaussian moments for squared versus mixed d monomials.
            boolean squared=angular.x()==2||angular.y()==2||angular.z()==2;
            assertEquals((squared?13.0/6:3.5)*.8,CartesianIntegrals.kinetic(p,angular,p,angular),3e-14);
            assertEquals(-16*StrictMath.sqrt(1.6)/(15*StrictMath.sqrt(StrictMath.PI)),
                    CartesianIntegrals.attraction(p,angular,p,angular,List.of(new totah.lab.aether.model.NuclearCenter(p.centerBohr(),1))),3e-14);
            assertTrue(CartesianIntegrals.repulsion(p,angular,p,angular,p,angular,p,angular)>0);
        }
        for(int n=0;n<=8;n++)assertEquals(1.0/(2*n+1),BoysFunction.value(n,0),1e-15);
        assertThrows(IllegalArgumentException.class,()->BoysFunction.value(9,1));
        for(int z:new int[]{1,6,7,8,15,16,17}) {
            var b=Def2SvpBasis.load().atBohr(z,new Point3D(0,0,0));assertEquals(z==1?5:z<15?15:19,b.size());
            for(var f:b)assertEquals(1,OverlapMatrix.compute(List.of(f)).get(0,0),2e-13);
        }
    }
}
