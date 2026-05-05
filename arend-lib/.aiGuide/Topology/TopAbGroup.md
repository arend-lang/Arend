### Topology.TopAbGroup

Topological abelian groups: abelian groups equipped with a topology making addition and negation continuous, automatically inducing a uniform structure from neighborhoods of zero.

#### Main Class

- **`TopAbGroup`**: Extends `TopSpace`, `AbGroup`, and `UniformSpace`. A topological abelian group with continuous addition (`+-cont`) and negation (`negative-cont`). The uniform structure is derived from neighborhoods of `0`: a cover is uniform iff there exists an open `U` containing `0` such that every point `x` has a cover element `V` containing all `y` with `U (x - y)`. Provides full implementations of `uniform-cover`, `uniform-top`, `uniform-refine`, `uniform-inter`, `uniform-star`, and `cauchy-open` from the neighborhood characterization.

#### Auxiliary Lemma

- **`shrink'`**: Given continuous `+` and `negative` on an additive group with a topology, and an open neighborhood `U` of `0`, produces an open `V ∋ 0` such that `U (x - y)` holds for all `x y ∈ V`. Used to construct symmetric/star-refining neighborhoods.

#### Star-Refinement Relation

- **`<=<ta`** (infix 4): Relation `V <=<ta U` meaning `∀ {x y : V}, U (x - y)` — `V` is "small enough" relative to `U` in the additive sense.
- **`<=<ta-left`**: From `V <=<ta U`, `V (x - z)`, and `V (y - z)`, deduce `U (x - y)`.
- **`<=<ta-right`**: From `V <=<ta U`, `V (x - y)`, and `V (x - z)`, deduce `U (y - z)`.
- **`<=<ta_<=`**: If `V` contains `0` and `V <=<ta U`, then `V ⊆ U`.
- **`<=<ta_negative`**: The relation `<=<ta` is preserved by pulling back along `negative`.
- **`<=<ta_<=*-shifted`**: Given a chain `W <=<ta V <=<ta U` with `W` open and containing `0`, the uniform ball `UBall W x` is `<=*`-below `UBall U x`.
- **`<=<ta_<=*`**: Given a chain `W <=<ta V <=<ta U` with `W` open and containing `0`, then `W <=* U` in the uniform-space sense.

#### Continuity Characterizations

- **`topAb-contAt`**: A function `f : X -> Y` between topological abelian groups is continuous at `x` iff for every open `V ∋ 0` in `Y` there is an open `U ∋ 0` in `X` with `U (x - x') -> V (f x - f x')`. The familiar ε-δ form for topological groups.
- **`topAb-sub-contAt`**: Same characterization for functions defined on a subspace `Set.Total S` of `X`, expressed via `TopSub S`.

#### Hausdorff Variant

- **`HausdorffTopAbGroup`**: Extends `TopAbGroup`, `HausdorffTopSpace`, and `SeparatedCoverSpace`. A Hausdorff topological abelian group, automatically a separated cover space.

#### Morphisms

- **`TopAbGroupMap`**: Extends `ContMap`, `AddGroupHom`, and `UniformMap`. A continuous group homomorphism between topological abelian groups, with `Dom` and `Cod` overridden to `TopAbGroup`. Uniformity (`func-uniform`) is derived automatically from continuity together with the additive structure (`func-zro`, `func-minus`), so any continuous additive homomorphism is uniformly continuous.
