package totah.lab.proteus.protein.mutation;

import totah.lab.gaia.structure.Structure;

import java.util.Objects;

/** A mutation set applied to one parent structure under one context. */
public record MutationRequest(
        Structure parent,
        MutationSet mutationSet,
        MutationContext context) {

    public MutationRequest {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(mutationSet, "mutationSet");
        context = context == null ? MutationContext.defaults() : context;
    }

    public MutationRequest(Structure parent, MutationSet mutationSet) {
        this(parent, mutationSet, MutationContext.defaults());
    }
}
