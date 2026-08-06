package totah.lab.web.service;

import java.util.List;

/**
 * Supplies the chosen pocket ids of a query receptor for the
 * chosen-reference retrieval channel. Chosen pockets are guaranteed
 * evaluation only: no score bonus, no rank bonus, never
 * auto-positive.
 */
@FunctionalInterface
interface ChosenPocketReferenceProvider {

    /**
     * Returns an empty list — used where no chosen-reference channel
     * is wired (legacy/test construction).
     */
    ChosenPocketReferenceProvider NONE =
            (receptorId, structureId) -> List.of();

    /**
     * The chosen pocket ids of the receptor's structures (the query
     * structure's own chosen pocket included).
     */
    List<Long> chosenPocketIds(long receptorId, long structureId);
}
