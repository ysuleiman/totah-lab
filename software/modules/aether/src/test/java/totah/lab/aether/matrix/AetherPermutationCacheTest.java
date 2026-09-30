package totah.lab.aether.matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.aether.basis.BasisFamily;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.model.NuclearCenter;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.gaia.geometry.Point3D;
import static org.junit.jupiter.api.Assertions.*;

class AetherPermutationCacheTest {
    @TempDir Path directory;
    static QuantumSystem water() {
        return new QuantumSystem(List.of(new NuclearCenter(new Point3D(0,0,0),8),
                new NuclearCenter(new Point3D(0,1.4,1.1),1),
                new NuclearCenter(new Point3D(0,-1.4,1.1),1)),0,1);
    }

    @Test void allFreshCacheEntriesAndContractionsMatchUnderPermutation() throws Exception {
        var system = water();
        var canonical = BasisFamily.DEF2_SVP.forSystem(system);
        var requested = new ArrayList<>(canonical);
        Collections.rotate(requested, 7);
        var cache = EriDiskCache.openOrCreate(directory, canonical, 2).cache();
        var fresh = EriDiskCache.openOrCreate(directory, requested, 2).cache();
        int n = requested.size();
        var values = new ArrayList<Double>();
        for (int i=0;i<n;i++) for (int j=0;j<n;j++) values.add(Math.cos(i+j)*.1 + (i==j?.5:0));
        var density = DensityMatrix.fromRowMajor(system,requested,values);
        try (var view = new PermutedCacheContractions(system,cache,requested,false);
             var jOnly = new PermutedCacheContractions(system,cache,requested,true);
             var reference = new SemiDirectJk(system,fresh);
             var freshJ = new CachedCoulomb(system,fresh);
             var reader = fresh.reader();
             var canonicalFile = FileChannel.open(cache.directory().resolve("eri.bin"),StandardOpenOption.READ)) {
            // Random single-value reads are deliberately confined to this small validation oracle.
            var pairs = new EriDiskCache.Pairs(n);
            var bytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN);
            reader.scan((first,data)->{
                int a=EriDiskCache.pairRow(first),b=(int)(first-EriDiskCache.triangle(a));
                while(data.hasRemaining()) {
                    long offset=view.canonicalOffset(pairs.i[a],pairs.j[a],pairs.i[b],pairs.j[b]);
                    bytes.clear();
                    try { while(bytes.hasRemaining()) {
                        int read=canonicalFile.read(bytes,offset+bytes.position());
                        if(read<0) throw new java.io.EOFException();
                    }} catch(java.io.IOException e) {throw new java.io.UncheckedIOException(e);}
                    bytes.flip();assertEquals(data.getDouble(),bytes.getDouble(),2e-13);
                    if(++b>a){a++;b=0;}
                }
            });
            var actual=view.calculateJk(density);var expected=reference.calculate(density);
            var actualJ=jOnly.calculateCoulomb(density);var expectedJ=freshJ.calculate(density);
            for(int i=0;i<n;i++)for(int j=0;j<n;j++) {
                assertEquals(expected.coulomb().get(i,j),actual.coulomb().get(i,j),1e-12);
                assertEquals(expected.exchange().get(i,j),actual.exchange().get(i,j),1e-12);
                assertEquals(expectedJ.coulomb().get(i,j),actualJ.coulomb().get(i,j),1e-12);
            }
            assertEquals(actual.receiptHash(),view.calculateJk(density).receiptHash());
            assertTrue(view.provenance().contains("bijection=VERIFIED"));
            assertEquals(cache.payloadBytes(),actual.scan().bytes());
            assertThrows(IllegalStateException.class,()->jOnly.calculateJk(density));
            assertThrows(IllegalArgumentException.class,()->view.calculateJk(
                    DensityMatrix.fromRowMajor(new QuantumSystem(system.nuclei(),2,1),requested,values)));
            assertThrows(IllegalArgumentException.class,()->view.calculateJk(
                    DensityMatrix.fromRowMajor(system,canonical,values)));
        }
    }

    @Test void ambiguousMissingAndChangedBasisDeclineReuse() throws Exception {
        var basis=BasisFamily.DEF2_SVP.forSystem(water());
        assertTrue(PermutedCacheContractions.bijection(basis,basis).isPresent());
        assertTrue(PermutedCacheContractions.bijection(basis,basis.subList(1,basis.size())).isEmpty());
        var duplicate=new ArrayList<>(basis);duplicate.set(1,duplicate.getFirst());
        assertTrue(PermutedCacheContractions.bijection(basis,duplicate).isEmpty());
        assertTrue(PermutedCacheContractions.bijection(duplicate,duplicate).isEmpty());
        List<ContractedGaussian> other=BasisFamily.STO_3G.forSystem(water());
        assertTrue(PermutedCacheContractions.bijection(basis,other).isEmpty());
    }
}
