package totah.lab.prometheus.molecular;

public record NuclearCharge(int atomicNumber){public NuclearCharge{if(atomicNumber<1||atomicNumber>118)throw new IllegalArgumentException("atomic number must be 1..118");}}
