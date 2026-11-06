### Algebra.FinSuppFunc

Functions with finite support from a set into an additively-pointed structure, equipped with pointwise algebraic operations.

A `FinSuppFunc` packages a set homomorphism whose values are zero outside some finite list of inputs (the support is given existentially as an array `s` together with a proof that any input not occurring in `s` maps to `0`). Because the support is recorded as a truncated existential rather than a precise finite set, operations like addition use list concatenation to combine supports, and the zero-outside-support condition is preserved by reasoning about indices via `++.index-left` / `++.index-right`. This module then lifts the pointwise additive structure of the codomain (pointed, monoid, abelian monoid, group, abelian group) onto `FinSuppFunc`, giving a uniform construction of finitely-supported function spaces over any additive algebraic structure.

#### Core Record

- **`FinSuppFunc`**: Extends `SetHom` with codomain restricted to `AddPointed`, plus a field `fSupp` asserting the existence of an array `s : Array Dom` such that for every `i` not equal to any `s j`, `func i = 0`. Models finitely-supported maps without committing to a canonical support set.

#### Decidability

- **`FinSuppFuncDec`**: For a `DecSet` domain `M` and an `AddPointed` codomain `R` with decidable equality, equips `FinSuppFunc M R` with decidable equality.

#### Additive Structure Instances

- **`FinSuppFuncAddPointed`**: `AddPointed` instance — the zero function (with empty support `nil`).
- **`FinSuppFuncAddMonoid`**: `AddMonoid` instance lifting an `AddMonoid` codomain pointwise; addition combines supports via `++` and uses `++.index-left` / `++.index-right` to show the sum vanishes off the concatenated support.
- **`FinSuppFuncAbMonoid`**: `AbMonoid` instance, inheriting commutativity from the codomain.
- **`FinSuppFuncAddGroup`**: `AddGroup` instance; the negation reuses the support of `f` and uses `B.negative_zro` to verify that `negative 0 = 0` off-support.
- **`FinSuppFuncAbGroup`**: `AbGroup` instance combining `FinSuppFuncAddGroup` with pointwise commutativity.
