package totah.lab.aether.matrix;

import totah.lab.aether.provenance.ContentHash;

/** Bounded-memory hashing through existing canonical number/SHA-256 infrastructure. */
final class NumericalEvidenceHash {
    private final StringBuilder block=new StringBuilder();
    private final ContentHash.Accumulator hashes=ContentHash.accumulator();private int count;
    NumericalEvidenceHash(String domain){hashes.line(domain+";blocks-of-1024-canonical-numbers");}
    void add(double value){block.append(ContentHash.number(value)).append('\n');if(++count%1024==0)flush();}
    private void flush(){hashes.line(ContentHash.sha256(block.toString()));block.setLength(0);}
    String finish(){if(!block.isEmpty())flush();return hashes.append("count="+count).finish();}
}
