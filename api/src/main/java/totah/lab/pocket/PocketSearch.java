package totah.lab.pocket;

import java.util.*;
import java.util.stream.Collectors;

public class PocketSearch {

    protected final List<Pocket> pockets;

    public PocketSearch(List<Pocket> pockets) {
        this.pockets = pockets == null ? new ArrayList<>() : pockets;
    }

    public List<Pocket> findByResidue(String residueName) {
        return pockets.stream()
                .filter(p -> p.getResidues().stream()
                        .anyMatch(r ->
                                r.getName().equalsIgnoreCase(residueName)))
                .collect(Collectors.toList());
    }


    /**
     * Find pockets containing a residue.
     */
    public List<Pocket> findByResidue(String residueName, int residueNumber) {

        return pockets.stream()
                .filter(p -> p.getResidues().stream()
                        .anyMatch(r ->
                                r.getName().equalsIgnoreCase(residueName)
                                        && r.getNumber() == residueNumber))
                .collect(Collectors.toList());
    }

    /**
     * Find pockets satisfying multiple constraints.
     */
    public List<Pocket> search(PocketCriteria criteria) {

        return pockets.stream()
                .filter(p -> {

                    if(criteria.minPocketScore != null &&
                            p.getScore() < criteria.minPocketScore)
                        return false;

                    if(criteria.containsResidue != null) {

                        boolean found =
                                p.getResidues()
                                        .stream()
                                        .anyMatch(r ->
                                                r.getName()
                                                        .equalsIgnoreCase(
                                                                criteria.containsResidue));

                        if(!found)
                            return false;
                    }

                    return true;
                })
                .collect(Collectors.toList());
    }


    public Map<Pocket, List<Residue>> findResidues(String residueName) {
        return pockets.stream()
                .collect(Collectors.toMap(
                        p -> p,
                        p -> p.getResidues().stream()
                                .filter(r -> r.getName().equalsIgnoreCase(residueName))
                                .toList()
                ))
                .entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
    }

    public List<Pocket> findByResidues(Set<String> residueNames) {
        if (residueNames == null || residueNames.isEmpty()) {
            return List.of();
        }
        return pockets.stream()
                .filter(p -> p.getResidues().stream()
                        .map(r -> r.getName().toUpperCase())
                        .anyMatch(residueNames::contains))
                .toList();
    }

    public List<Pocket> findContainingAllResidues(Set<String> residueNames) {
        if (residueNames == null || residueNames.isEmpty()) {
            return List.of();
        }
        Set<String> query = residueNames.stream()
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
        return pockets.stream()
                .filter(p -> {

                    Set<String> names = p.getResidues().stream()
                            .map(r -> r.getName().toUpperCase())
                            .collect(Collectors.toSet());
                    return names.containsAll(query);
                })
                .toList();
    }

    private double distance(double[] a, double[] b) {
        double dx = a[0]-b[0];
        double dy = a[1]-b[1];
        double dz = a[2]-b[2];
        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }


    public static class PocketCriteria {
        private Double minPocketScore;
        private String containsResidue;
        public PocketCriteria minScore(double value) {
            this.minPocketScore = value;
            return this;
        }
        public PocketCriteria containsResidue(String residue) {
            this.containsResidue = residue;
            return this;
        }
    }
}