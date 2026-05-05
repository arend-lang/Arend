### Algebra.FinSuppFunc

Functions with finite support from a set into a pointed additive structure, equipped with pointwise algebraic operations.

#### Core Class

- **`FinSuppFunc`**: Extends `SetHom` with `Cod : AddPointed`. A set-theoretic function `Dom -> Cod` together with a proof `fSupp` that there exists a finite array `s` such that `func i = 0` for every `i` not appearing in `s`. Models finitely supported functions.

#### Decidable Equality

- **`FinSuppFuncDec`**: Instance providing `DecSet (FinSuppFunc M R)` when `M` is a `DecSet` and `R` is an `AddPointed` with decidable equality. Decides `f = g` by searching the (combined) supports for a witness of disagreement, and otherwise uses `exts` together with the support property to conclude pointwise equality.

#### Pointed Structure

- **`FinSuppFuncAddPointed`**: Instance making `FinSuppFunc A B` an `AddPointed` whenever `B` is. The zero is the constantly-`0` function with empty support `nil`.

#### Additive Monoid Structure

- **`FinSuppFuncAddMonoid`**: Instance making `FinSuppFunc A B` an `AddMonoid` when `B : AddMonoid`. Pointwise addition `(f + g) a = f a + g a`; the support of a sum is witnessed by the concatenation `s ++ s'` of the two supports, using `++.++_index-left`/`++.index-right` lemmas to relate indices, and pointwise unit/associativity laws lifted via `exts`.

#### Abelian Monoid Structure

- **`FinSuppFuncAbMonoid`**: Instance making `FinSuppFunc A B` an `AbMonoid` when `B : AbMonoid`, with commutativity inherited pointwise.

#### Additive Group Structure

- **`FinSuppFuncAddGroup`**: Instance making `FinSuppFunc A B` an `AddGroup` when `B : AddGroup`. Negation is pointwise, with support preserved via `TruncP.map` and `B.negative_zro`; inverse laws are pointwise.

#### Abelian Group Structure

- **`FinSuppFuncAbGroup`**: Instance making `FinSuppFunc A B` an `AbGroup` when `B : AbGroup`, combining `FinSuppFuncAddGroup` with the commutativity from `FinSuppFuncAbMonoid`.
