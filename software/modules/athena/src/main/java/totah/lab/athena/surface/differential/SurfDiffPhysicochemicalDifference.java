package totah.lab.athena.surface.differential;

import java.util.Map;

/** SurfDiff's fixed 21-by-21 physicochemical-distance lookup, divided by 18. */
public final class SurfDiffPhysicochemicalDifference {
    private static final String ALPHABET = "ARNDCQEGHILKMFPSTWYV-";
    private static final Map<String, Character> THREE_TO_ONE = Map.ofEntries(
            Map.entry("ALA", 'A'), Map.entry("ARG", 'R'), Map.entry("ASN", 'N'),
            Map.entry("ASP", 'D'), Map.entry("CYS", 'C'), Map.entry("GLN", 'Q'),
            Map.entry("GLU", 'E'), Map.entry("GLY", 'G'), Map.entry("HIS", 'H'),
            Map.entry("ILE", 'I'), Map.entry("LEU", 'L'), Map.entry("LYS", 'K'),
            Map.entry("MET", 'M'), Map.entry("PHE", 'F'), Map.entry("PRO", 'P'),
            Map.entry("SER", 'S'), Map.entry("THR", 'T'), Map.entry("TRP", 'W'),
            Map.entry("TYR", 'Y'), Map.entry("VAL", 'V'), Map.entry("XXX", '-'));

    private static final int[][] DISTANCE = {
        {0,12,14,14,10,12,12,10,14,12,12,12,12,14,12,8,10,15,14,10,18},
        {12,0,10,14,15,8,10,14,10,17,14,6,12,16,13,12,12,14,13,17,18},
        {14,10,0,8,14,10,10,10,9,16,16,10,14,15,13,8,10,15,13,16,18},
        {14,14,8,0,14,10,6,12,11,16,18,12,16,15,12,10,12,15,15,16,18},
        {10,15,14,14,0,15,16,14,14,12,12,15,12,13,14,12,12,12,13,12,18},
        {12,8,10,10,15,0,6,14,10,17,14,8,10,16,12,10,12,13,12,14,18},
        {12,10,10,6,16,6,0,14,10,17,17,8,14,16,12,10,12,14,13,14,18},
        {10,14,10,12,14,14,14,0,13,18,18,14,16,15,13,10,14,13,15,16,18},
        {14,10,9,11,14,10,10,13,0,16,16,12,13,11,13,12,13,12,7,16,18},
        {12,17,16,16,12,17,17,18,16,0,5,17,8,10,16,15,12,15,12,2,18},
        {12,14,16,18,12,14,17,18,16,5,0,14,6,10,16,15,12,13,12,8,18},
        {12,6,10,12,15,8,8,14,12,17,14,0,12,16,12,10,12,14,13,14,18},
        {12,12,14,16,12,10,14,16,13,8,6,12,0,10,13,12,12,11,12,8,18},
        {14,16,15,15,13,16,16,15,11,10,10,16,10,0,16,14,14,9,5,12,18},
        {12,13,13,12,14,12,12,13,13,16,16,12,13,16,0,12,12,15,14,14,18},
        {8,12,8,10,12,10,10,10,12,15,15,10,12,14,12,0,8,15,14,15,18},
        {10,12,10,12,12,12,12,14,13,12,12,12,12,14,12,8,0,13,13,10,18},
        {15,14,15,15,12,13,14,13,12,15,13,14,11,9,15,15,13,0,8,15,18},
        {14,13,13,15,13,12,13,15,7,12,12,13,12,5,14,14,13,8,0,12,18},
        {10,17,16,16,12,14,14,16,16,2,8,14,8,12,14,15,10,15,12,0,18},
        {18,18,18,18,18,18,18,18,18,18,18,18,18,18,18,18,18,18,18,18,18}
    };

    private SurfDiffPhysicochemicalDifference() {
    }

    public static double betweenThreeLetter(String query, String subject) {
        Character queryOne = THREE_TO_ONE.get(normalize(query));
        Character subjectOne = THREE_TO_ONE.get(normalize(subject));
        if (queryOne == null || subjectOne == null) {
            throw new IllegalArgumentException("unsupported residue name");
        }
        return betweenOneLetter(queryOne, subjectOne);
    }

    public static double unmatched() {
        return 1.0;
    }

    public static double betweenOneLetter(char query, char subject) {
        int queryIndex = ALPHABET.indexOf(Character.toUpperCase(query));
        int subjectIndex = ALPHABET.indexOf(Character.toUpperCase(subject));
        if (queryIndex < 0 || subjectIndex < 0) {
            throw new IllegalArgumentException("unsupported amino-acid code");
        }
        return DISTANCE[queryIndex][subjectIndex] / 18.0;
    }

    private static String normalize(String value) {
        if (value == null) {
            throw new NullPointerException("residue name");
        }
        return value.trim().toUpperCase();
    }
}
