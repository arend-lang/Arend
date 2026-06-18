### Algebra.Monoid.MonoidCategory

The category of monoids and its bicompleteness, plus kernel/image constructions for monoid homomorphisms.

This module assembles `MonoidCat` (and the additive analogue `AddMonoidCat`) as categories whose morphisms are monoid homomorphisms, and upgrades `MonoidCat` to a bicomplete category by exhibiting it as equivalent to the category of models of the algebraic theory of monoids (one nullary operation for the unit, one binary operation for multiplication, with unit and associativity axioms). Limits and colimits are then transported from `ModelCat` via this equivalence, giving completeness and cocompleteness for free. The remaining constructions package the kernel and image of a (additive) monoid homomorphism as monoid objects in their own right, together with the canonical inclusion/projection homs, with abelian variants when the codomain is commutative.

#### Categories of Monoids

- **`MonoidCat`**: The category `Cat Monoid` whose homs are `MonoidHom`, with composition lifted through pointwise application. Includes a forgetful functor `MonoidCat.forget : Functor MonoidCat SetCat`.
- **`AddMonoidCat`**: The additive analogue: `Cat AddMonoid` with `AddMonoidHom` morphisms and a forgetful functor `AddMonoidCat.forget : Functor AddMonoidCat SetCat`.

#### Bicompleteness via Algebraic Theory

- **`MonoidBicat`**: Promotes `MonoidCat` to a `BicompleteCat` by transporting limits and colimits from `ModelCat theory` along `catEquiv`.
- **`MonoidBicat.theory`**: The single-sorted algebraic `Theory` of monoids: function symbols `Fin 2` (arities 0 and 2), no predicates, axioms expressing left/right unit and associativity.
- **`MonoidBicat.catEquiv`**: A `CatEquiv` between `ModelCat theory` and `MonoidCat`, with left adjoint `monoidToMod.functor`, unit `id`, and counit interpreting a monoid model as a monoid.
- **`MonoidBicat.modToMonoid`**: Converts a `Model theory` to a `Monoid` by reading `ide` from the nullary operation and `*` from the binary operation.
- **`MonoidBicat.monoidToMod`**: Converts a `Monoid` into a `Model` of the theory; its `\where`-functor `monoidToMod.functor : Functor MonoidCat (ModelCat theory)` provides the inverse direction of the equivalence.

#### Kernels of Additive Monoid Homomorphisms

- **`KerAddMonoid`**: The kernel of `f : AddMonoidHom` as an `AddMonoid`, extending `KerAddPointed f` with componentwise addition that preserves the kernel condition.
- **`KerMonoidHom`**: The canonical inclusion `AddMonoidHom (KerAddMonoid f) f.Dom`, extending `KerPointedHom`.
- **`KerAbMonoid`**: When the domain is an `AbMonoid`, upgrades `KerAddMonoid f` to an abelian additive monoid.

#### Images of (Additive) Monoid Homomorphisms

- **`ImageAddMonoid`**: The image of `f : AddMonoidHom` as an `AddMonoid`, with addition built from preimages via the propositional truncation.
- **`ImageMonoid`**: The multiplicative analogue: image of `f : MonoidHom` as a `Monoid`.
- **`ImageMonoidLeftHom`**: The corestriction `MonoidHom f.Dom (ImageMonoid f)` onto the image.
- **`ImageMonoidRightHom`**: The inclusion `MonoidHom (ImageMonoid f) f.Cod` of the image into the codomain.
- **`ImageAddMonoidLeftHom`**, **`ImageAddMonoidRightHom`**: The additive analogues of the corestriction and inclusion homs.
- **`ImageAbMonoid`**: When the codomain is an `AbMonoid`, the image inherits abelianness, giving an `AbMonoid` structure on `ImageAddMonoid f`.
