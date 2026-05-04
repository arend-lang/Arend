### Category.Adjoint

Adjoint functors and categorical equivalences, expressed via unit/counit and via hom-set bijections.

#### Adjunctions

- **`RightAdjoint`**: Class extending `Functor` representing a right adjoint `F : C -> D`. Bundles the left adjoint `LAdj : D -> C`, unit `eta : Id -> F ∘ LAdj`, counit `epsilon : LAdj ∘ F -> Id`, and the two triangle identities `eta_epsilon-left` and `eta_epsilon-right`.
- **`RightAdjoint.rightFactor`**: Given a fully faithful `G1 : D -> E`, a functor `G2 : E -> D`, and an adjunction with right adjoint `Comp G1 G2`, factors it through `G1` to produce a `RightAdjoint` structure on `G2` itself. Used to transport adjointness across a fully faithful embedding.

#### Hom-Set Formulations

- **`RightAdjointUnit`**: Class extending `RightAdjoint` defined via the unit transposition. Requires `eta-adjoint : Equiv (Hom (LAdj X) Y) (Hom X (F Y))` given by `Func __ ∘ eta X`, and derives `epsilon`, `eta_epsilon-left`, and `eta_epsilon-right` from this hom-set bijection.
- **`RightAdjointCounit`**: Class extending `RightAdjoint` defined via the counit transposition. Requires `epsilon-adjoint : Equiv (Hom X (F Y)) (Hom (LAdj X) Y)` given by `epsilon Y ∘ LAdj.Func __`, and derives `eta`, `eta_epsilon-left`, and `eta_epsilon-right` from it.

#### Categorical Equivalences

- **`CatEquiv`**: Class extending `FullyFaithfulFunctor` and `RightAdjoint`, representing an equivalence of categories. Adds `eta-iso` (unit is iso at every object) and `epsilon-iso` (counit is iso at every object), and constructs the fully-faithful structure (the `QEquiv` on hom-sets) from these isomorphisms together with the triangle identities.
