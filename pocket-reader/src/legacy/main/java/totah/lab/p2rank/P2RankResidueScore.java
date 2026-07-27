package totah.lab.p2rank;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class P2RankResidueScore {

    private final String chain;
    private final int residueNumber;
    private final String residueName;

    private final double score;
    private final double zScore;
    private final double probability;

    private final int pocketRank;

    public P2RankResidueScore(
            String chain,
            int residueNumber,
            String residueName,
            double score,
            double zScore,
            double probability,
            int pocketRank) {

        this.chain = chain;
        this.residueNumber = residueNumber;
        this.residueName = residueName;
        this.score = score;
        this.zScore = zScore;
        this.probability = probability;
        this.pocketRank = pocketRank;
    }
}
