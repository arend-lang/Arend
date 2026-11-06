### Algebra.Ring.RingCat

Category-theoretic structure on rings and commutative rings, with forgetful functors and a model-theoretic presentation.

This module assembles `Ring` and `CRing` into categories, equips them with the standard forgetful functors (to abelian groups, monoids, and sets), and characterizes isomorphisms in terms of bijectivity. The commutative case is presented twice: directly as a subcategory of `RingCat`, and as the category of models of an algebraic theory with five operation symbols (`0`, `1`, `+`, `*`, `negative`). The latter equivalence (`catEquiv`) transfers (co)completeness from `ModelCat` to `CRingCat`, yielding `BicompleteCat` for free, and shows that the forgetful functor to `Set` creates limits.

#### Ring Category

- **`RingCat`**: The category of rings with `RingHom` morphisms.
- **`RingCat.natCoefUnique`**: For ring homomorphisms equal to the identity on the underlying set, the natural-number coefficient maps agree.

#### Forgetful Functors from `RingCat`

- **`RingCat.forgetToAbGroup`**: Functor `RingCat -> AbGroupCat` discarding multiplicative structure.
- **`RingCat.forgetToMonoid`**: Functor `RingCat -> MonoidCat` discarding additive structure.
- **`RingCat.forget`**: Functor `RingCat -> SetCat` to underlying sets.

#### Isomorphisms in `RingCat`

- **`RingCat.Group-Iso->Ring-Iso`**: A ring homomorphism whose underlying group homomorphism is an iso in `GroupCat` is itself an iso in `RingCat`.
- **`RingCat.Iso<->Inj+Surj`**: A ring homomorphism is an iso iff it is injective and surjective.
- **`RingCat.image-iso`**: For a surjective `f : RingHom R S`, the image ring `ImRing f` is isomorphic in `RingCat` to the codomain `S`.

#### Commutative Ring Category

- **`CRingCat`**: The category of commutative rings, built as a `subCat` of `RingCat` via the embedding that forgets `*-comm` (using `prop-dpi` to handle the propositional commutativity field).
- **`CRingCat.forgetToRing`**: Functor `CRingCat -> RingCat`.
- **`CRingCat.forget`**: Functor `CRingCat -> SetCat`.
  - **`CRingCat.forget.reflectsLimit`**: The forgetful functor reflects limits, proved directly from the corresponding property of `SetBicat` limits and the fact that ring operations are determined pointwise.
  - **`CRingCat.forget.preservesLimit`**: The forgetful functor preserves limits.

#### Bicomplete Structure via Algebraic Theories

- **`LimitCRing`**: The limit of a diagram `F : J -> CRingCat` as a commutative ring: compatible families of elements, with operations defined pointwise (`LimitCRing.limProj` and `limMap` give the projections and the induced map from a cone).
- **`CRingBicat`**: `CRingCat` as a `BicompleteCat`; (co)limits are transported from `ModelCat theory` along `catEquiv`.
- **`CRingBicat.theory`**: The single-sorted algebraic theory of commutative rings, with five operation symbols (`0` arity 0, `1` arity 0, `+` arity 2, `*` arity 2, `negative` arity 1) and the standard ring axioms (associativity, commutativity, units, inverse, distributivity).
- **`CRingBicat.modToRing`**: Builds a `CRing` from a model of `theory` by interpreting each operation symbol.
- **`CRingBicat.ringtoMod`**: Builds a model of `theory` from a `CRing` by reading off `0`, `1`, `+`, `*`, `negative`.
  - **`CRingBicat.ringtoMod.functor`**: Promotes `ringtoMod` to a functor `CRingCat -> ModelCat theory`.
- **`CRingBicat.catEquiv`**: A categorical equivalence `CatEquiv CRingCat (ModelCat theory)` with `ringtoMod.functor` as the left adjoint and `modToRing.functor` as the right adjoint; both unit and counit are identity-on-carriers.
- **`CRingBicat.createsLimits`**: For any small diagram `F : J -> CRingCat`, the forgetful functor to `Set` creates the limit: it is given by `CRingBicat.limit F`, with `CRingCat.forget.preservesLimit` and `CRingCat.forget.reflectsLimit`.
