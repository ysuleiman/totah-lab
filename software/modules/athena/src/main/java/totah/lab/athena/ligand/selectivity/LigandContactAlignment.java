package totah.lab.athena.ligand.selectivity;

import totah.lab.athena.sequence.SequenceAlignment;

import java.util.List;
import java.util.Objects;

/**
 * The aligned differential-contact table of one ligand against two
 * receptors: one {@link AlignedLigandContact} row per informative
 * alignment position, plus the {@link SequenceAlignment} that produced
 * the correspondence (kept so renderers and audits can revisit every
 * aligned position, including the uninformative identical non-contact
 * ones, which are intentionally absent from {@code contacts}).
 *
 * <p>Rows cover every mapped position where the residues differ or at
 * least one side has a ligand contact, followed by
 * {@link DifferentialContactType#UNMAPPED} rows for ligand-contact
 * residues the alignment did not map (in residue-number order, with
 * synthetic positions continuing the mapped numbering).
 */
public record LigandContactAlignment(
        List<AlignedLigandContact> contacts,
        SequenceAlignment sequenceAlignment
) {

    public LigandContactAlignment {
        contacts = List.copyOf(
                Objects.requireNonNull(contacts, "contacts")
        );
        Objects.requireNonNull(sequenceAlignment, "sequenceAlignment");
    }
}
