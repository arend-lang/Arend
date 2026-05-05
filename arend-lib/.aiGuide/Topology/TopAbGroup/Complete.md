### Topology.TopAbGroup.Complete

Complete topological abelian groups, the completion of a topological abelian group, and the universal property for extending continuous group homomorphisms to the completion.

#### Classes

- **`CompleteTopAbGroup`**: Extends `TopAbGroup` and `CompleteUniformSpace`. A topological abelian group whose underlying uniform space is complete.

#### Universal Property

- **`dense-topAb-lift`**: Given a dense topological-group embedding `f : X -> Y` and a continuous group homomorphism `g : X -> Z` into a complete topological abelian group `Z`, produces the unique extension `Y -> Z` as a `TopAbGroupMap`.

#### Completion of a Topological Abelian Group

- **`TopAbGroupCompletion`**: Instance making the uniform completion of a `TopAbGroup X` into a `CompleteTopAbGroup`. The carrier is `RegularCauchyFilter X`, with group operations lifted from `X`.
- **`TopAbGroupCompletion.abGroup`**: The abelian group structure on `RegularCauchyFilter X`, with zero given by `pointCF 0`, addition by `+-func`, and negation by `negative-cover`.
- **`TopAbGroupCompletion.subtract-cover`**: The continuous (cover) subtraction map `Completion X ⨯ Completion X -> Completion X`, built as `+-uniform ∘ prod id negative-uniform` lifted to the completion.
- **`TopAbGroupCompletion.subtract_-`**: Identifies `subtract-cover (F, G)` with the group difference `F - G` in the completed abelian group.
- **`TopAbGroupCompletion.+-cover`**: The continuous addition cover map on the completion, defined via `subtract-cover` and `negative-cover`.
- **`TopAbGroupCompletion.+-func`**: Underlying binary operation on `RegularCauchyFilter X` used as the group addition.
- **`TopAbGroupCompletion.+-char`**: Characterizes addition on point filters: `+-cover (pointCF x, pointCF y) = pointCF (x + y)`.
- **`TopAbGroupCompletion.negative-cover`**: The continuous negation cover map on the completion.
- **`TopAbGroupCompletion.negative-char`**: Characterizes negation on point filters: `negative-cover (pointCF x) = pointCF (negative x)`.
- **`TopAbGroupCompletion.topAb-neighborhood`**: Neighborhood criterion for the difference of two regular Cauchy filters: `single (F - G) <=< U` iff there exist a refinement `U' <=< U` and sets `V1 ∈ F`, `V2 ∈ G` such that `pointCF (x - y) ∈ U'` for all `x ∈ V1`, `y ∈ V2`.

#### Canonical Embedding

- **`completion-topAb`**: The canonical `TopAbGroupMap X -> TopAbGroupCompletion X`, sending each point to its principal regular Cauchy filter; serves as the unit of the completion.
