package totah.lab.hermes.file.pdbqt.meeko;

import totah.lab.hermes.file.pdbqt.PdbqtRemarkParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MeekoResultParser implements PdbqtRemarkParser<MeekoResult> {

    private static final String SMILES_PREFIX =
            "REMARK SMILES ";

    private static final String SMILES_IDX_PREFIX =
            "REMARK SMILES IDX ";

    private static final String H_PARENT_PREFIX =
            "REMARK H PARENT ";

    @Override
    public Optional<MeekoResult> parse(
            List<String> remarks
    ) {
        if (remarks == null || remarks.isEmpty()) {
            return Optional.empty();
        }

        String smiles = null;

        List<MeekoResult.IndexPair> smilesIndices =
                new ArrayList<>();

        List<MeekoResult.HydrogenParent> hydrogenParents =
                new ArrayList<>();

        List<String> otherRemarks =
                new ArrayList<>();

        boolean foundMeekoRemark = false;

        for (String remark : remarks) {
            if (remark == null || remark.isBlank()) {
                continue;
            }

            if (remark.startsWith(SMILES_IDX_PREFIX)) {
                foundMeekoRemark = true;

                parseSmilesIndices(
                        remark,
                        smilesIndices
                );

                continue;
            }

            if (remark.startsWith(H_PARENT_PREFIX)) {
                foundMeekoRemark = true;

                parseHydrogenParents(
                        remark,
                        hydrogenParents
                );

                continue;
            }

            if (remark.startsWith(SMILES_PREFIX)) {
                foundMeekoRemark = true;

                smiles = remark
                        .substring(SMILES_PREFIX.length())
                        .trim();

                continue;
            }

            otherRemarks.add(remark);
        }

        if (!foundMeekoRemark) {
            return Optional.empty();
        }

        return Optional.of(
                new MeekoResult(
                        smiles,
                        List.copyOf(smilesIndices),
                        List.copyOf(hydrogenParents),
                        List.copyOf(otherRemarks)
                )
        );
    }

    private static void parseSmilesIndices(
            String remark,
            List<MeekoResult.IndexPair> output
    ) {
        String tail = remark
                .substring(SMILES_IDX_PREFIX.length())
                .trim();

        String[] tokens = splitTokens(
                remark,
                tail
        );

        for (int i = 0; i < tokens.length; i += 2) {
            output.add(
                    new MeekoResult.IndexPair(
                            parseInteger(
                                    remark,
                                    tokens[i]
                            ),
                            parseInteger(
                                    remark,
                                    tokens[i + 1]
                            )
                    )
            );
        }
    }

    private static void parseHydrogenParents(
            String remark,
            List<MeekoResult.HydrogenParent> output
    ) {
        String tail = remark
                .substring(H_PARENT_PREFIX.length())
                .trim();

        String[] tokens = splitTokens(
                remark,
                tail
        );

        for (int i = 0; i < tokens.length; i += 2) {
            output.add(
                    new MeekoResult.HydrogenParent(
                            parseInteger(
                                    remark,
                                    tokens[i]
                            ),
                            parseInteger(
                                    remark,
                                    tokens[i + 1]
                            )
                    )
            );
        }
    }

    private static String[] splitTokens(
            String remark,
            String tail
    ) {
        if (tail.isBlank()) {
            throw new IllegalArgumentException(
                    "Malformed Meeko remark: " + remark
            );
        }

        String[] tokens =
                tail.split("\\s+");

        if (tokens.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "Expected integer pairs in Meeko remark: "
                            + remark
            );
        }

        return tokens;
    }

    private static int parseInteger(
            String remark,
            String value
    ) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid integer in Meeko remark: "
                            + remark,
                    e
            );
        }
    }
}
