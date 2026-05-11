### Topology.CoverSpace.Directed

Cover space structure on a directed set, where Cauchy covers are determined by eventual behavior, and its product with arbitrary cover spaces.

This module equips a `DirectedSet I` with a canonical cover space structure: a family of sets is Cauchy iff some member covers a tail `{n : I | N <= n}` and every point is covered by some member. The associated `EventualityFilter` packages the "eventually" filter (sets containing a tail) as a Cauchy filter, modeling convergence along the directed order. The product construction `DirectProdCover` characterizes Cauchy covers on `I × X` in terms of an eventual cover in `X` together with a pointwise cover at each `n : I`, providing the bridge needed when reasoning about nets or sequences valued in a cover space.

#### Cover Space on a Directed Set

- **`DirectedCoverSpace`**: The cover space `CoverSpace I` associated to a directed set `I`, obtained by regularizing `precover`.
- **`DirectedCoverSpace.precover`**: The underlying `PrecoverSpace I`. A family `C` is Cauchy iff some `U ∈ C` covers a tail (`∃ N, ∀ n ≥ N, U n`) and every `n : I` is covered by some `V ∈ C`.
- **`DirectedCoverSpace.makePrecover`**: For any `N : I`, the cover consisting of the tail `{n | N <= n}` together with all singletons `{n}` is Cauchy — the canonical witness used to refine arbitrary covers.

#### Eventuality Filter

- **`EventualityFilter`**: The Cauchy filter on `DirectedCoverSpace I` whose underlying proper filter consists of subsets `U ⊆ I` containing a tail (`∃ N, ∀ n ≥ N, U n`). Built via `regPrecoverCauchyFilter`, with directedness ensuring closure under finite meets and inhabitation giving the top element.

#### Product with a Cover Space

- **`DirectProdCover`**: Predicate on `Set (Set (Σ I X))` characterizing Cauchy covers of the product `I × X`: there is an `X`-Cauchy cover whose members eventually (for `n ≥ N`) embed into some `D`-member, and for each fixed `n : I` the slices `{x | V (n, x)}` form an `X`-Cauchy cover.
- **`DirectProdCover.Space`**: The `PrecoverSpace (Σ I X)` whose Cauchy covers are exactly `DirectProdCover`.
- **`directedProdCover-char`**: Bidirectional characterization showing that `isCauchy` in the product cover space `DirectedCoverSpace.precover ⨯ X` coincides with `DirectProdCover` — the bridge between the abstract product construction and the explicit eventual/pointwise description.
