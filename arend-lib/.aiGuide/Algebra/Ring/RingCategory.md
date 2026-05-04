### Algebra.Ring.RingCategory

Categorical structure on rings and commutative rings, including their categories, forgetful functors, isomorphism characterizations, and a presentation of `CRing` as models of an algebraic theory.

#### Ring Category

- **`RingCat`**: The category of rings with `RingHom` as morphisms. Establishes identity, composition, and proves univalence via the structure identity principle.
- **`RingCat.natCoefUnique`**: For a ring homomorphism that is the identity on the underlying set, the natural number coefficients agree: `R.natCoef n = S.natCoef n`. Used in the univalence proof.

#### Forgetful Functors from `RingCat`

- **`RingCat.forgetToAbGroup`**: Forgetful functor `RingCat -> AbGroupCat` discarding multiplication.
- **`RingCat.forgetToMonoid`**: Forgetful functor `RingCat -> MonoidCat` discarding addition.
- **`RingCat.forget`**: Forgetful functor `RingCat -> SetCat` to underlying sets.

#### Ring Isomorphisms

- **`RingCat.Group-Iso->Ring-Iso`**: A ring homomorphism whose underlying additive group homomorphism is an iso in `GroupCat` is an iso in `RingCat`.
- **`RingCat.Iso<->Inj+Surj`**: Characterizes ring isomorphisms as injective-and-surjective ring homomorphisms.
- **`RingCat.image-iso`**: For a surjective ring homomorphism `f`, the image ring `f.Image.IRing` is isomorphic to the codomain `f.Cod`.
- **`RingCat.Image=Cod`**: For a surjective `f : RingHom R S`, the image ring equals `S` as rings (via univalence).

#### Commutative Ring Category

- **`CRingCat`**: The category of commutative rings, constructed as a full subcategory of `RingCat` via `subCat` (commutativity is a proposition, so the embedding is a retraction).
- **`CRingCat.forgetToRing`**: Forgetful functor `CRingCat -> RingCat`.
- **`CRingCat.forget`**: Forgetful functor `CRingCat -> SetCat`, with submodules `reflectsLimit` and `preservesLimit` deriving these properties from `createsLimits`.
- **`CRingCat.Image=Cod`**: For a surjective ring homomorphism into a commutative ring, the commutative-ring image equals the codomain as commutative rings.

#### Bicompleteness of `CRingCat`

- **`CRingBicat`**: Witnesses that `CRingCat` is bicomplete (has all small limits and colimits), transported from the model category of the algebraic theory of commutative rings via `catEquiv`.

#### Algebraic Theory of Commutative Rings

- **`CRingBicat.theory`**: First-order algebraic theory with a single sort, five operation symbols (zero, one, addition, multiplication, negation) and the eight commutative-ring axioms (additive associativity/identity/commutativity, multiplicative associativity/identity/commutativity, additive inverse, left distributivity).
- **`CRingBicat.modToRing`**: Converts a model `M` of `theory` into a `CRing` on `M ()`, interpreting the operation symbols and deriving the ring axioms from `M.isModel`.
- **`CRingBicat.ringtoMod`**: Converts a `CRing` into a model of `theory`, with submodule `functor` packaging this as a functor `CRingCat -> ModelCat theory`.
- **`CRingBicat.catEquiv`**: Categorical equivalence `ModelCat theory ≃ CRingCat` between the model category of the theory and `CRingCat`, with `modToRing` as the underlying object map and `ringtoMod.functor` as the left adjoint.
- **`CRingBicat.createsLimits`**: The forgetful functor `CRingCat -> SetCat` creates limits for any small diagram.
