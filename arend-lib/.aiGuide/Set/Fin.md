### Set.Fin

Finite sets presented as types equipped with a bijection to a standard `Fin n`.

This module defines the `FinSet` class, which augments `KFinSet` (Kuratowski-finite) with a chosen cardinality `card` and a propositionally-truncated equivalence to `Fin card`. Because the cardinality is fixed and the equivalence merely exists, `FinSet` automatically yields decidable equality and apartness via transport from the standard finite ordinals, and also provides choice and surjection-lifting lemmas. The module also provides search/decision procedures lifted from `Fin n`, face/skip combinatorics for working with finite ordinals, and concrete `FinSet` instances (`Empty`, unit, `Bool`, `Fin n`, decidable propositions, disjoint sums).

#### Main Class

- **`FinSet`**: Class extending `KFinSet` and `DecSet`. Adds `finEq : TruncP (QEquiv (Fin card) E)` and derives `finSurj`, a default `decideEq`, and the apartness structure (`#` defined as `/=`) from this equivalence. The class is a proposition over its underlying `\Set`.
- **`FinSet.choice`**: Finite choice over a `FinSet` `E`: from `\Pi (x : E) -> TruncP (B x)` obtain `TruncP (\Pi (x : E) -> B x)`.
- **`FinSet.liftDepSurj`**: Lifts a section along a fiberwise surjection `f : A x -> B x`: for every `g : \Pi (x : E) -> B x` there merely exists `g'` with `f (g' x) = g x` for all `x`.
- **`FinSet.liftSurj`**: Non-dependent version: for a surjection `f : A -> B` and `g : E -> B` there merely exists `g' : E -> A` with `f (g' x) = g x`.
- **`FinSet.splitSurj`**: Every surjection `A -> E` onto a `FinSet` is split (`IsSplitSurj`).
- **`finSet`**: Coercion `\func finSet {A : FinSet} => A` to recover the underlying type.

#### Choice and Search

- **`||-finiteAC`**: Disjunctive form of finite choice: from `\Pi (i : Fin n) -> A || B i` derive `A || (\Pi i -> B i)`.
- **`finiteAC`**: Finite axiom of choice for `TruncP`-valued families over `Fin n`.
- **`searchFin`**: Bounded search on `Fin n` returning the least witness of a decidable predicate, or a proof of universal negation.
- **`searchFin-equiv`**: Lifts `searchFin` along a `QEquiv` `Fin n ≃ B`, giving a witness in `B` or a proof of universal negation.
- **`searchFin-unique`**: The least-witness triple is contractible when a witness exists.

#### Cardinality Lemmas

- **`FinCardBij`**: A `QEquiv` `Fin n ≃ Fin m` forces `n = m`.
- **`FinCardInj`**: Injection `Fin n -> Fin m` forces `n <= m`.
- **`fromArray`**: Builds a `FinSet A l.len` from an array `l` whose entries are surjective and injective onto `A`.
- **`card_Equiv`**: Finite sets in `QEquiv` have equal cardinality: `A.card = B.card`.
- **`card_inj`**: An injection `f : A -> B` between `FinSet`s gives `A.card <= B.card`.
- **`FinSet-transport`**: Transports the `FinSet` structure of `A` along a `QEquiv` `A ≃ B` to a `FinSet B A.card` for a set `B`.

#### `Fin n` Combinatorics

- **`pred`**: Predecessor on `Fin (suc (suc n))`, sending `0 ↦ 0`.
- **`suc-isInj`**: Injectivity of `fsuc` on `Fin n`.
- **`skip`**: `skip x0 x d : Fin n` removes the value `x0` from `Fin (suc n)`, given `x0 /= x`.
- **`skip-isInj`**: `skip x0 _ _` is injective in its second argument.
- **`sface`**: `sface k i : Fin (suc n)` — the `k`-th face inclusion `Fin n -> Fin (suc n)` skipping `k`.
- **`sface_skip`**, **`skip_sface`**: `sface` and `skip` are mutually inverse on the appropriate domains.
- **`sface-skip`**: `sface k i /= k`.
- **`sface-inj`**: `sface k` is injective.
- **`skip_<`**, **`<_skip`**: Order-preservation/reflection of `skip`.
- **`skip-left`**, **`skip-right`**: Numerical formula for `skip` below/above the removed point.

#### Inclusions and Raises

- **`fin-inc_<=`**: Inclusion `Fin n -> Fin m` from `n <= m`.
- **`fin-inc`**: Left inclusion `Fin n -> Fin (n + m)` preserving the underlying natural number (with lemma `char`/`char_nat`).
- **`fin-inc-right`**: Right inclusion `Fin m -> Fin (n + m)`.
- **`fin-raise`**: Shift `Fin n -> Fin (k + n)` by adding `k` on the left.

#### Transport on `Fin`

- **`transport_zero`**, **`transport_suc`**: Compute `transport Fin (pmap suc p)` on `0` and `suc x`.
- **`fin_transport`**: Transport along `n = m` preserves the underlying natural number.

#### Instances

- **`EmptyFin`**: `FinSet Empty` with cardinality `0`.
- **`UnitFin`**: `FinSet (\Sigma)` with cardinality `1`.
- **`BoolFin`**: `FinSet Bool` with cardinality `2`; the `QEquiv` `Fin 2 ≃ Bool` is given by `equiv`.
- **`FinFin`**: `FinSet (Fin n)` with cardinality `n`.
- **`DecFin`**: Turns a decidable proposition `P` into a `FinSet P` of cardinality `1` or `0`.
- **`FinDec`**: Pointwise decidability over a `FinSet` lifts to decidability of `\Pi (a : A) -> B a` (with helper `fin-dec` for `Fin n`).
- **`OrFin`**: `FinSet (Or S T)` with cardinality `S.card + T.card`; the underlying `QEquiv` `aux : Fin (n + m) ≃ Or (Fin n) (Fin m)` is supported by `ret_inl-lem` and `ret_inr-lem`.

#### Stabilization and Sequences

- **`three-consecutive-equals`**: Pigeonhole-style stabilization: a non-increasing sequence `k : Fin (N + 2) -> Fin (suc n)` with `N > 2 * n` has three consecutive equal values, i.e. some `j` with `k j = k (suc j) = k (suc (suc j))`.
- **`sucPreserving-isId`**: If `f : Fin (suc n) -> Nat` satisfies `f 0 = 0` and `f (suc x) = suc (f x)`, then `f x = x` for all `x`.
- **`finiteDependentChoice`**: Finite dependent choice: given `a0 : A` and a step `\Pi a -> ∃ (b : A) (R a b)`, for every `n` there merely exists a chain `f : Fin (suc n) -> A` with `f 0 = a0` and `R (f i) (f (suc i))` for all `i`.
