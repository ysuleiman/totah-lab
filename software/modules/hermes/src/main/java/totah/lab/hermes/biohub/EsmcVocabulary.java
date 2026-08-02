package totah.lab.hermes.biohub;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

final class EsmcVocabulary {

    private static final Map<Character, Integer> TOKEN_BY_AMINO_ACID =
            createTokenMap();

    private EsmcVocabulary() {
    }

    static Map<Character, Integer> canonicalTokens() {
        return TOKEN_BY_AMINO_ACID;
    }

    static int tokenFor(char aminoAcid) {
        Integer token = TOKEN_BY_AMINO_ACID.get(aminoAcid);
        if (token == null) {
            throw new IllegalArgumentException(
                    "Unsupported amino acid: " + aminoAcid
            );
        }
        return token;
    }

    private static Map<Character, Integer> createTokenMap() {
        Map<Character, Integer> tokens = new LinkedHashMap<>();
        tokens.put('A', 5);
        tokens.put('C', 23);
        tokens.put('D', 13);
        tokens.put('E', 9);
        tokens.put('F', 18);
        tokens.put('G', 6);
        tokens.put('H', 21);
        tokens.put('I', 12);
        tokens.put('K', 15);
        tokens.put('L', 4);
        tokens.put('M', 20);
        tokens.put('N', 17);
        tokens.put('P', 14);
        tokens.put('Q', 16);
        tokens.put('R', 10);
        tokens.put('S', 8);
        tokens.put('T', 11);
        tokens.put('V', 7);
        tokens.put('W', 22);
        tokens.put('Y', 19);
        return Collections.unmodifiableMap(tokens);
    }
}
