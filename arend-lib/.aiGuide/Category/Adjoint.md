### Category.Adjoint

Adjoint functors and categorical equivalences between precategories.

This module formalizes adjunctions through the unit/counit (triangle identities) presentation: an `Adjunction C D` consists of functors `LAdj : C -> D` and `RAdj : D -> C`, a unit `eta : Id -> RAdj ∘ LAdj`, a counit `epsilon : LAdj ∘ RAdj -> Id`, and the two zigzag laws. A `CatEquiv` strengthens an adjunction to an equivalence of categories by requiring `eta` and `epsilon` to be componentwise isomorphisms.

#### Adjunction Core

- **`Adjunction`**: Record of an adjunction between precategories `C` and `D`: a left adjoint `LAdj : Functor C D`, a right adjoint `RAdj : Functor D C`, unit `eta`, counit `epsilon`, and the two triangle identities `eta_epsilon-left` and `eta_epsilon-right` saying the zigzag composites are identities.
- **`Adjunction.eta_epsilon-equiv`**: The hom-set adjunction bijection `Hom (LAdj X) Y ≃ Hom X (RAdj Y)` given by `f ↦ Func f ∘ eta X` with inverse `g ↦ epsilon Y ∘ LAdj.Func g`, derived from the triangle identities.
- **`Adjunction.eta_Iso`**: When `eta (RAdj Y)` is an iso, identifies `Func (epsilon Y)` with its inverse.
- **`Adjunction.preservesLimits`**: The right adjoint `RAdj` preserves all limits (`PreservesLimit RAdj G` for every diagram `G`).
- **`Adjunction.rightFactor`**: Given a fully faithful `G1 : D -> E`, a `G2 : C -> D`, and an adjunction `E ⇄ C` whose right adjoint is `Comp G1 G2`, transports it to an adjunction `D ⇄ C` with right adjoint `G2` and left adjoint `Comp adj.LAdj G1`.

#### Adjunctions from a Hom-Set Equivalence


#### Categorical Equivalences

- **`CatEquiv`**: Extends `Adjunction` with `eta-iso` and `epsilon-iso` asserting that every component of the unit and counit is an isomorphism.
- **`CatEquiv.RAdj-FF`**: The right adjoint of a categorical equivalence is fully faithful: `RAdj.Func` is an equivalence on every hom-set.
- **`CatEquiv.op`**: The opposite categorical equivalence between `C^op` and `D^op`, with unit and counit obtained by transposing the inverses of the originals.
- **`CatEquiv.reverse`**: Swaps the roles of `LAdj` and `RAdj`, giving a categorical equivalence `CatEquiv D C` in the reverse direction with unit and counit given by the inverses of the original counit and unit.
