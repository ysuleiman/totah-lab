package totah.lab.hermes.file.pdbqt.reader;

import totah.lab.hermes.file.pdbqt.*;
import totah.lab.hermes.file.pdbqt.internal.PdbqtAtomParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Reader for PDBQT files.
 *
 * Supports:
 * - MODEL / ENDMDL
 * - ATOM / HETATM
 * - ROOT / ENDROOT
 * - BRANCH / ENDBRANCH
 * - TORSDOF
 * - REMARK
 *
 * Unknown records are ignored.
 */
public final class PdbqtReader {
    private final PdbqtAtomParser atomParser = new PdbqtAtomParser();

    public PdbqtFile read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            return read(reader);
        }
    }

    public PdbqtFile read(Reader source) throws IOException {
        Objects.requireNonNull(source, "source");

        BufferedReader reader = source instanceof BufferedReader buffered
                ? buffered
                : new BufferedReader(source);

        List<PdbqtModel> models = new ArrayList<>();

        ModelBuilder model = null;
        int implicitModelNumber = 1;

        String line;
        int lineNumber = 0;

        while ((line = reader.readLine()) != null) {
            lineNumber++;

            if (line.isBlank()) {
                continue;
            }

            String record = recordName(line);

            switch (record) {

                case "MODEL" -> {
                    if (model != null && !model.isEmpty()) {
                        models.add(model.build());
                    }

                    Integer modelNumber = parseOptionalInt(
                            safeSubstring(
                                    line,
                                    5,
                                    line.length()
                            ).trim()
                    );

                    model = new ModelBuilder(
                            modelNumber != null
                                    ? modelNumber
                                    : implicitModelNumber++
                    );
                }

                case "ENDMDL" -> {
                    if (model != null) {
                        models.add(model.build());
                        model = null;
                    }
                }

                case "ATOM", "HETATM" -> {
                    if (model == null) {
                        model = new ModelBuilder(implicitModelNumber);
                    }

                    PdbqtAtom atom = atomParser.parse(
                            line,
                            lineNumber
                    );

                    model.addAtom(atom);
                }

                case "ROOT" -> {
                    if (model == null) {
                        model = new ModelBuilder(implicitModelNumber);
                    }

                    model.startRoot();
                }

                case "ENDROOT" -> {
                    if (model == null) {
                        throw formatError(
                                lineNumber,
                                "ENDROOT encountered without active model"
                        );
                    }

                    model.endRoot();
                }

                case "BRANCH" -> {
                    if (model == null) {
                        model = new ModelBuilder(implicitModelNumber);
                    }

                    BranchIds ids = parseBranch(
                            line,
                            lineNumber
                    );

                    model.startBranch(
                            ids.parentAtom(),
                            ids.childAtom()
                    );
                }

                case "ENDBRANCH" -> {
                    if (model == null) {
                        throw formatError(
                                lineNumber,
                                "ENDBRANCH encountered without active model"
                        );
                    }

                    BranchIds ids = parseBranch(
                            line,
                            lineNumber
                    );

                    model.endBranch(
                            ids.parentAtom(),
                            ids.childAtom(),
                            lineNumber
                    );
                }

                case "TORSDOF" -> {
                    if (model == null) {
                        model = new ModelBuilder(implicitModelNumber);
                    }

                    String value = safeSubstring(
                            line,
                            7,
                            line.length()
                    ).trim();

                    try {
                        model.torsdof = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        throw formatError(
                                lineNumber,
                                "Invalid TORSDOF value: " + value,
                                e
                        );
                    }
                }

                case "REMARK" -> {
                    if (model == null) {
                        model = new ModelBuilder(implicitModelNumber);
                    }

                    model.remarks.add(line);
                }

                default -> {
                    // Ignore unsupported records.
                }
            }
        }

        if (model != null && !model.isEmpty()) {
            models.add(model.build());
        }

        return new PdbqtFile(
                List.copyOf(models)
        );
    }

    private static BranchIds parseBranch(
            String line,
            int lineNumber
    ) throws PdbqtFormatException {
        String[] tokens = line.trim().split("\\s+");

        if (tokens.length < 3) {
            throw formatError(
                    lineNumber,
                    "Malformed BRANCH record: " + line
            );
        }

        try {
            return new BranchIds(
                    Integer.parseInt(tokens[1]),
                    Integer.parseInt(tokens[2])
            );
        } catch (NumberFormatException e) {
            throw formatError(
                    lineNumber,
                    "Malformed BRANCH atom serials: " + line,
                    e
            );
        }
    }

    private static String recordName(
            String line
    ) {
        int end = Math.min(
                10,
                line.length()
        );

        String prefix =
                line.substring(0, end).trim();

        int space = prefix.indexOf(' ');

        if (space >= 0) {
            prefix = prefix.substring(
                    0,
                    space
            );
        }

        return prefix;
    }

    private static String safeSubstring(
            String value,
            int start,
            int end
    ) {
        if (start >= value.length()) {
            return "";
        }

        return value.substring(
                start,
                Math.min(end, value.length())
        );
    }

    private static Integer parseOptionalInt(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static PdbqtFormatException formatError(
            int lineNumber,
            String message
    ) {
        return new PdbqtFormatException(
                "PDBQT line "
                        + lineNumber
                        + ": "
                        + message
        );
    }

    private static PdbqtFormatException formatError(
            int lineNumber,
            String message,
            Throwable cause
    ) {
        return new PdbqtFormatException(
                "PDBQT line "
                        + lineNumber
                        + ": "
                        + message,
                cause
        );
    }

    private static final class ModelBuilder {

        private final int modelNumber;

        private final List<PdbqtAtom> atoms =
                new ArrayList<>();

        private final List<PdbqtAtom> rootAtoms =
                new ArrayList<>();

        private final List<PdbqtBranch> branches =
                new ArrayList<>();

        private final List<String> remarks =
                new ArrayList<>();

        private final Deque<BranchBuilder> branchStack =
                new ArrayDeque<>();

        private boolean inRoot;

        private Integer torsdof;

        private ModelBuilder(
                int modelNumber
        ) {
            this.modelNumber = modelNumber;
        }

        private void addAtom(
                PdbqtAtom atom
        ) {
            atoms.add(atom);

            if (inRoot) {
                rootAtoms.add(atom);
            }

            if (!branchStack.isEmpty()) {
                branchStack
                        .peek()
                        .atoms
                        .add(atom);
            }
        }

        private void startRoot() {
            inRoot = true;
        }

        private void endRoot() {
            inRoot = false;
        }

        private void startBranch(
                int parentAtom,
                int childAtom
        ) {
            branchStack.push(
                    new BranchBuilder(
                            parentAtom,
                            childAtom
                    )
            );
        }

        private void endBranch(
                int parentAtom,
                int childAtom,
                int lineNumber
        ) throws PdbqtFormatException {
            if (branchStack.isEmpty()) {
                throw formatError(
                        lineNumber,
                        "ENDBRANCH without BRANCH"
                );
            }

            BranchBuilder builder =
                    branchStack.pop();

            if (builder.parentAtom != parentAtom
                    || builder.childAtom != childAtom) {

                throw formatError(
                        lineNumber,
                        "ENDBRANCH does not match BRANCH. Expected "
                                + builder.parentAtom
                                + " "
                                + builder.childAtom
                                + " but found "
                                + parentAtom
                                + " "
                                + childAtom
                );
            }

            PdbqtBranch branch =
                    builder.build();

            if (branchStack.isEmpty()) {
                branches.add(branch);
            } else {
                branchStack
                        .peek()
                        .children
                        .add(branch);
            }
        }

        private boolean isEmpty() {
            return atoms.isEmpty()
                    && remarks.isEmpty()
                    && torsdof == null;
        }

        private PdbqtModel build() throws PdbqtFormatException {
            if (!branchStack.isEmpty()) {
                throw new PdbqtFormatException(
                        "Unclosed BRANCH in PDBQT model "
                                + modelNumber
                );
            }

            PdbqtTorsionTree torsionTree =
                    new PdbqtTorsionTree(
                            List.copyOf(rootAtoms),
                            List.copyOf(branches),
                            torsdof
                    );

            return new PdbqtModel(
                    modelNumber,
                    List.copyOf(atoms),
                    torsionTree,
                    List.copyOf(remarks)
            );
        }
    }

    private static final class BranchBuilder {

        private final int parentAtom;
        private final int childAtom;

        private final List<PdbqtAtom> atoms =
                new ArrayList<>();

        private final List<PdbqtBranch> children =
                new ArrayList<>();

        private BranchBuilder(
                int parentAtom,
                int childAtom
        ) {
            this.parentAtom = parentAtom;
            this.childAtom = childAtom;
        }

        private PdbqtBranch build() {
            return new PdbqtBranch(
                    parentAtom,
                    childAtom,
                    List.copyOf(atoms),
                    List.copyOf(children)
            );
        }
    }

    private record BranchIds(
            int parentAtom,
            int childAtom
    ) {}

}
