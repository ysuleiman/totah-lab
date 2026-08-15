package totah.lab.prometheus.molecular;

public record SpinSector(int alphaElectrons,int betaElectrons,int multiplicity){
    public SpinSector{if(alphaElectrons<0||betaElectrons<0||multiplicity<1)throw new IllegalArgumentException("invalid spin sector");if(alphaElectrons-betaElectrons!=multiplicity-1)throw new IllegalArgumentException("multiplicity inconsistent with alpha/beta populations");}
    public int electronCount(){return alphaElectrons+betaElectrons;}
    public static SpinSector fromElectronCountAndMultiplicity(ElectronCount count,int multiplicity){int unpaired=multiplicity-1;if(unpaired>count.value()||((count.value()-unpaired)&1)!=0)throw new IllegalArgumentException("electron count and multiplicity parity are incompatible");int beta=(count.value()-unpaired)/2;return new SpinSector(beta+unpaired,beta,multiplicity);}
}
