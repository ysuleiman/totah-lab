package totah.lab.prometheus.validation;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Strict reader for selected authoritative H2O-13 monomer energy/force records. */
public final class H2o13ReferenceReader {
    public static final double EV_PER_HARTREE=27.211386245988;
    public static final double EV_PER_ANGSTROM_TO_HARTREE_PER_BOHR=0.019446903811488874;
    public static final double ABSOLUTE_MINIMUM_HARTREE=-76.4390;

    public Reference read(Path path,double oh1,double oh2,double angleDegrees)throws IOException {
        String prefix=String.format(Locale.ROOT,"OH1=%.8f OH2=%.8f HOH=%s ",oh1,oh2,formatAngle(angleDegrees));
        try(BufferedReader reader=Files.newBufferedReader(path)) {
            String line;long lineNumber=0;
            while((line=reader.readLine())!=null){lineNumber++;if(!line.startsWith(prefix))continue;
                double psEnergy=value(line,"PS_energy=");List<Atom> atoms=new ArrayList<>();
                for(int atom=0;atom<3;atom++){String atomLine=reader.readLine();lineNumber++;if(atomLine==null)throw new IOException("truncated H2O-13 record");String[] f=atomLine.trim().split("\\s+");if(f.length<18)throw new IOException("malformed H2O-13 atom record at line "+lineNumber);atoms.add(new Atom(f[0],Double.parseDouble(f[1]),Double.parseDouble(f[2]),Double.parseDouble(f[3]),Double.parseDouble(f[12])*EV_PER_ANGSTROM_TO_HARTREE_PER_BOHR,Double.parseDouble(f[13])*EV_PER_ANGSTROM_TO_HARTREE_PER_BOHR,Double.parseDouble(f[14])*EV_PER_ANGSTROM_TO_HARTREE_PER_BOHR));}
                return new Reference(oh1,oh2,angleDegrees,ABSOLUTE_MINIMUM_HARTREE+psEnergy/EV_PER_HARTREE,List.copyOf(atoms),lineNumber-3,path);
            }
        }
        throw new IOException("required H2O-13 geometry not found: "+prefix);
    }

    private static double value(String line,String key){int start=line.indexOf(key);if(start<0)throw new IllegalArgumentException("missing "+key);start+=key.length();int end=line.indexOf(' ',start);return Double.parseDouble(end<0?line.substring(start):line.substring(start,end));}
    private static String formatAngle(double value){return value==Math.rint(value)?Long.toString((long)value):Double.toString(value);}
    public record Atom(String element,double xAngstrom,double yAngstrom,double zAngstrom,double fxHartreePerBohr,double fyHartreePerBohr,double fzHartreePerBohr) { }
    public record Reference(double oh1Angstrom,double oh2Angstrom,double angleDegrees,double absoluteEnergyHartree,List<Atom> atoms,long headerLine,Path source){public Reference{atoms=List.copyOf(atoms);}}
}
