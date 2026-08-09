package totah.lab.athena.ligand.pose;

import totah.lab.athena.ligand.contact.LigandContact;

import java.util.List;

public record PoseAnalysis(
        PocketPose pocketPose,
        List<LigandContact> contacts
) {
    public PoseAnalysis {
        contacts = List.copyOf(contacts);
    }
}
