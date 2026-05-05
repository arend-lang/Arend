### Topology.CoverSpace.CompletionTools

Utilities for lifting maps and proving uniqueness/inequality results on completions of cover spaces, leveraging density of the canonical embedding `pointCF : X -> Completion X`.

#### Binary Lifts

- **`lift2`**: Lifts a binary cover map `f : X ⨯ X -> X` to a cover map `Completion X ⨯ Completion X -> Completion X` via dense-lifting along the product embedding `prod completion completion`.
- **`lift2-char`**: Characterizes `lift2 f` on point filters: `lift2 f (pointCF x, pointCF y) = pointCF (f (x, y))`.

#### Uniqueness Lemmas (Equality)

- **`unique1`**: Two continuous maps `f, g : Completion X -> Completion X` agreeing on all `pointCF x` agree on every regular Cauchy filter.
- **`unique2`**: Binary version: continuous maps `Completion X ⨯ Completion X -> Completion X` agreeing on pairs of point filters agree everywhere.
- **`unique3`**: Ternary version: continuous maps on `Completion X ⨯ Completion X ⨯ Completion X` agreeing on triples of point filters agree everywhere.

#### Uniqueness Lemmas (Inequality)

- **`unique1_<=`**: If `f, g : ContMap (Completion X) ExUpperRealMetric` satisfy `f (pointCF x) <= g (pointCF x)` for all `x : X`, then `f x <= g x` for any regular Cauchy filter.
- **`unique2_<=`**: Binary inequality version, extending pointwise inequality on point filters to all pairs of regular Cauchy filters.

#### Neighborhood Characterizations of Lifted Maps

- **`completion-lift-neighborhood2`**: Characterizes the cover relation `single (cauchy-lift ... g (F, G)) <=< W` for a binary lift `g : X ⨯ Y -> Z` into a complete cover space `Z`: it holds iff there exist a refinement `W' <=< W` and sets `V1, V2` in the filters `F, G` with `g (V1 ⨯ V2) ⊆ W'`.
- **`strongCompletion-lift-neighborhood2`**: Strong analogue for strongly regular/complete cover spaces, using `weaklyDense-lift` along the strong completion embedding and the strong cover relation `s<=<`.
