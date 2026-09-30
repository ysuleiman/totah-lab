package totah.lab.aether.matrix;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.integral.PreparedRepulsion;
import totah.lab.aether.provenance.ContentHash;

/** Exact immutable ERI backing store. No molecular state and no full payload in heap. */
public final class EriDiskCache {
    public static final int PAGE_DOUBLES=65536;
    public static final String FORMAT="aether-eri-cache-14.1-1;binary64-big-endian;canonical-pair-triangle;page=65536";
    private final Path directory;
    private final List<ContractedGaussian> basis;
    private final String identity,payloadHash,indexHash;
    private final long slots;
    private EriDiskCache(Path directory,List<ContractedGaussian> basis,String identity,String payloadHash,String indexHash) {
        this.directory=directory;this.basis=List.copyOf(basis);this.identity=identity;
        this.payloadHash=payloadHash;this.indexHash=indexHash;slots=triangle(pairCount(basis.size()));
    }
    public String identity(){return identity;}
    public String payloadSha256(){return payloadHash;}
    public int dimension(){return basis.size();}
    public long uniqueSlots(){return slots;}
    public long payloadBytes(){return Math.multiplyExact(8,slots);}
    public Path directory(){return directory;}
    List<ContractedGaussian> basis(){return basis;}
    public static String identity(List<ContractedGaussian> functions) {
        var basis=List.copyOf(functions);var hash=ContentHash.accumulator().line(FORMAT)
                .line(IntegralMatrixData.basisGeometryHash(basis)).line("normalized-Cartesian;bohr;hartree")
                .line(IntegralMatrixData.protocol(ElectronRepulsionTensor.PROTOCOL,basis)).line("PreparedRepulsion-frozen-M14");
        for(var f:basis) {
            hash.line(ContentHash.number(f.normalization()));
            for(var term:f.terms())hash.line(ContentHash.number(f.angularMomentum().normalization(term.primitive())));
        }
        return hash.finish();
    }
    static long triangle(long n){return Math.multiplyExact(n,Math.addExact(n,1))/2;}
    static int pairCount(int n){return Math.toIntExact(triangle(n));}
    public static long offset(int i,int j,int k,int l) {
        if(Math.min(Math.min(i,j),Math.min(k,l))<0)throw new IllegalArgumentException("Negative AO index");
        long a=triangle(Math.max(i,j))+Math.min(i,j),b=triangle(Math.max(k,l))+Math.min(k,l);
        return Math.multiplyExact(8,Math.addExact(triangle(Math.max(a,b)),Math.min(a,b)));
    }
    /** Serializes same-JVM creators; the file lock also serializes independent JVMs. */
    public static synchronized Creation openOrCreate(Path root,List<ContractedGaussian> input,int workers)throws IOException {
        long start=System.nanoTime();var basis=List.copyOf(input);String id=identity(basis);
        if(workers<1||workers>32)throw new IllegalArgumentException("Invalid worker count");
        Files.createDirectories(root);Path dest=root.resolve(id);long[] times=new long[3];
        boolean generated=false;EriDiskCache verified=null;
        try(var lockChannel=FileChannel.open(root.resolve(id+".lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
            var lock=lockChannel.lock()) {
            if(!Files.exists(dest)) {
                Path temp=Files.createTempDirectory(root,id+".partial-");
                try {
                    generate(temp,basis,workers,times);
                    long t=System.nanoTime();verified=validate(temp,basis);times[2]+=System.nanoTime()-t;
                    forceDirectory(temp);
                    // Atomic publication is mandatory. Never expose a partially populated destination.
                    Files.move(temp,dest,StandardCopyOption.ATOMIC_MOVE);forceDirectory(root);generated=true;
                } finally {
                    if(Files.exists(temp))try(var files=Files.list(temp)) {
                        for(Path p:files.toList())Files.deleteIfExists(p);
                        Files.deleteIfExists(temp);
                    }
                }
            }
            long t=System.nanoTime();var cache=verified==null?validate(dest,basis):new EriDiskCache(dest,basis,id,verified.payloadHash,verified.indexHash);times[2]+=System.nanoTime()-t;
            return new Creation(cache,generated,generated?cache.uniqueSlots():0,times[0],times[1],times[2],System.nanoTime()-start);
        }
    }
    private static void forceDirectory(Path directory)throws IOException {
        try(var channel=FileChannel.open(directory,StandardOpenOption.READ)){channel.force(true);}
    }
    public static EriDiskCache open(Path directory,List<ContractedGaussian> basis)throws IOException {return validate(directory,List.copyOf(basis));}
    private static EriDiskCache validate(Path dir,List<ContractedGaussian> basis)throws IOException {
        Path manifest=dir.resolve("manifest.txt");
        if(!Files.isRegularFile(manifest)||Files.size(manifest)>4096)throw new IOException("Missing/oversized cache manifest");
        var lines=Files.readAllLines(manifest,StandardCharsets.US_ASCII);
        String id=identity(basis);long slots=triangle(pairCount(basis.size()));
        if(lines.size()!=7||!lines.get(0).equals(FORMAT)||!lines.get(1).equals(id)
                ||!lines.get(2).equals(Integer.toString(basis.size()))||!lines.get(3).equals(Long.toString(slots))
                ||!lines.get(6).equals("SCREENING_ONLY"))throw new IOException("Incompatible ERI cache identity/format");
        Path payload=dir.resolve("eri.bin"),index=dir.resolve("pages.sha256");
        if(Files.size(payload)!=Math.multiplyExact(8,slots)||Files.size(index)!=32*((slots+PAGE_DOUBLES-1)/PAGE_DOUBLES))
            throw new IOException("Truncated/oversized ERI cache");
        if(!shaFile(payload).equals(lines.get(4))||!shaFile(index).equals(lines.get(5)))throw new IOException("ERI cache checksum mismatch");
        return new EriDiskCache(dir,basis,id,lines.get(4),lines.get(5));
    }
    private record Page(long start,int count){}
    private record Values(double[] values,long nanos){}
    private static void generate(Path dir,List<ContractedGaussian> basis,int workers,long[] times)throws IOException {
        long begin=System.nanoTime();var evaluator=new PreparedRepulsion(basis);
        var sessions=ThreadLocal.withInitial(evaluator::newSession);var pairs=new Pairs(basis.size());
        long slots=triangle(pairs.i.length);var payloadDigest=digest();var indexDigest=digest();
        var buffer=ByteBuffer.allocate(PAGE_DOUBLES*8).order(ByteOrder.BIG_ENDIAN);
        long[] writes={0};
        Iterable<Page> pages=()->new Iterator<>() {
            long next;
            public boolean hasNext(){return next<slots;}
            public Page next(){if(!hasNext())throw new NoSuchElementException();var p=new Page(next,(int)Math.min(PAGE_DOUBLES,slots-next));next+=p.count;return p;}
        };
        try(var output=FileChannel.open(dir.resolve("eri.bin"),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
            var index=FileChannel.open(dir.resolve("pages.sha256"),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
            var pool=new ExactWorkers(workers)) {
            try {
                pool.forEachOrdered(pages,page->{
                    long t=System.nanoTime();var local=sessions.get();local.beginBlock();
                    try {
                        var values=new double[page.count];int a=pairRow(page.start),b=(int)(page.start-triangle(a));
                        for(int x=0;x<values.length;x++) {
                            double v=local.get(pairs.i[a],pairs.j[a],pairs.i[b],pairs.j[b]);
                            if(!Double.isFinite(v))throw new ArithmeticException("Nonfinite cache ERI");values[x]=v;
                            if(++b>a){a++;b=0;}
                        }
                        return new Values(values,System.nanoTime()-t);
                    }finally{local.endBlock();}
                },values->{
                    long t=System.nanoTime();buffer.clear();for(double v:values.values)buffer.putDouble(v);buffer.flip();
                    var pageDigest=digest();pageDigest.update(buffer.asReadOnlyBuffer());byte[] pageHash=pageDigest.digest();
                    payloadDigest.update(buffer.asReadOnlyBuffer());indexDigest.update(pageHash);
                    try {writeFully(output,buffer);writeFully(index,ByteBuffer.wrap(pageHash));}
                    catch(IOException ex){throw new UncheckedIOException(ex);}
                    writes[0]+=System.nanoTime()-t;
                });
            }catch(UncheckedIOException ex){throw ex.getCause();}
            output.force(true);index.force(true);
        }finally{sessions.remove();}
        times[0]=System.nanoTime()-begin;times[1]=writes[0]; // generation elapsed includes overlapping write work
        String metadata=String.join("\n",FORMAT,identity(basis),Integer.toString(basis.size()),Long.toString(slots),
                HexFormat.of().formatHex(payloadDigest.digest()),HexFormat.of().formatHex(indexDigest.digest()),"SCREENING_ONLY")+"\n";
        try(var file=FileChannel.open(dir.resolve("manifest.txt"),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
            writeFully(file,StandardCharsets.US_ASCII.encode(metadata));file.force(true);
        }
    }
    static int pairRow(long slot) {
        int row=(int)((StrictMath.sqrt(8.0*slot+1)-1)/2);
        while(triangle(row)>slot)row--;while(triangle(row+1)<=slot)row++;return row;
    }
    static final class Pairs {
        final int[] i,j;
        Pairs(int n){i=new int[pairCount(n)];j=new int[i.length];int x=0;for(int a=0;a<n;a++)for(int b=0;b<=a;b++){i[x]=a;j[x++]=b;}}
    }
    static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private static String shaFile(Path file)throws IOException {
        var hash=digest();try(var in=Files.newInputStream(file)){byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1)hash.update(bytes,0,n);}
        return HexFormat.of().formatHex(hash.digest());
    }
    private static void writeFully(FileChannel channel,ByteBuffer buffer)throws IOException {while(buffer.hasRemaining())channel.write(buffer);}
    public Reader reader()throws IOException{return new Reader(this,false);}
    // Mmap is package-private experimental instrumentation, not a production policy.
    Reader experimentalMappedReader()throws IOException{return new Reader(this,true);}
    public static final class Reader implements AutoCloseable {
        private final EriDiskCache cache;private final FileChannel payload;
        private final ByteBuffer buffer;private final MappedByteBuffer mapped;
        private final byte[] expected=new byte[32];
        private Reader(EriDiskCache cache,boolean mapping)throws IOException {
            this.cache=cache;payload=FileChannel.open(cache.directory.resolve("eri.bin"),StandardOpenOption.READ);
            try {
                if(mapping&&cache.payloadBytes()>Integer.MAX_VALUE)throw new IOException("Pilot mmap limited to one Java mapping");
                mapped=mapping?payload.map(FileChannel.MapMode.READ_ONLY,0,cache.payloadBytes()):null;
                buffer=mapping?null:ByteBuffer.allocate(PAGE_DOUBLES*8).order(ByteOrder.BIG_ENDIAN);
            }catch(IOException|RuntimeException ex){payload.close();throw ex;}
        }
        @FunctionalInterface interface PageConsumer {void accept(long first,ByteBuffer values);}
        synchronized Scan scan(PageConsumer consumer)throws IOException {
            long read=0,verify=0,consume=0,pages=0;payload.position(0);
            if(payload.size()!=cache.payloadBytes())throw new IOException("Cache size changed after validation");
            try(var checks=new BufferedInputStream(Files.newInputStream(cache.directory.resolve("pages.sha256")),8192)) {
                for(long first=0;first<cache.slots;first+=PAGE_DOUBLES) {
                    int bytes=(int)Math.min(PAGE_DOUBLES,cache.slots-first)*8;long t=System.nanoTime();ByteBuffer data;
                    if(mapped!=null)data=mapped.slice(Math.toIntExact(first*8),bytes).order(ByteOrder.BIG_ENDIAN);
                    else {buffer.clear().limit(bytes);while(buffer.hasRemaining())if(payload.read(buffer)<0)throw new EOFException("Truncated ERI page");buffer.flip();data=buffer;}
                    read+=System.nanoTime()-t;t=System.nanoTime();
                    if(checks.readNBytes(expected,0,32)!=32)throw new EOFException("Truncated checksum index");
                    var md=digest();md.update(data.asReadOnlyBuffer());
                    if(!MessageDigest.isEqual(expected,md.digest()))throw new IOException("ERI page checksum mismatch");
                    verify+=System.nanoTime()-t;t=System.nanoTime();consumer.accept(first,data);consume+=System.nanoTime()-t;pages++;
                }
                if(checks.read()!=-1)throw new IOException("Oversized checksum index");
            }
            return new Scan(cache.payloadBytes(),pages,read,verify,consume);
        }
        @Override public void close()throws IOException{payload.close();}
    }
    public record Scan(long bytes,long pages,long readNanos,long verificationNanos,long contractionNanos){}
    public record Creation(EriDiskCache cache,boolean generated,long eriGenerationCount,long generationNanos,long writeNanos,long verificationNanos,long totalNanos){}
}
