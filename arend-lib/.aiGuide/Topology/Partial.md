### Topology.Partial

Topology on the type of partial elements `Partial Y`, making partial values into a topological space and lifting continuous maps along partiality.

#### Topology on Partial Elements

- **`PartialTopSpace`**: Instance making `Partial Y` a `TopSpace` for any `TopSpace Y`. A subset `U` of `Partial Y` is open iff for every `y ∈ U`, either `U` is the whole space, or there exists an open `U' ⊆ Y` containing the underlying value of `y` such that all defined elements coming from `U'` lie in `U`.

#### Canonical Open Sets

- **`PartialTopSpace.totalOpen`**: The subset of defined partial elements `{s : Partial Y | isDefined s}` is open in `PartialTopSpace Y`.
- **`PartialTopSpace.makeOpen`**: Lifts an open set `U ⊆ Y` to the open set `{s : Partial Y | ∃ p, U (s p)}` in `PartialTopSpace Y`.

#### Continuity Characterization and Lifting

- **`PartialTopSpace-char`**: A map `f : X -> Partial Y` is continuous iff its domain of definition `{x | isDefined (f x)}` is open in `X` and the restriction to this open subset (with the subspace topology) is a continuous map into `Y`.
- **`plift-cont`**: The partial lift `plift f : Partial X -> Partial Y` of a continuous map `f : X -> Y` is continuous between the corresponding partial topological spaces.
- **`plift2-cont`**: The binary partial lift of a continuous map `f : X ⨯ Y -> Z` yields a continuous map `PartialTopSpace X ⨯ PartialTopSpace Y -> PartialTopSpace Z`.
