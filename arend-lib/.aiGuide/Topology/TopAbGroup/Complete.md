### Topology.TopAbGroup.Complete

Completions of topological abelian groups as complete topological abelian groups.

This module combines the completion construction for uniform spaces with the abelian group structure to produce the completion of a topological abelian group. The completion is built on regular Cauchy filters: addition is defined via a cover map on the product of completions, and subtraction is obtained by composing addition with negation. The universal property is provided by `dense-topAb-lift`, which extends a continuous group homomorphism along a dense embedding into a complete target, while `completion-topAb` packages the unit map of the completion as a topological group homomorphism.

#### Main Class

- **`CompleteTopAbGroup`**: A topological abelian group that is also a complete uniform space. Extends `TopAbGroup` and `CompleteUniformSpace`.

#### Universal Property

- **`dense-topAb-lift`**: Extension of a topological abelian group homomorphism `g : X -> Z` along a dense topological embedding `f : X -> Y` into a complete topological abelian group `Z`, yielding a `TopAbGroupMap Y Z`. The continuous map part is obtained from `dense-uniform-lift` and additivity is preserved.

#### Completion Construction

- **`TopAbGroupCompletion`**: Instance making `UniformCompletion X` into a `CompleteTopAbGroup` for any `TopAbGroup X`. Equips the space of regular Cauchy filters with the abelian group structure inherited from `X` and proves continuity of the operations.

#### Group Structure on Regular Cauchy Filters (in `TopAbGroupCompletion`'s `\where`)

- **`abGroup`**: The abelian group `AbGroup (RegularCauchyFilter X)` with zero given by `pointCF 0`, addition by `+-func`, and negation by `negative-cover`.
- **`subtract-cover`**: Cover map `Completion X ⨯ Completion X -> Completion X` implementing subtraction, defined as `lift2 (+-uniform ∘ prod id negative-uniform)`.
- **`subtract_-`**: Identifies `subtract-cover (F, G)` with the abelian group difference `F - G` in `abGroup`.
- **`+-cover`**: Cover map for addition, obtained as `subtract-cover ∘ prod id negative-cover`.
- **`+-func`**: The addition function on regular Cauchy filters, applying `+-cover` pointwise.
- **`+-char`**: Compatibility of completion with addition: `+-cover (pointCF x, pointCF y) = pointCF (x + y)`.
- **`negative-cover`**: Cover map implementing negation on the completion, defined as subtraction from zero.
- **`negative-char`**: Compatibility of completion with negation: `negative-cover (pointCF x) = pointCF (negative x)`.
- **`topAb-neighborhood`**: Characterization of neighborhoods of `F - G` in the completion: `single (F - G) <=< U` iff there exist a refining cover `U'`, sets `V1 ∈ F`, `V2 ∈ G`, and pointwise membership `U' (pointCF (x - y))` for `x ∈ V1`, `y ∈ V2`. Used to prove continuity of the group operations.

#### Unit Map

- **`completion-topAb`**: The canonical topological abelian group homomorphism `X -> TopAbGroupCompletion X`, lifting `uniform-completion` with proof of additivity. Provides the dense embedding through which `dense-topAb-lift` extends maps.
