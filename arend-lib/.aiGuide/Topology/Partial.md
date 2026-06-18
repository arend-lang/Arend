### Topology.Partial

Topological structure on partial elements of a space, where a partial value is either undefined or a defined point.

This module endows `Partial Y` with a topology in which an open set must, at every point it contains, either be the entire set of partial values or arise from an open subset of `Y` together with the definedness witness. The `totalOpen` lemma shows the subset of defined values is open, while `makeOpen` lifts open sets of `Y` to open sets of `Partial Y`. The continuity characterization and the `plift`/`plift2` lemmas ensure that partiality interacts well with continuous maps, so that lifting functions to partial inputs preserves continuity.

#### Topology Instance

- **`PartialTopSpace`**: `TopSpace` instance on `Partial Y` for a topological space `Y`. A set `U` is open iff for every `y ∈ U`, either `U` is the entire space or there exist an open `U' ⊆ Y`, a definedness witness for `y` with `y` mapping into `U'`, such that every defined point coming from `U'` lies in `U`.

#### Open Sets on Partial Spaces

- **`PartialTopSpace.totalOpen`**: The set of defined partial elements `\lam s => isDefined {s}` is open in `PartialTopSpace Y`.
- **`PartialTopSpace.makeOpen`**: Lifts an open set `U` of `Y` to the open set `\lam s => Σ (p : isDefined {s}) (U (s p))` in `PartialTopSpace Y`.

#### Continuity Lemmas

- **`PartialTopSpace-char`**: Characterizes continuity of `f : X -> Partial Y`: it is continuous iff the definedness predicate `\lam x => isDefined {f x}` is open in `X` and the restriction `\lam x => f x.1 x.2` on the total subspace is continuous into `Y`.
- **`plift-cont`**: Continuity is preserved by `plift`: a continuous `f : X -> Y` lifts to a continuous map `PartialTopSpace X -> PartialTopSpace Y`.
- **`plift2-cont`**: Two-argument version: a continuous `f : X ⨯ Y -> Z` lifts to a continuous map on `PartialTopSpace X ⨯ PartialTopSpace Y` into `PartialTopSpace Z` via `plift2`.
