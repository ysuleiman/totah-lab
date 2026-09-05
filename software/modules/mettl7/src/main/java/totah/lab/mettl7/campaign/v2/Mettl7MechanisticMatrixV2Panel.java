package totah.lab.mettl7.campaign.v2;

import java.util.List;

import static totah.lab.mettl7.campaign.v2.CompoundBranch.ChemistryBranch.INHIBITOR_SELECTIVITY;
import static totah.lab.mettl7.campaign.v2.CompoundBranch.ChemistryBranch.N_METHYLATION;
import static totah.lab.mettl7.campaign.v2.CompoundBranch.ChemistryBranch.S_METHYLATION;
import static totah.lab.mettl7.campaign.v2.ReceptorBackground.Paralog.METTL7A;
import static totah.lab.mettl7.campaign.v2.ReceptorBackground.Paralog.METTL7B;

/** Frozen nominal panel from the clean-rebuild specification. */
public final class Mettl7MechanisticMatrixV2Panel {

    private Mettl7MechanisticMatrixV2Panel() { }

    public static List<ReceptorBackground> receptors() {
        return List.of(
                receptor("A0", METTL7A),
                receptor("A1", METTL7A, "F43L"),
                receptor("A2", METTL7A, "Y47S"),
                receptor("A3", METTL7A, "F199G"),
                receptor("A4", METTL7A, "F43L", "Y47S"),
                receptor("A5", METTL7A, "F43L", "F199G"),
                receptor("A6", METTL7A, "Y47S", "F199G"),
                receptor("A7", METTL7A, "F43L", "Y47S", "F199G"),
                receptor("B0", METTL7B),
                receptor("B1", METTL7B, "L43F"),
                receptor("B2", METTL7B, "S47Y"),
                receptor("B3", METTL7B, "G199F"),
                receptor("B4", METTL7B, "L43F", "S47Y"),
                receptor("B5", METTL7B, "L43F", "G199F"),
                receptor("B6", METTL7B, "S47Y", "G199F"),
                receptor("B7", METTL7B, "L43F", "S47Y", "G199F"));
    }

    public static List<CompoundBranch> compounds() {
        return List.of(
                sulfur("HS_MINUS_H2S"), sulfur("DTT"), sulfur("CAPTOPRIL"),
                sulfur("TSL"), sulfur("REDUCED_ROMIDEPSIN"), sulfur("CYSTEINE"),
                sulfur("GLUTATHIONE"), sulfur("4_NITROBENZENETHIOL"),
                sulfur("6_MERCAPTOPURINE"), sulfur("D_PENICILLAMINE"),
                sulfur("L_PENICILLAMINE"), sulfur("THIOGLUCOSE"),
                sulfur("PRASUGREL_ACTIVE_THIOL_SS"),
                sulfur("PRASUGREL_ACTIVE_THIOL_SR"),
                sulfur("PRASUGREL_ACTIVE_THIOL_RR"),
                sulfur("PRASUGREL_ACTIVE_THIOL_RS"),
                new CompoundBranch("BI187004_M1_N1", N_METHYLATION, false),
                new CompoundBranch("BI187004_M14_N3", N_METHYLATION, false),
                inhibitor("R_DCMB"), inhibitor("S_DCMB"),
                inhibitor("NETARSUDIL"), inhibitor("METTL7_BRICS_0003"));
    }

    /** Nominal lower bound before chemical-state enumeration and seed multiplication. */
    public static int nominalDockingCellCount() {
        return receptors().size() * compounds().size();
    }

    private static ReceptorBackground receptor(
            String id,
            ReceptorBackground.Paralog paralog,
            String... substitutions) {
        return new ReceptorBackground(id, paralog, List.of(substitutions));
    }

    private static CompoundBranch sulfur(String id) {
        return new CompoundBranch(id, S_METHYLATION, true);
    }

    private static CompoundBranch inhibitor(String id) {
        return new CompoundBranch(id, INHIBITOR_SELECTIVITY, false);
    }
}
