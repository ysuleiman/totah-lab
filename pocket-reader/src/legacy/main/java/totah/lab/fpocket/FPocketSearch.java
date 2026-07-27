package totah.lab.fpocket;

import totah.lab.pocket.Pocket;
import totah.lab.pocket.PocketSearch;

import java.util.Comparator;
import java.util.List;

public class FPocketSearch extends PocketSearch{

    public FPocketSearch(List<Pocket> pockets) {
        super(pockets);
    }

    public List<FPocket> rankByDruggability() {
        return pockets.stream()
                .filter(FPocket.class::isInstance)
                .map(FPocket.class::cast)
                .sorted(Comparator.comparing(
                        FPocket::getDruggabilityScore
                ).reversed())
                .toList();
    }

    public List<FPocket> rankByScore() {
        return pockets.stream()
                .filter(FPocket.class::isInstance)
                .map(FPocket.class::cast)
                .sorted(Comparator.comparing(
                        FPocket::getScore
                ).reversed())
                .toList();
    }

    public List<FPocket> rankByHydrophobicity() {

        return pockets.stream()
                .filter(FPocket.class::isInstance)
                .map(FPocket.class::cast)
                .sorted(Comparator.comparingDouble(
                        (FPocket p) -> p.getChemistry().getHydrophobicityScore()
                ).reversed())
                .toList();
    }

    public List<FPocket> rankByPolarity() {

        return pockets.stream()
                .filter(FPocket.class::isInstance)
                .map(FPocket.class::cast)
                .sorted(Comparator.comparingInt(
                        (FPocket p) -> p.getChemistry().getPolarityScore()
                ).reversed())
                .toList();
    }
}
