package totah.lab.athena.energy;

/** Energy terms remain separate; absence means the engine did not evaluate it. */
public enum EnergyComponent {
    TOTAL_POTENTIAL,
    PROTEIN_LIGAND_VDW,
    PROTEIN_LIGAND_ELECTROSTATIC,
    LIGAND_INTERNAL_STRAIN,
    PROTEIN_INTERNAL_STRAIN,
    BOND,
    ANGLE,
    TORSIONAL,
    BONDED,
    VDW,
    ELECTROSTATIC,
    NONBONDED,
    SAM_INTERACTION,
    SOLVENT,
    RESTRAINT
}
