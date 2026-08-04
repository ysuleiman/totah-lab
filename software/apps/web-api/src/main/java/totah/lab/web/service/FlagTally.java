package totah.lab.web.service;

import java.util.List;

/**
 * Flag occurrence counts over a set of annotated proteins.
 */
public record FlagTally(
        int total,
        int enzymes,
        int transferases,
        int methyltransferases,
        int membraneProteins,
        int ligandBindingProteins,
        int catalyticResidues,
        int experimentalStructures,
        int rossmannLikeFolds,
        int samBinders
) {
    static FlagTally of(List<AnnotationFlags> flags) {
        return new FlagTally(
                flags.size(),
                count(flags, AnnotationFlags::enzyme),
                count(flags, AnnotationFlags::transferase),
                count(flags, AnnotationFlags::methyltransferase),
                count(flags, AnnotationFlags::membraneProtein),
                count(flags, AnnotationFlags::ligandBindingProtein),
                count(flags, AnnotationFlags::catalyticResidues),
                count(flags, AnnotationFlags::experimentalStructure),
                count(flags, AnnotationFlags::rossmannLikeFold),
                count(flags, AnnotationFlags::bindsSam)
        );
    }

    private static int count(
            List<AnnotationFlags> flags,
            java.util.function.Predicate<AnnotationFlags> flag
    ) {
        int count = 0;
        for (AnnotationFlags value : flags) {
            if (flag.test(value)) {
                count++;
            }
        }
        return count;
    }
}
