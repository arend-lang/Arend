### Set.Filter

Filters on meet-semilattices and on subset lattices, including proper/weakly-proper variants and their semilattice structure.

#### Filter Classes

- **`Filter`**: A filter on a `TopMeetSemilattice` `A`: a predicate `F : A -> \Prop` closed upward (`filter-mono`), containing `top` (`filter-top`), and closed under finite meets (`filter-meet`).
- **`CompleteFilter`**: Extends `Filter` over a `CompleteLattice`; adds `filter-Join` requiring that if `F (Join f)` holds, then some `f j` is in `F` (a completeness/compactness condition).
- **`SetFilter`**: Extends `Filter` specialized to `A := SetLattice X` for a set `X`, i.e. a filter of subsets of `X`.
- **`WeaklyProperFilter`**: Extends `SetFilter` with `isWeaklyProper`: the empty set (`bottom`) is not in the filter.
- **`ProperFilter`**: Extends `WeaklyProperFilter` with `isProper`: every set in the filter is inhabited (`F U -> ∃ U`); weak properness follows automatically.

#### Constructions

- **`Filter.principal`**: The principal filter at `a : A` on a `TopMeetSemilattice`, given by `F b := a <= b`.
- **`pointFilter`**: The principal ultrafilter at a point `x : X`, defined by `F U := U x`; produces a `ProperFilter X`.

#### Pushforward Along Maps

- **`SetFilter-map`**: Pushforward of a `SetFilter X` along `f : X -> Y`, given by `F V := F (f ^-1 V)`.
- **`WeaklyProperFilter-map`**: Pushforward preserving weak properness.
- **`ProperFilter-map`**: Pushforward preserving properness.

#### Semilattice Structure

- **`WeaklyProperFilterSemilattice`**: `MeetSemilattice` instance on `WeaklyProperFilter X`, ordered by inclusion `F ⊆ G`; the meet is the pointwise intersection filter `\lam U => \Sigma (F U) (G U)`.
- **`ProperFilterSemilattice`**: `MeetSemilattice` instance on `ProperFilter X`, ordered by inclusion; meet is the pointwise intersection filter, with properness inherited from the left component.
