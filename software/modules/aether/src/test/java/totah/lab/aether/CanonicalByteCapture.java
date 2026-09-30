package totah.lab.aether;

import java.io.ByteArrayOutputStream;
import java.security.*;
import java.util.*;

/** Test-only observation of the bytes actually delivered to SHA-256; no production hook. */
public final class CanonicalByteCapture implements AutoCloseable {
    private static final String NAME="AetherTestByteCapture";
    private static final ThreadLocal<List<byte[]>> CAPTURE=new ThreadLocal<>();
    private static final Provider DELEGATE;
    static {
        try { DELEGATE=MessageDigest.getInstance("SHA-256").getProvider(); }
        catch(NoSuchAlgorithmException e) { throw new ExceptionInInitializerError(e); }
    }
    private final List<byte[]> streams=new ArrayList<>();
    public CanonicalByteCapture() {
        if(CAPTURE.get()!=null)throw new IllegalStateException("Nested capture");
        var provider=new Provider(NAME,"1.0","Test-only canonical-byte observer") {};
        provider.put("MessageDigest.SHA-256",Digest.class.getName());
        if(Security.insertProviderAt(provider,1)!=1)throw new IllegalStateException("Capture provider not installed");
        CAPTURE.set(streams);
    }
    public boolean contains(byte[] expected) { return streams.stream().anyMatch(bytes->Arrays.equals(expected,bytes)); }
    @Override public void close() { Security.removeProvider(NAME);CAPTURE.remove();streams.clear(); }
    public static final class Digest extends MessageDigestSpi {
        private final MessageDigest delegate;
        private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        public Digest() {
            try { delegate=MessageDigest.getInstance("SHA-256",DELEGATE); }
            catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        }
        @Override protected void engineUpdate(byte value) {delegate.update(value);if(CAPTURE.get()!=null)bytes.write(value);}
        @Override protected void engineUpdate(byte[] value,int offset,int length) {
            delegate.update(value,offset,length);if(CAPTURE.get()!=null)bytes.write(value,offset,length);
        }
        @Override protected byte[] engineDigest() {
            if(CAPTURE.get()!=null)CAPTURE.get().add(bytes.toByteArray());
            bytes.reset();return delegate.digest();
        }
        @Override protected void engineReset() {bytes.reset();delegate.reset();}
        @Override protected int engineGetDigestLength() {return 32;}
    }
}
