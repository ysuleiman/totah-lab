package totah.lab.prometheus.ingest.authoritative;

/** Native SANDER energy components in kcal/mol. */
public record AmberEnergyComponents(double bond, double angle, double properTorsion,
                                    double ordinaryLennardJones, double electrostatics,
                                    double oneFourLennardJones, double oneFourElectrostatics,
                                    double restraint, double total) {
}
