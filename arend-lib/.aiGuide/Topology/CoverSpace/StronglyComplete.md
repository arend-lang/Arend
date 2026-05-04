### Topology.CoverSpace.StronglyComplete

Strong completeness for cover spaces: strongly regular Cauchy filters, the strong completion construction, and universal lifting of cover maps along weakly dense embeddings.

#### Cauchy Filter Order and Equivalence

- **`WeaklyCauchyFilterPoset`**: Poset instance on `WeaklyCauchyFilter S` ordered by subset inclusion `F ⊆ G`.
- **`WCF~_meet`**: Given equivalent weakly Cauchy filters `F CF~ G`, constructs their meet as a weakly Cauchy filter via the filter semilattice.
- **`WCF~_<=<`**: Equivalent weakly Cauchy filters transport along the strong rather-below relation: if `F CF~ G`, `V s<=< U`, and `F V`, then `G U`.
- **`WeaklyCauchyFilterEquivalence`**: Equivalence instance on `WeaklyCauchyFilter S` for a strongly regular cover space, using the Cauchy-filter relation `CF~`.

#### Strongly Regular Cauchy Filters

- **`StronglyRegularCauchyFilter`**: Class extending `WeaklyCauchyFilter` with `isStronglyRegularFilter`: every member `U` of the filter is approximated from below in the strong rather-below relation by another member `V s<=< U`.
- **`StronglyRegularCauchyFilter.Reg_CF~_<=`**: For a strongly regular `F` and weakly Cauchy `G`, the equivalence `F CF~ G` upgrades to inclusion `F ⊆ G`.
- **`StronglyRegularCauchyFilter.equality`**: Two strongly regular Cauchy filters that are `CF~`-equivalent are equal.
- **`sregCF`**: Builds a strongly regular Cauchy filter from a weakly Cauchy filter `F` by taking sets `U` such that every weakly Cauchy filter contained in `F` already contains `U`.
- **`sregCF_<=`**: The strongly regular refinement is a sub-filter: `sregCF F ⊆ F`.
- **`pointSCF`**: The principal strongly regular Cauchy filter at a point `x`, consisting of sets `U` with `single x <=< U`.

#### Strongly Separated and Strongly Complete Spaces

- **`StronglySeparatedCoverSpace`**: Class extending `SeparatedCoverSpace`, `StronglyRegularCoverSpace`, and `StronglyHausdorffTopSpace`; derives the strong Hausdorff property from separatedness using regular covers and the strong rather-below relation.
- **`IsStronglyCompleteCoverSpace`**: Predicate: every strongly regular Cauchy filter `F` on `S` has a point `x` with `pointSCF x ⊆ F`.
- **`IsStronglyCompleteCoverSpace.cauchyFilterToPoint`**: Strong completeness suffices to send any weakly Cauchy filter to a convergence point.
- **`StronglyCompleteCoverSpace`**: Class extending `StronglySeparatedCoverSpace` and `CompleteCoverSpace`; derives ordinary completeness from strong completeness via `sregCF`.
- **`contMap-cauchy`**: Any continuous map out of a strongly complete cover space is automatically a strongly Cauchy map.

#### Lifting Along Weakly Dense Embeddings

- **`weaklyDense-filter-lift`**: Pulls a weakly Cauchy filter `F` on `Y` back to one on `X` along a weakly dense embedding `f`, using sets `U` that contain the preimage `f ^-1 V` for some `V' <=< V` with `F V'`.
- **`weaklyDense-filter-lift.map-equiv`**: The pushforward of the lifted filter along `f` is `CF~`-equivalent to the original filter `F`.
- **`weaklyDense-cauchy-lift`**: Extends a strongly Cauchy map `g : X -> Z` along a weakly dense embedding `f : X -> Y` (with `Z` strongly complete) to a strongly Cauchy map `Y -> Z`.
- **`weaklyDense-lift`**: Extends a cover map `g : X -> Z` along a weakly dense embedding `f : X -> Y` to a cover map `Y -> Z`.
- **`weaklyDense-lift-char`**: The lifted map agrees with `g` on the image of `f`: `weaklyDense-lift f fd g (f x) = g x`.
- **`weaklyDense-lift-neighborhood`**: Characterization of strong neighborhoods of `weaklyDense-lift f fd g y`: `single (lift y) <=< W` iff there is `W' s<=< W` and `V` with `single y <=< V` and `f ^-1 V ⊆ g ^-1 W'`.
- **`weaklyDense-lift-natural`**: Naturality: the lift commutes with `weak-filter-point`, i.e. `lift (weak-filter-point (f.func-weak-cauchy F)) = weak-filter-point (g.func-weak-cauchy F)`.
- **`func-weak-cauchy_<=`**: The `func-weak-cauchy` operation is monotone in the filter argument.
- **`dense-stronglyComplete`**: A weakly dense embedding `f : X -> Y` makes `Y` strongly complete provided every strongly regular Cauchy filter on `X` has a convergence point in `Y`.

#### The Strong Completion

- **`strongCompletion`**: The canonical cover map `S -> StrongCompletion.coverSpace` sending each point to its principal strongly regular Cauchy filter `pointSCF`.
- **`strongCompletion.dense-aux`**: For `single F <=< V` in the completion, `F` itself contains the preimage set `\lam x => V (strongCompletion x)`.
- **`strongCompletion.isDenseEmbedding`**: `strongCompletion` is a weakly dense embedding.
- **`StrongCompletion`**: Strong completion of a strongly regular cover space `X`, given as a `StronglyCompleteCoverSpace` instance on `StronglyRegularCauchyFilter X`.
- **`StrongCompletion.mkSet`**: Lifts a subset `U ⊆ X` to the subset `\lam F => F U` of strongly regular Cauchy filters.
- **`StrongCompletion.mkSet_<=`**: `mkSet` is monotone: `U ⊆ V` implies `mkSet U ⊆ mkSet V`.
- **`StrongCompletion.isCCauchy`**: The cauchy structure on the completion: `D` is cauchy iff some cauchy `C` on `X` refines `D` via `mkSet`.
- **`StrongCompletion.makeCover`**: Every cauchy cover of `X` lifts to a cauchy cover of the completion via `mkSet`.
- **`StrongCompletion.coverSpace`**: The strongly regular cover space structure on `StronglyRegularCauchyFilter X`.
- **`StrongCompletion.pointSCF_^-1_<=<`**: If `single F <=< U` in the completion, then `F` contains the preimage `pointSCF ^-1 U`.
- **`StrongCompletion.mkSet_<=<-point`**: Equivalence `single F <=< mkSet U <-> F U`, characterizing strong neighborhoods of points in the completion.
