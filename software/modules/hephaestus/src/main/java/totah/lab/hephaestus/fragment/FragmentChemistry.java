package totah.lab.hephaestus.fragment;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Canonical intrinsic chemistry of a fragment; no pocket-fit evidence belongs here. */
public record FragmentChemistry(
        String fragmentId,
        String canonicalSmiles,
        boolean chemicallyValid,
        Optional<String> invalidReason,
        FragmentDescriptors descriptors,
        RuleOfThreeAssessment ruleOfThree,
        List<FragmentAttachmentHandle> attachmentHandles
) {
    public FragmentChemistry {
        fragmentId = requireText(fragmentId, "fragmentId");
        canonicalSmiles = requireText(canonicalSmiles, "canonicalSmiles");
        invalidReason = Objects.requireNonNull(invalidReason, "invalidReason");
        descriptors = Objects.requireNonNull(descriptors, "descriptors");
        ruleOfThree = Objects.requireNonNull(ruleOfThree, "ruleOfThree");
        attachmentHandles = List.copyOf(Objects.requireNonNull(attachmentHandles, "attachmentHandles"));
        if (chemicallyValid == invalidReason.isPresent()) {
            throw new IllegalArgumentException("Validity must agree with invalidReason");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
