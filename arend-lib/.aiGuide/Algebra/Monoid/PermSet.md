### Algebra.Monoid.PermSet

The free commutative monoid on a set, constructed as arrays quotiented by permutation equivalence.

This module realizes `PermSet A` as the quotient of `Array A` by `EPerm` (extensional permutation), giving the free commutative monoid on `A` with concatenation as multiplication and the empty array as identity. Because the underlying carrier is a quotient of lists, definitions on `PermSet` are typically built by `\elim`-ing on `in~` and discharging the `~-equiv` case via permutation-invariance lemmas (e.g., `EPerm.eperm-++-left/right`, `EPerm.EPerm_map`, `BigProd_EPerm`). The universal property is captured by `permSet-univ`, which sends any function `A -> B` into a commutative monoid `B` by mapping pointwise and taking the big product, making `PermSet` the left adjoint to the forgetful functor from `CMonoid` to `\Set`.

#### Core Type and Quotient Helpers

- **`PermSet`**: The free commutative monoid on `A : \Set`, defined as `Quotient {Array A} EPerm`.
- **`inPS~`**: Embeds an array into `Quotient EPerm` via `in~`.
- **`inPS`**: Embeds an array into `PermSet A` via `in~`.
- **`unext`**: From an equality of quotient classes, extracts a truncated permutation `TruncP (EPerm l l')`.
- **`~-psequiv`**: Lifts an `EPerm l l'` to an equality `inPS l = inPS l'` in `PermSet A`.
- **`permSet-ext`**: Transports equalities from the underlying quotient `Quotient EPerm` to equalities in `PermSet A`.

#### Algebraic Structure

- **`PermSetDec`**: `DecSet` instance for `PermSet A` whenever `A : DecSet`.
- **`PermSetMonoid`**: Commutative monoid (`CMonoid`) instance with identity `in~ nil` and multiplication given by list concatenation, well-defined up to permutation via `EPerm.eperm-++-left/right`.

#### Functorial Action

- **`permSet-map`**: Functorial action `(A -> B) -> PermSet A -> PermSet B`, defined by mapping over the underlying array.
- **`permSet-map-comp`**: Functoriality: `permSet-map g ∘ permSet-map f = permSet-map (g ∘ f)`.
- **`permSet-hom`**: Packages `permSet-map f` as a `MonoidHom` between `PermSetMonoid A` and `PermSetMonoid B`.
- **`permSet-map_+`**: `permSet-map f` distributes over the monoid operation `*`.

#### Universal Property and Big Product

- **`permSet-sum`**: For `A : CMonoid`, sends `PermSet A` to `A` by taking the big product `BigProd` of the underlying list; well-defined since `BigProd` is permutation-invariant.
- **`permSet-sum-natural`**: Naturality of `permSet-sum` along a monoid homomorphism `f : A -> B`.
- **`permSet-sum_+`**: `permSet-sum` is a monoid homomorphism: it sends `x * y` to `permSet-sum x * permSet-sum y`.
- **`permSet-univ`**: Universal property: any `f : A -> B` into a commutative monoid extends uniquely to a `MonoidHom (PermSetMonoid A) B` via `permSet-sum ∘ permSet-map f`.
- **`permSet-univ-natural`**: Naturality statement matching `permSet-sum-natural`, packaged for the universal map.

#### Decidability and Length

- **`permSet-zro-dec`**: Decides whether an element of `PermSet A` is the identity, using `unext` and `EPerm.EPerm_len` to handle the non-empty case.
- **`permSet-length`**: Sends `p : PermSet A` to its length as a `Nat`, well-defined since permutations preserve length (`EPerm.EPerm_len`).

#### Generators and Powers

- **`permSet-split`**: Decomposes `inPS l` as the big product of singleton `PermSet`s `inPS (a :: nil)` over the elements of `l`.
- **`permSet-pow`**: The `n`-th monoid power of a singleton `inPS (a :: nil)` equals `inPS (replicate n a)`.
