package totah.lab.aether.matrix;

import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.aether.basis.*;
import totah.lab.aether.model.*;
import totah.lab.gaia.geometry.Point3D;
import static org.junit.jupiter.api.Assertions.*;

class AetherSemiDirectCacheTest {
    @TempDir Path root;
    private static QuantumSystem water(){return new QuantumSystem(List.of(new NuclearCenter(new Point3D(0,0,0),8),new NuclearCenter(new Point3D(0,1.4,1.1),1),new NuclearCenter(new Point3D(0,-1.4,1.1),1)),0,1);}
    @Test void binaryCanonicalValuesAndAllPermutationsMatchPacked()throws Exception {
        var system=water();var basis=BasisFamily.DEF2_SVP.forSystem(system);var reference=new ElectronRepulsionCalculator(system,basis).calculate();
        var created=EriDiskCache.openOrCreate(root,basis,2);assertTrue(created.generated());assertEquals(reference.uniqueQuartetCount(),created.eriGenerationCount());
        try(var reader=created.cache().reader()) {
            var pairs=new EriDiskCache.Pairs(basis.size());long[] visited={0};
            var scan=reader.scan((first,buffer)->{int a=EriDiskCache.pairRow(first),b=(int)(first-EriDiskCache.triangle(a));
                while(buffer.hasRemaining()){
                    assertEquals(reference.get(pairs.i[a],pairs.j[a],pairs.i[b],pairs.j[b]),buffer.getDouble(),0);
                    assertEquals(8*visited[0],EriDiskCache.offset(pairs.j[b],pairs.i[b],pairs.j[a],pairs.i[a]));visited[0]++;
                    if(++b>a){a++;b=0;}
                }});
            assertEquals(reference.uniqueQuartetCount(),visited[0]);assertEquals(created.cache().payloadBytes(),scan.bytes());
        }
        var again=EriDiskCache.openOrCreate(root,basis,1);assertFalse(again.generated());assertEquals(0,again.eriGenerationCount());
    }
    @Test void pageBoundaryReconstructsCanonicalOrdering()throws Exception {
        var basis=new ArrayList<ContractedGaussian>();
        for(int i=0;i<27;i++)basis.add(new ContractedGaussian(List.of(new GaussianTerm(new PrimitiveGaussian(new Point3D(i*.01,0,0),.3+i*.01),1))));
        var reference=new ElectronRepulsionCalculator(water(),basis).calculate();var cache=EriDiskCache.openOrCreate(root,basis,2).cache();
        try(var reader=cache.reader()) {
            var pairs=new EriDiskCache.Pairs(27);var scan=reader.scan((first,buffer)->{
                int a=EriDiskCache.pairRow(first),b=(int)(first-EriDiskCache.triangle(a));
                while(buffer.hasRemaining()){assertEquals(reference.get(pairs.i[a],pairs.j[a],pairs.i[b],pairs.j[b]),buffer.getDouble(),0);if(++b>a){a++;b=0;}}
            });assertEquals(2,scan.pages());
        }
    }
    @Test void identityRejectsChangedBasisCenterPrimitiveCoefficientAngularMomentumAndOrder()throws Exception {
        var basis=BasisFamily.STO_3G.forSystem(water());var cache=EriDiskCache.openOrCreate(root,basis,1).cache();
        var reversed=new ArrayList<>(basis);Collections.reverse(reversed);assertNotEquals(cache.identity(),EriDiskCache.identity(reversed));
        assertThrows(java.io.IOException.class,()->EriDiskCache.open(cache.directory(),reversed));
        var f=basis.getFirst();var t=f.terms().getFirst();
        var angular=new ArrayList<>(basis);angular.set(0,new ContractedGaussian(f.terms(),CartesianAngularMomentum.PX));
        assertNotEquals(cache.identity(),EriDiskCache.identity(angular));
        assertThrows(java.io.IOException.class,()->EriDiskCache.open(cache.directory(),angular));
        for(int kind=0;kind<3;kind++) {
            var terms=new ArrayList<>(f.terms());var p=t.primitive();
            var center=kind==0?new Point3D(p.centerBohr().x()+.1,p.centerBohr().y(),p.centerBohr().z()):p.centerBohr();
            if(kind==0)for(int i=0;i<terms.size();i++){var old=terms.get(i);terms.set(i,new GaussianTerm(new PrimitiveGaussian(center,old.primitive().exponent()),old.coefficient()));}
            else terms.set(0,new GaussianTerm(new PrimitiveGaussian(center,p.exponent()+(kind==1?.01:0)),t.coefficient()+(kind==2?.01:0)));
            var changed=new ArrayList<>(basis);changed.set(0,new ContractedGaussian(terms,f.angularMomentum()));
            assertNotEquals(cache.identity(),EriDiskCache.identity(changed));assertThrows(java.io.IOException.class,()->EriDiskCache.open(cache.directory(),changed));
        }
    }
    @Test void corruptionTruncationAndFormatChangesFailClosed()throws Exception {
        var basis=BasisFamily.STO_3G.forSystem(water());var cache=EriDiskCache.openOrCreate(root,basis,1).cache();var file=cache.directory().resolve("eri.bin");
        try(var reader=cache.reader();var channel=FileChannel.open(file,StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(new byte[]{42}),0);assertThrows(java.io.IOException.class,()->reader.scan((a,b)->{}));
        }
        assertThrows(java.io.IOException.class,()->EriDiskCache.open(cache.directory(),basis));
        try(var channel=FileChannel.open(file,StandardOpenOption.WRITE)){channel.truncate(8);}
        assertThrows(java.io.IOException.class,()->EriDiskCache.openOrCreate(root,basis,1));
        var other=EriDiskCache.openOrCreate(root.resolve("other"),basis,1).cache();
        Files.writeString(other.directory().resolve("manifest.txt"),"wrong format\n");
        assertThrows(java.io.IOException.class,()->EriDiskCache.open(other.directory(),basis));
    }
    @Test void simultaneousCreatorsPublishOnceAndIgnoreIncompleteTemporaryDirectories()throws Exception {
        var basis=BasisFamily.STO_3G.forSystem(water());Files.createDirectory(root.resolve(EriDiskCache.identity(basis)+".partial-interrupted"));
        try(var executor=Executors.newFixedThreadPool(2)) {
            var a=executor.submit(()->EriDiskCache.openOrCreate(root,basis,1));var b=executor.submit(()->EriDiskCache.openOrCreate(root,basis,1));
            var x=a.get();var y=b.get();assertNotEquals(x.generated(),y.generated());assertEquals(x.cache().identity(),y.cache().identity());
        }
    }
    @Test void pagedAndMappedJkMatchPackedForArbitraryDensityAndRejectOtherSystem()throws Exception {
        var system=water();var basis=BasisFamily.STO_3G.forSystem(system);int n=basis.size();var values=new ArrayList<Double>();
        for(int i=0;i<n;i++)for(int j=0;j<n;j++)values.add((i==j?.8:.1)/(i+j+1));
        var density=DensityMatrix.fromRowMajor(system,basis,values);var packed=new ElectronRepulsionCalculator(system,basis).calculate();var reference=JkCalculator.calculate(density,packed);
        var cache=EriDiskCache.openOrCreate(root,basis,2).cache();String hash=null;
        for(boolean mapped:new boolean[]{false,true})try(var jk=new SemiDirectJk(system,cache,mapped)) {
            var result=jk.calculate(density);if(hash==null)hash=result.receiptHash();else assertEquals(hash,result.receiptHash());
            for(int i=0;i<n;i++)for(int j=0;j<n;j++){assertEquals(reference.coulomb().get(i,j),result.coulomb().get(i,j),1e-12);assertEquals(reference.exchange().get(i,j),result.exchange().get(i,j),1e-12);}
            var other=new QuantumSystem(system.nuclei(),2,1);
            assertThrows(IllegalArgumentException.class,()->jk.calculate(DensityMatrix.fromRowMajor(other,basis,values)));
        }
    }
}
