### Set.Fin

Finite sets as types equipped with a cardinality and a merely-existing equivalence with `Fin n`, together with constructions of common finite sets and search/decidability lemmas.

#### Core Class

- **`FinSet`**: Class of finite sets, extending `KFinSet`, `Choice`, and `DecSet`. Carries `finCard : Nat` and `finEq : TruncP (Equiv {Fin finCard} {E})`. Derives surjectivity, choice, decidable equality, and an apartness relation (`#`) given by `/=`.
- **`FinSet.levelProp`**: `FinSet` structure on a `\Set` is a proposition — any two `FinSet` instances on the same set are equal (uses `FinCardBij`).
- **`finSet`**: Identity coercion `{A : FinSet} => A`, used to introduce a `FinSet` instance into scope.

#### Choice and Search Principles

- **`||-finiteAC`**: Finite version of choice for `||`: from `\Pi (i : Fin n) -> A || B i` extract `A || (\Pi i -> B i)`.
- **`finiteAC`**: Finite axiom of choice: a family of inhabited propositions over `Fin n` has an inhabited product.
- **`searchFin`**: Decidable search over `Fin n`: returns either a least witness with proof of minimality, or a proof that `A` holds nowhere.
- **`searchFin-equiv`**: Decidable search transported across an equivalence `Fin n ≃ B`.
- **`searchFin-unique`**: The least witness produced by `searchFin` is unique (`Contr`).

#### Cardinality Lemmas

- **`FinCardBij`**: `Fin n ≃ Fin m` implies `n = m`.
- **`FinCardInj`**: An injection `Fin n -> Fin m` implies `n <= m`.
- **`finCard_Equiv`**: An equivalence between two `FinSet`s yields equal cardinalities.
- **`finCard_inj`**: An injection between two `FinSet`s yields `A.finCard <= B.finCard`.

#### Operations on `Fin`

- **`pred`**: Predecessor `Fin (suc (suc n)) -> Fin (suc n)`, sending `0` to `0`.
- **`suc-isInj`**: `suc` is injective on `Fin n`.
- **`skip`**: Removes `x0` from `Fin (suc n)`: given `x0 /= x`, returns the corresponding element of `Fin n`.
- **`skip-isInj`**: `skip x0` is injective in its second argument.
- **`skip_<` / **`<_skip`**: `skip x0` is order-preserving and order-reflecting.
- **`skip-left`**, **`skip-right`**: Numerical characterization of `skip` for `x < x0` (yields `x`) and `x0 < x` (yields `pred' x`).
- **`sface`**: Face map `Fin (suc n)` from `Fin n` skipping a given index `k` (the simplicial face inclusion).
- **`sface-skip`**: `sface k i /= k` — the face map avoids its index.
- **`sface-inj`**: `sface k` is injective.
- **`sface_skip`**, **`skip_sface`**: `sface` and `skip` are mutually inverse on the appropriate domains.

#### Inclusions Between `Fin` Types

- **`fin-inc_<=`**: Inclusion `Fin n -> Fin m` from a proof `n <= m`.
- **`fin-inc`**: Includes `Fin n` into `Fin (n + m)` on the left.
- **`fin-inc.char`**, **`fin-inc.char_nat`**: `fin-inc` preserves the underlying natural number.
- **`fin-inc-right`**: Includes `Fin m` into `Fin (n + m)` on the right (via `fin-inc_<=`).
- **`fin-inc-right.char_nat`**: Numerical characterization of `fin-inc-right`.
- **`fin-raise`**: Shifts `Fin n` into `Fin (k + n)` by adding `k` to the index.

#### Transport Lemmas for `Fin`

- **`transport_zero`**: Transport of `0 : Fin (suc n)` along `pmap suc p` is `0`.
- **`transport_suc`**: Transport commutes with `suc` along `pmap suc p`.
- **`fin_transport`**: Transport along `n = m` preserves the underlying natural number.

#### Concrete `FinSet` Instances

- **`EmptyFin`**: `Empty` is finite with cardinality `0`.
- **`UnitFin`**: `\Sigma` (the unit type) is finite with cardinality `1`.
- **`BoolFin`**: `Bool` is finite with cardinality `2`; explicit equivalence `Fin 2 ≃ Bool` provided in `BoolFin.equiv`.
- **`FinFin`**: `Fin n` is itself a `FinSet` with cardinality `n`.
- **`DecFin`**: A decidable proposition `P` is a `FinSet` with cardinality `1` or `0` according to its decidability.
- **`OrFin`**: The disjoint union `Or S T` of two `FinSet`s is finite with cardinality `S.finCard + T.finCard`. Helper `OrFin.aux` provides the explicit equivalence `Fin (n + m) ≃ Or (Fin n) (Fin m)`, with naturality lemmas `ret_inl-lem` and `ret_inr-lem`.
- **`fromArray`**: Constructs a `FinSet A l.len` from an array `l : Array A` together with surjectivity and injectivity hypotheses.

#### Decidability of Finite Products

- **`FinDec`**: For a `FinSet A` and decidable family `B : A -> Decide`, the dependent product `\Pi (a : A) -> B a` is decidable.
- **`FinDec.fin-dec`**: The underlying `Fin n` version: `Dec (\Pi (j : Fin n) -> B j)` from pointwise decidability.
