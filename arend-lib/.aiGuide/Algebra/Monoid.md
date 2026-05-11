### Algebra.Monoid

Algebraic hierarchy of semigroups, monoids, and their commutative and cancellative variants, together with divisibility and invertibility records.

This module sets up the foundational multiplicative structures used throughout the algebra library. `Semigroup` and `Monoid` are introduced as classes that other algebraic structures extend, with parallel `AddMonoid`/`AbMonoid` versions for additive notation (the `op` constructor and `fromMonoid`/`toMonoid` coercions let one switch sides freely). Divisibility is captured by witness records `LDiv`/`RDiv` carrying the explicit cofactor, and invertibility (`Inv`, `LInv`, `RInv`) is built on top of them as the special case where the elem is `ide`; this presentation makes inverses propositional and easy to manipulate. Iterated operations (`pow`, `*n`, `BigProd`, `BigSum`, and the `FinProd`/`FinSum` versions over finite sets) collect the standard combinatorial lemmas (associativity, splitting, permutation invariance) needed by the rest of the library.

#### Semigroups

- **`Semigroup`**: Class extending `BaseSet` with associative binary `*`.
- **`Semigroup.IsSquare`**: Predicate `∃ y, y * y = x`.
- **`Semigroup.op`**: Opposite semigroup with arguments flipped.
- **`CSemigroup`**: Commutative semigroup, adds `*-comm`.

#### Monoids

- **`Monoid`**: Class extending `Pointed` and `Semigroup`, with left and right identity laws for `ide`.
- **`Monoid.pow`**: Iterated product `pow a n = a * ... * a` (n times).
- **`Monoid.pow_+`**, **`pow_*`**: `pow a (n+m) = pow a n * pow a m` and `pow a (n*m) = pow (pow a n) m`.
- **`Monoid.pow-comm1`**, **`pow-comm2`**, **`pow-comm`**: Commutation lemmas for powers when the bases commute.
- **`Monoid.pow_ide`**, **`pow-left`**: `pow ide n = ide` and `a * pow a n = pow a n * a`.
- **`Monoid.op`**: Opposite monoid with `*` reversed.
- **`Monoid.equals`**: Extensionality for monoids — two monoids agree once their carriers, identities, and operations agree.
- **`CMonoid`**: Commutative monoid extending `Monoid` and `CSemigroup`; derives `ide-left`/`ide-right` from commutativity.
- **`CancelMonoid`**: Monoid with left and right cancellation.
- **`CancelCMonoid`**: Cancellative commutative monoid; right cancellation derived from commutativity and left cancellation.

#### Big Products

- **`Monoid.BigProd`**: `BigProd l = l 0 * l 1 * ... * ide` over an array.
- **`Monoid.BigProd-ext`**: Extensionality in the entries.
- **`Monoid.BigProd_ide`**: Vanishes to `ide` when all entries are `ide`.
- **`Monoid.BigProd-unique`**: Reduces to `l i` when all other entries equal `1`.
- **`Monoid.BigProd_++`**, **`BigProd_suc`**: Splitting on concatenation and on the last entry.
- **`Monoid.BigProd_replicate`**, **`BigProd_replicate1`**: `BigProd (replicate n x) = pow x n` and the `ide` variant.
- **`CMonoid.BigProd_*`**, **`BigProd_pow`**: Products distribute pointwise over `*` and over `pow`.
- **`CMonoid.BigProd_EPerm`**, **`BigProd_Perm`**: Permutation invariance.
- **`CMonoid.LDiv_BigProd`**, **`LDiv_BigProd-coord`**, **`LDiv_BigProd_TruncP`**: Each factor divides the product, and pointwise divisibility lifts to the product.

#### Finite Products (`FinProd`)

- **`CMonoid.FinProd`**: Product indexed by an arbitrary `FinSet`, defined via any chosen enumeration.
- **`CMonoid.FinProd_char`**, **`FinProd_char2`**: Characterization in terms of `BigProd` along an equivalence `Fin A.finCard ≃ A`.
- **`CMonoid.FinProd-const`**: Constant case reduces to `BigProd` of replicated values.

#### Divisibility (`LDiv`, `RDiv`)

- **`DivBase`**: Carrier record with `val`, `elem`, and a candidate cofactor `inv`.
- **`LDiv`**: Left divisibility: `val * inv = elem`.
- **`LDiv.make`**, **`product`**, **`product-left`**, **`product-right`**: Constructor and product-stability of `LDiv`.
- **`LDiv.factor-left`**, **`factor-right`**: `x | y` implies `x | (y * z)` (left/right placement).
- **`LDiv.cancel-left`**, **`cancel-right`**: Cancel a common factor in cancellative monoids.
- **`LDiv.trans`**: Transitivity of divisibility.
- **`LDiv.ide-div`**, **`id-div`**: `ide | x` and `x | x`.
- **`LDiv.swap`**: Swap `val` and `inv` in a commutative monoid.
- **`LDiv.idempt`**: Idempotence consequence `l * l.elem = l.elem` when `l * l = l`.
- **`LDiv.cancelProp`**, **`fromTruncP`**, **`levelProp`**: Propositional level of `LDiv` in the presence of cancellation.
- **`RDiv`**: Right divisibility (`inv * val = elem`) with analogous `product-right`, `cancel-right`, `trans`, `levelProp`.

#### Invertibility (`LInv`, `RInv`, `Inv`)

- **`LInv`**: Left inverse — `RDiv` with `elem = ide`. Provides `cancel`: `x * y = x * z` implies `y = z`.
- **`RInv`**: Right inverse — `LDiv` with `elem = ide`. Provides `cancel` for right multiplication.
- **`Inv`**: Two-sided inverse extending both, with `reverse` swapping `val` and `inv`.
- **`Inv.inv-isUnique`**, **`levelProp`**: Inverses are unique and `Inv x` is propositional.
- **`Inv.ide-isInv`**: `ide` is invertible.
- **`Inv.product`**, **`prod`**: Product of invertibles is invertible with explicit cofactor `j.inv * i.inv`.
- **`Inv.factor-left`**, **`factor-right`**: Factor an `Inv (x * y)` into `Inv x` (resp. `Inv y`) given a one-sided inverse on the other side.
- **`Inv.lmake`**, **`rmake`**, **`ldiv`**, **`rdiv`**: Convenient constructors in commutative monoids from one-sided equations or divisibility witnesses.
- **`Inv.cfactor-left`**, **`cfactor-right`**: In a `CMonoid`, invertibility of a product gives invertibility of each factor.
- **`CMonoid.divInv`**, **`CancelCMonoid.divInv`**: From `LDiv (x * y) x` deduce `Inv y` (using cancellation or commutativity).

#### Associates

- **`associates`**: `x` and `y` differ by an invertible factor: `Σ (u : Inv) (x = u * y)`.
- **`associates.levelProp`**: In a `CancelMonoid`, being associates is propositional.
- **`associates-sym`**: Symmetry of the associates relation.

#### Additive Monoids

- **`AddMonoid`**: Additive analogue of `Monoid`, extending `AddPointed` with `+`, `zro`, identity laws, and `+-assoc`.
- **`AddMonoid.negative-unique`**: Two-sided inverses for `+` are unique.
- **`AddMonoid.*n`**: Iterated addition `n *n a`.
- **`AddMonoid.*n_pow`**, **`*n-rdistr`**, **`*n-assoc`**: Relate `*n` to `pow` and prove distributivity/associativity in `n,m`.
- **`AddMonoid.op`**: Opposite additive monoid.
- **`AddMonoid.fromMonoid`**, **`toMonoid`**: Coercions between additive and multiplicative presentations.
- **`AbMonoid`**: Commutative additive monoid extending `AddMonoid` with `+-comm`; derives `zro-left`/`zro-right` from commutativity.
- **`AbMonoid.fromCMonoid`**, **`toCMonoid`**: Coercions to/from `CMonoid`.
- **`AbMonoid.*n-ldistr`**: Left distributivity `n *n (a + b) = n *n a + n *n b`.

#### Big Sums

- **`AddMonoid.BigSum`**: Sum of an array, dual to `BigProd`.
- **`AddMonoid.BigSum-ext`**: Extensionality.
- **`AddMonoid.BigSum_++`**, **`BigSum_Big++`**, **`BigSum_++'`**: Splitting on concatenation, including a flattening lemma over arrays of arrays.
- **`AddMonoid.BigSum-split`**: Splits a sum of length `n + m` into two sub-sums via `fin-inc` and `fin-raise`.
- **`AddMonoid.BigSum_suc`**, **`BigSum_suc-nat`**: Pull the last summand out.
- **`AddMonoid.BigSum_zro`**, **`BigSum_replicate0`**: Vanishing when entries are `0`.
- **`AddMonoid.BigSum-unique`**, **`BigSum-unique2`**: Reduce to one or two non-zero entries.
- **`AddMonoid.BigSum-pred`**: A predicate closed under `0` and `+` is preserved by `BigSum`.
- **`AddMonoid.BigSum-subset`**: Equate two sums when one array is a prefix-style subset of the other (extra entries zero).
- **`AddMonoid.fit_BigSum`**, **`fit-transpose`**: Truncate trailing zeros and transpose `fit`-padded sums.
- **`AbMonoid.BigSum_+`**: Pointwise additivity.
- **`AbMonoid.BigSum-transpose`**: Swap order of nested sums.
- **`AbMonoid.BigSum-double-dep`**, **`BigSum_Or`**: Reindex nested/sigma-indexed and `Or`-indexed sums.
- **`AbMonoid.BigSum_EPerm`**, **`BigSum_Perm`**: Permutation invariance.

#### Finite Sums (`FinSum`)

- **`AbMonoid.FinSum`**: Sum indexed by an arbitrary `FinSet`.
- **`AbMonoid.FinSum_char`**, **`FinSum_char2`**: Characterization via `BigSum` along an equivalence `Fin A.finCard ≃ A`.
- **`AbMonoid.FinSum_zro`**, **`FinSum_+`**, **`FinSum-const`**: Basic algebraic identities.
- **`AbMonoid.FinSum-double-dep`**, **`FinSum-double`**: Reduce nested `FinSum`s to a single sum over a sigma/product.
- **`AbMonoid.FinSum=BigSum`**: Agreement with `BigSum` for `Fin n` indices.
- **`AbMonoid.FinSum_Equiv`**, **`FinSum_Equiv2`**: Reindexing along an equivalence.
- **`AbMonoid.FinSum-inj`**: Reindex along an injection, dropping summands not in the image.
- **`AbMonoid.FinSum_Or`**: Split a sum over `Or A B` into `A` and `B` parts.
- **`AbMonoid.FinSum-unique`**, **`FinSum-unique2`**: Reduce to one or two non-zero summands.
- **`AbMonoid.FinSum-pred`**: Predicate-preservation analogue of `BigSum-pred`.

#### Regularity and Chain Conditions

- **`CMonoid.IsRegularElem`**: An element `a` is regular if `a * x = a * y` implies `x = y`.
- **`CMonoid.LDiv_IsRegular`**: Divisors of regular elements are regular.
- **`CMonoid.DivChain`**: Property that every descending chain of divisibilities `a (suc i) | a i` eventually stabilizes (`LDiv (a j) (a (suc j))`).
