package totah.lab.athena.ligand.pose;

import totah.lab.athena.ligand.contact.ContactAnalyzer;
import totah.lab.athena.ligand.contact.LigandContact;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Structure;

import java.util.List;
import java.util.Objects;

public final class DefaultPoseInteractionAnalyzer
        implements PoseInteractionAnalyzer {

    private final ContactAnalyzer contactAnalyzer;
    private final PocketPoseAnalyzer pocketPoseAnalyzer;

    public DefaultPoseInteractionAnalyzer(
            ContactAnalyzer contactAnalyzer,
            PocketPoseAnalyzer pocketPoseAnalyzer
    ) {
        this.contactAnalyzer = Objects.requireNonNull(
                contactAnalyzer,
                "contactAnalyzer"
        );
        this.pocketPoseAnalyzer = Objects.requireNonNull(
                pocketPoseAnalyzer,
                "pocketPoseAnalyzer"
        );
    }

    @Override
    public PoseAnalysis analyze(
            Structure receptor,
            Pocket pocket,
            Ligand ligand
    ) {
        Objects.requireNonNull(receptor, "receptor");
        Objects.requireNonNull(pocket, "pocket");
        Objects.requireNonNull(ligand, "ligand");

        List<LigandContact> contacts =
                contactAnalyzer.analyze(
                        receptor,
                        ligand
                );

        PocketPose pocketPose =
                pocketPoseAnalyzer.analyze(
                        pocket,
                        ligand
                );

        return new PoseAnalysis(
                pocketPose,
                contacts
        );
    }
}
