package totah.lab.hermes.file.writer.pdbqt.validation;

public record PdbqtValidationOptions(
        boolean allowHetatm,
        boolean allowTer,
        boolean allowEnd,
        boolean requireEndRecord) {
    public static PdbqtValidationOptions defaults(){return new PdbqtValidationOptions(true,true,true,false);}
}
