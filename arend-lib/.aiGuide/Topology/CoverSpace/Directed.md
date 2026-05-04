### Topology.CoverSpace.Directed

Cover space structure on a directed set and the eventuality filter associated with it, plus the product cover space with a directed-set factor.

#### Directed Cover Space

- **`DirectedCoverSpace`**: Given a `DirectedSet I`, produces a `CoverSpace I` by regularizing the precover where a family is Cauchy iff some member contains a tail `{n | N <= n}` and every point is covered by some member.
- **`DirectedCoverSpace.precover`**: The underlying `PrecoverSpace I`; `isCauchy C` is the conjunction of "exists `U ∈ C` and `N` with `U` containing the upper set of `N`" and "every `n` lies in some member of `C`".
- **`DirectedCoverSpace.makePrecover`**: For any `N : I`, the family consisting of the upper set `{n | N <= n}` together with all singletons `{n}` is a Cauchy cover.

#### Eventuality Filter

- **`EventualityFilter`**: The Cauchy filter on `DirectedCoverSpace I` whose sets are those eventually true: `U` belongs iff there is `N : I` with `U n` for all `n >= N`. Built from a `ProperFilter` using directedness for `filter-meet`, inhabitedness for `filter-top`, and reflexivity for `isProper`.

#### Product with a Directed Cover Space

- **`DirectProdCover`**: Predicate on families `D : Set (Set (Σ I X))` characterizing covers of the product `I × X`: there is an `X`-Cauchy cover whose members eventually (for `n >= N`) refine into some `V ∈ D`, and for each fixed `n` there is an `X`-Cauchy refinement into slices of `D`.
- **`DirectProdCover.Space`**: The `PrecoverSpace (Σ I X)` whose Cauchy predicate is `DirectProdCover`.
- **`directedProdCover-char`**: The Cauchy structure on the product cover space `DirectedCoverSpace.precover ⨯ X` coincides with `DirectProdCover`.
