### Category.Adjoint

Adjoint functors and categorical equivalences between precategories.

This module formalizes adjunctions through the unit/counit (triangle identities) presentation: a `RightAdjoint` is a functor `F : C -> D` together with a left adjoint `LAdj : D -> C`, a unit `eta : Id -> F ∘ LAdj`, a counit `epsilon : LAdj ∘ F -> Id`, and the two zigzag laws. Specialized variants `RightAdjointUnit` and `RightAdjointCounit` reconstruct the entire adjunction from just one of the natural transformations together with a hom-set bijection, which is convenient when only one half is naturally available. A `CatEquiv` strengthens an adjunction to an equivalence of categories by requiring `eta` and `epsilon` to be componentwise isomorphisms, and inherits `FullyFaithfulFunctor` automatically.

#### Adjunction Core

- **`RightAdjoint`**: Class extending `Functor` (the right adjoint `F : C -> D`). Bundles the left adjoint `LAdj`, unit `eta`, counit `epsilon`, and the two triangle identities `eta_epsilon-left` and `eta_epsilon-right` saying the zigzag composites are identities.
- **`RightAdjoint.eta_epsilon-equiv`**: The hom-set adjunction bijection `Hom (LAdj X) Y ≃ Hom X (F Y)` given by `f ↦ Func f ∘ eta X` with inverse `g ↦ epsilon Y ∘ LAdj.Func g`, derived from the triangle identities.
- **`RightAdjoint.eta_Iso`**: When `eta (F Y)` is an iso, identifies `Func (epsilon Y)` with its inverse.
- **`RightAdjoint.rightFactor`**: Given a fully faithful `G1 : D -> E`, a `G2 : E -> D`, and a right adjoint structure on `Comp G1 G2`, transports the adjunction to a right adjoint structure on `G2` itself with left adjoint `Comp adj.LAdj G1`.

#### Adjunctions from a Hom-Set Equivalence

- **`RightAdjointUnit`**: Class extending `RightAdjoint` that constructs the adjunction from the unit and a hom-set equivalence `eta-adjoint : Hom (LAdj X) Y ≃ Hom X (F Y)` (transposition along `eta`); the counit and triangle identities are derived.
- **`RightAdjointCounit`**: Dual presentation extending `RightAdjoint`: builds the adjunction from the counit and a hom-set equivalence `epsilon-adjoint : Hom X (F Y) ≃ Hom (LAdj X) Y` (transposition along `epsilon`); the unit and triangle identities are derived.

#### Categorical Equivalences

- **`CatEquiv`**: Class extending both `FullyFaithfulFunctor` and `RightAdjoint`, with `eta-iso` and `epsilon-iso` asserting that every component of the unit and counit is an isomorphism. The fully-faithful structure is constructed explicitly from the inverse of `epsilon` and the triangle identities.
- **`CatEquiv.op`**: The opposite categorical equivalence between `C^op` and `D^op`, with unit and counit obtained by transposing the inverses of the originals.
- **`CatEquiv.reverse`**: Swaps the roles of `F` and `LAdj`, exhibiting the left adjoint as a categorical equivalence in the reverse direction with unit and counit given by the inverses of the original counit and unit.
