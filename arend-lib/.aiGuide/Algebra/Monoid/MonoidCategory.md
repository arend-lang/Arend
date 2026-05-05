### Algebra.Monoid.MonoidCategory

The categories of monoids and additive monoids, their bicompleteness via algebraic theories, and kernel/image constructions.

#### Monoid Category

- **`MonoidCat`**: The category `Cat Monoid` with `MonoidHom` as morphisms, identity and composition of monoid homomorphisms, and univalence via `sip`.
- **`MonoidCat.forget`**: The forgetful functor `MonoidCat -> SetCat` taking a monoid to its underlying set.

#### Bicomplete Structure on Monoids

- **`MonoidBicat`**: Instance of `BicompleteCat` on `MonoidCat`, obtaining limits and colimits by transporting along an equivalence with the category of models of the monoid theory.
- **`MonoidBicat.theory`**: The algebraic `Theory` of monoids: a single sort, two operation symbols (unit of arity 0 and multiplication of arity 2), no predicates, and axioms for left/right unit laws and associativity.
- **`MonoidBicat.catEquiv`**: Categorical equivalence `CatEquiv (ModelCat theory) MonoidCat` along `modToMonoid`, with unit, counit, and isomorphism witnesses provided as identities.
- **`MonoidBicat.modToMonoid`**: Converts a `Model theory` into a `Monoid` on its underlying set, deriving the monoid laws from `M.isModel` applied to the theory's axioms.
- **`MonoidBicat.monoidToMod`**: Converts a `Monoid` into a `Model theory`, interpreting symbol 0 as `ide` and symbol 1 as `*`.
- **`MonoidBicat.monoidToMod.functor`**: Functor `MonoidCat -> ModelCat theory` lifting `monoidToMod` to morphisms via `MonoidHom`'s `func-ide` and `func-*`.

#### Additive Monoid Category

- **`AddMonoidCat`**: The category `Cat AddMonoid` with `AddMonoidHom` morphisms; analogous structure to `MonoidCat` for additive monoids, with univalence via `sip`.
- **`AddMonoidCat.forget`**: Forgetful functor `AddMonoidCat -> SetCat`.

#### Kernels

- **`KerAddMonoid`**: The kernel of an `AddMonoidHom f` as an `AddMonoid`, extending `KerAddPointed f` with pointwise addition restricted to elements mapping to zero.
- **`KerMonoidHom`**: The canonical inclusion `AddMonoidHom (KerAddMonoid f) f.Dom`.
- **`KerAbMonoid`**: The kernel of a homomorphism into an `AbMonoid` as an `AbMonoid`, extending `KerAddMonoid` with commutativity inherited from the codomain.

#### Images

- **`ImageAddMonoid`**: The image of an `AddMonoidHom` as an `AddMonoid`, extending `ImageAddPointed` with pointwise addition and a witness combining preimages via `func-+`.
- **`ImageMonoid`**: The image of a `MonoidHom` as a `Monoid`, extending `ImagePointed` with pointwise multiplication and preimage witnesses via `func-*`.
- **`ImageMonoidLeftHom`**: The surjection `f.Dom -> ImageMonoid f` extending `ImagePointedLeftHom`.
- **`ImageMonoidRightHom`**: The injection `ImageMonoid f -> f.Cod` extending `ImagePointedRightHom`.
- **`ImageAddMonoidLeftHom`**: Additive analogue of `ImageMonoidLeftHom`.
- **`ImageAddMonoidRightHom`**: Additive analogue of `ImageMonoidRightHom`.
- **`ImageAbMonoid`**: The image of an `AddMonoidHom` into an `AbMonoid` as an `AbMonoid`, inheriting commutativity from the codomain.
