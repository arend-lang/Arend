### Topology.CoverSpace.CompletionTools

Utilities for lifting maps to completions of cover spaces and reasoning about uniqueness of continuous extensions.

This module provides the standard machinery for working with completions of cover spaces: extending binary operations from a space `X` to its completion `Completion X`, and proving that two continuous maps on a completion agree everywhere whenever they agree on the dense subset of point filters `pointCF x`. The lifting construction relies on the dense embedding of `X` into `Completion X` (and the product version for binary operations), while the uniqueness lemmas capture the universal property that a continuous map out of a completion is determined by its values on point filters. Variants are provided for ordinary completions, for `<=` comparisons into `ExUpperRealMetric`, and for strong completions of strongly regular cover spaces.

#### Lifting Binary Maps

- **`lift2`**: Lifts a cover map `f : X ⨯ X -> X` to a cover map `Completion X ⨯ Completion X -> Completion X` using the dense embedding of `X ⨯ X` into the product of completions, post-composed with `completion`.
- **`lift2-char`**: Characterization of `lift2` on point filters: `lift2 f (pointCF x, pointCF y) = pointCF (f (x, y))`.

#### Uniqueness of Continuous Extensions

- **`unique1`**: Two continuous maps `f g : Completion X -> Completion X` are equal on any regular Cauchy filter whenever they agree on all point filters `pointCF x`.
- **`unique2`**: Binary version: agreement on pairs of point filters implies agreement on arbitrary pairs of regular Cauchy filters.
- **`unique3`**: Ternary version of the uniqueness principle, for maps from a triple product of completions.

#### Inequality Variants for Upper Reals

- **`unique1_<=`**: If `f, g : Completion X -> ExUpperRealMetric` satisfy `f (pointCF x) <= g (pointCF x)` for all `x : X`, then `f x <= g x` for any regular Cauchy filter `x`.
- **`unique2_<=`**: Binary version of the inequality extension principle for maps into `ExUpperRealMetric`.

#### Neighborhood Characterization for Lifted Maps

- **`completion-lift-neighborhood2`**: Characterizes when the lift of a binary cover map `g : X ⨯ Y -> Z` (with `Z` a complete cover space) sends a pair `(F, G)` of completion filters into a neighborhood of `W`: equivalent to the existence of `W' <=< W` and basic neighborhoods `V1 ∈ F`, `V2 ∈ G` with `g (V1 ⨯ V2) ⊆ W'`.
- **`strongCompletion-lift-neighborhood2`**: Strong-completion analogue, using `weaklyDense-lift` over strongly regular cover spaces and the strong neighborhood relation `s<=<` on the target strongly complete cover space.
