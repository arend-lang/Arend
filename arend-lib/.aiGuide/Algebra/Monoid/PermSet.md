### Algebra.Monoid.PermSet

The free commutative monoid on a set, defined as arrays quotiented by permutation equivalence (`EPerm`).

#### Core Type and Constructors

- **`PermSet`**: `\Set -> \Set` defined as `Quotient {Array A} EPerm` — multisets of `A` represented as permutation-equivalence classes of arrays.
- **`PermSet.inPS`**: Constructor embedding `Array A` into `PermSet A`.
- **`PermSet.inPS~`**: Variant landing in the raw `Quotient EPerm`.
- **`PermSet.unext`**: From an equality of quotient classes, extract a truncated `EPerm` witness between the underlying arrays.
- **`PermSet.~-psequiv`**: Lifts an `EPerm l l'` to `inPS l = inPS l'`.
- **`permSet-ext`**: Equality in `Quotient EPerm` transfers to equality in `PermSet A`.

#### Instances

- **`PermSetDec`**: `DecSet` instance for `PermSet A` when `A : DecSet`, decided by `EPerm.EPermDec`.
- **`PermSetMonoid`**: `CMonoid` instance on `PermSet A` with unit `in~ nil`, multiplication via array concatenation, and commutativity proved by `EPerm.eperm-++-comm`.

#### Functorial Action

- **`permSet-map`**: `(A -> B) -> PermSet A -> PermSet B`, the functorial action mapping pointwise on representatives.
- **`permSet-map-comp`**: Functoriality: `permSet-map g ∘ permSet-map f = permSet-map (g ∘ f)`.
- **`permSet-hom`**: Packages `permSet-map f` as a `MonoidHom (PermSetMonoid A) (PermSetMonoid B)`.
- **`permSet-map_+`**: `permSet-map f (x * y) = permSet-map f x * permSet-map f y` (multiplicativity of the action).

#### Sum / Universal Property

- **`permSet-sum`**: For a `CMonoid A`, folds a `PermSet A` to `A` via `BigProd`; well-defined by `BigProd_EPerm`.
- **`permSet-sum-natural`**: A monoid homomorphism commutes with `permSet-sum` after applying `permSet-map`.
- **`permSet-sum_+`**: `permSet-sum (x * y) = permSet-sum x * permSet-sum y`.
- **`permSet-univ`**: Universal property — any `f : A -> B` into a `CMonoid B` extends uniquely to `MonoidHom (PermSetMonoid A) B` via `permSet-sum ∘ permSet-map f`.
- **`permSet-univ-natural`**: Naturality of the universal extension along monoid homomorphisms.

#### Decision and Decomposition

- **`permSet-zro-dec`**: Decides whether a `PermSet A` equals the identity (i.e., is empty).
- **`permSet-split`**: Any `inPS l` decomposes as the product of its singleton elements: `BigProd (map (\lam a => inPS (a :: nil)) l) = inPS l`.
- **`permSet-pow`**: `pow (inPS (a :: nil)) n = inPS (replicate n a)` — powers of singletons are repeated copies.
- **`permSet-length`**: `PermSet A -> Nat`, the cardinality (well-defined since `EPerm` preserves length).
