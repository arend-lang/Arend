### Arith.Real.Extremes

Suprema and infima of subsets of the real numbers, characterized via the Dedekind-cut presentation of `Real`.

This module defines what it means for a real number to be the supremum or infimum of a set of reals, packages these as proposition-level structures (using uniqueness to obtain `\level`), and provides constructors that build the extremum directly as a Dedekind cut. The supremum's lower cut is the union of the lower cuts of its members, while its upper cut consists of strict upper bounds; the infimum is built dually. Existence is established under either an order-theoretic locatedness hypothesis (`As`) or a totally-bounded hypothesis (`Ac`) requiring finite ε-nets, and inf-from-sup duality is provided by negation.

#### Supremum Predicate

- **`IsSup`**: `IsSup A b` asserts `b` is a least upper bound: every `a ∈ A` satisfies `a <= b`, and for every `eps > 0` some `x ∈ A` exceeds `b - eps` (witnessed as `b < x + eps`).
- **`IsSup.isLowest`**: Any other upper bound `c` of `A` satisfies `b <= c`, so `b` is the least upper bound.
- **`IsSup.isUnique`**: Suprema are unique: if both `b` and `b'` are suprema of `A`, then `b = b'`.
- **`HasSup`**: The Σ-type of pairs `(B : Real, IsSup A B)`; `levelProp` upgrades it to a proposition via uniqueness of suprema.

#### Constructing Suprema

- **`makeSup`**: Builds the supremum of `A` directly as a `Real` (Dedekind cut). Lower cut: rationals below some member; upper cut: rationals strictly above some real upper bound. Requires inhabitation `Aa0`, a strict upper bound `Ab`, and a locatedness condition `As` deciding for any `x < y` whether some `a ∈ A` exceeds `x` or all of `A` lies below `y`.
- **`makeSup.isSup`**: Verifies that `makeSup` indeed satisfies `IsSup A` under its hypotheses.
- **`makeSup.conv`**: Converse: if `IsSup A b` holds, then the locatedness alternative `As` follows for any `x < y`.
- **`makeSup-pair`**: Packages `makeSup` and `makeSup.isSup` into a `HasSup A`.
- **`makeSupTB`**: Existence of `HasSup A` under a totally-bounded hypothesis: for every `eps > 0` there is a finite list `l ⊆ A` such that every element of `A` is within `eps` (in absolute value) of some entry of `l`.

#### Infimum Predicate

- **`IsInf`**: `IsInf A b` asserts `b` is the greatest lower bound: every `a ∈ A` satisfies `b <= a`, and for every `eps > 0` some `x ∈ A` is below `b + eps`.
- **`IsInf.isGreatest`**: Any other lower bound `c` of `A` satisfies `c <= b`.
- **`IsInf.isUnique`**: Infima are unique.
- **`HasInf`**: The Σ-type `(B : Real, IsInf A B)`, made into a proposition by uniqueness.

#### Constructing Infima

- **`makeSupInf`**: Duality lemma: if `negative b` is a supremum of `{x | A (negative x)}`, then `b` is an infimum of `A`.
- **`makeHasSupInf`**: Lifts `makeSupInf` to packaged form: `HasSup` of the negated set yields `HasInf A`.
- **`makeInf`**: Builds the infimum of `A` directly as a Dedekind cut, dual to `makeSup`. Lower cut: rationals strictly below some real lower bound; upper cut: rationals above some member. Hypotheses mirror those of `makeSup`.
- **`makeInf.isInf`**: Verifies `makeInf` satisfies `IsInf A`.
- **`makeInf.conv`**: Converse locatedness derived from `IsInf A b`.
- **`makeInfTB`**: Existence of `HasInf A` under the same totally-bounded hypothesis as `makeSupTB`.
