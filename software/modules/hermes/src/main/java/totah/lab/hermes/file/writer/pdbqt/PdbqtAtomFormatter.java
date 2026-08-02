package totah.lab.hermes.file.writer.pdbqt;

import totah.lab.gaia.geometry.Point3D;

import java.util.Locale;

final class PdbqtAtomFormatter {
    String format(int serial,String atomName,String residueName,String chainId,
            int residueNumber,Character insertionCode,Point3D position,
            double occupancy,double bFactor,double charge,String ad4Type) {
        StringBuilder line=new StringBuilder(90);
        left(line,"ATOM",6); integer(line,serial,5); line.append(' ');
        left(line,atomName(atomName),4); line.append(' '); left(line,residueName,3);
        line.append(' '); left(line,chainId,1); integer(line,residueNumber,4);
        line.append(insertionCode==null?' ':insertionCode); line.append("   ");
        decimal(line,position.x(),8,3); decimal(line,position.y(),8,3); decimal(line,position.z(),8,3);
        decimal(line,occupancy,6,2); decimal(line,bFactor,6,2); line.append("    ");
        right(line,String.format(Locale.US,"%+.4f",charge),7); line.append(' ');
        right(line,ad4Type,2); line.append(System.lineSeparator()); return line.toString();
    }
    private String atomName(String value){if(value==null)return"    ";if(value.length()==1)return" "+value+"  ";if(value.length()==2)return" "+value+" ";if(value.length()==3)return value+" ";return value.substring(0,4);}
    private void left(StringBuilder out,String value,int width){value=value==null?"":value;int length=Math.min(value.length(),width);out.append(value,0,length);out.append(" ".repeat(width-length));}
    private void right(StringBuilder out,String value,int width){value=value==null?"":value;if(value.length()>width)throw new IllegalArgumentException("Value does not fit PDBQT field of width "+width+": "+value);out.append(" ".repeat(width-value.length()));out.append(value);}
    private void integer(StringBuilder out,int value,int width){right(out,Integer.toString(value),width);}
    private void decimal(StringBuilder out,double value,int width,int precision){String formatted=switch(precision){case 2->String.format(Locale.US,"%.2f",value);case 3->String.format(Locale.US,"%.3f",value);default->throw new IllegalArgumentException("Unsupported decimal precision: "+precision);};right(out,formatted,width);}
}
