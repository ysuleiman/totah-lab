package totah.lab.p2rank;

import totah.lab.pocket.Pocket;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class P2RankCsvParser {

    public List<Pocket> parse(Path file) throws IOException {
        List<Pocket> pockets = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            // skip header
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty())
                    continue;

                // Split first 10 columns only because residue_ids and surf_atom_ids contain spaces
                String[] cols = line.split(",", 11);
                P2RankPocket pocket = new P2RankPocket();
                pocket.setPocketName(cols[0].trim());
                pocket.setId(
                        Integer.parseInt(cols[1].trim())
                );
                pocket.setScore(
                        Double.parseDouble(cols[2].trim())
                );
                pocket.setDruggabilityScore(
                        Double.parseDouble(cols[3].trim())
                );
                pocket.setSasPoints(
                        Integer.parseInt(cols[4].trim())
                );
                pocket.setSurfaceAtoms(
                        Integer.parseInt(cols[5].trim())
                );

                double[] center = {
                        Double.parseDouble(cols[6].trim()),
                        Double.parseDouble(cols[7].trim()),
                        Double.parseDouble(cols[8].trim())
                };
                pocket.setCenter(center);
                // residues
                List<String> residues =
                        Arrays.asList(
                                cols[9].trim().split("\\s+")
                        );
                pocket.setResidueIds(residues);

                // surface atoms
                List<Integer> atoms =
                        Arrays.stream(
                                        cols[10].trim().split("\\s+")
                                )
                                .map(Integer::parseInt)
                                .toList();
                pocket.setSurfaceAtomIds(atoms);
                pockets.add(pocket);
            }
        }
        return pockets;
    }
}
