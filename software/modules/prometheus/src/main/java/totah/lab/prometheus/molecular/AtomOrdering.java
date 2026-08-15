package totah.lab.prometheus.molecular;

import java.util.List;

/** Explicit canonical atom ordering, independent of incidental collection iteration order. */
public record AtomOrdering(List<Integer> orderedIndices){public AtomOrdering{orderedIndices=List.copyOf(orderedIndices);for(int i=0;i<orderedIndices.size();i++)if(orderedIndices.get(i)!=i)throw new IllegalArgumentException("atom ordering must be contiguous and canonical");}public static AtomOrdering canonical(int count){return new AtomOrdering(java.util.stream.IntStream.range(0,count).boxed().toList());}}
