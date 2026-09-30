package totah.lab.aether.matrix;

/** Explicit unpruned quadrature; no implicit production-quality designation. */
public record GridDefinition(int radialPoints,int angularPoints) {
    public GridDefinition {
        if(radialPoints!=40&&radialPoints!=80&&radialPoints!=120&&radialPoints!=160)throw new IllegalArgumentException("Unsupported radial rule size");
        if(angularPoints!=110&&angularPoints!=302&&angularPoints!=590&&angularPoints!=974)throw new IllegalArgumentException("Unsupported Lebedev rule size");
    }
    public int angularOrder(){return switch(angularPoints){case 110->17;case 302->29;case 590->41;case 974->53;default->throw new AssertionError();};}
    public String protocol(){return "aether-grid-v1;bohr;Gauss-Legendre-[0,1];r=u/(1-u);scale=1bohr;radial="+radialPoints
            +";Lebedev-points="+angularPoints+";Lebedev-order="+angularOrder()+";Becke-cubic-3;no-radius-adjustment;no-pruning;atom-radial-angular-order;StrictMath";}
}
