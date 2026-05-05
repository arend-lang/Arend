### Arith.Real.Extremes

This module provides supremum and infimum constructions for sets of real numbers.

#### Supremum

- **`IsSup`**: Proposition that `b` is the supremum of `A`: `b` is an upper bound and for every `eps > 0` there exists `x ∈ A` with `b < x + eps`.
  - **`isLowest`**: `IsSup A b` and `c` is an upper bound imply `b <= c`.
  - **`isUnique`**: Suprema are unique.
- **`HasSup`**: Truncated pair `(B, IsSup A B)`, with `levelProp` proving it is a proposition.
- **`makeSup`**: Constructs a `Real` that is the supremum of `A`, given an element `a0 ∈ A`, an upper bound `b`, and a locatedness condition `As`.
  - **`isSup`**: Proof that `makeSup` satisfies `IsSup`.
  - **`conv`**: Derives the locatedness condition from `IsSup`.
- **`makeSup-pair`**: Constructs `HasSup A` from the same data as `makeSup`.
- **`makeSupTB`**: Constructs `HasSup A` from totally bounded approximation data (finite `eps`-nets).

#### Infimum

- **`IsInf`**: Proposition that `b` is the infimum of `A`: `b` is a lower bound and for every `eps > 0` there exists `x ∈ A` with `x < b + eps`.
  - **`isGreatest`**: `IsInf A b` and `c` is a lower bound imply `c <= b`.
  - **`isUnique`**: Infima are unique.
- **`HasInf`**: Truncated pair `(B, IsInf A B)`, with `levelProp`.
- **`makeSupInf`**: Derives `IsInf A b` from `IsSup (λ x => A (negative x)) (negative b)`.
- **`makeHasSupInf`**: Derives `HasInf A` from `HasSup (λ x => A (negative x))`.
- **`makeInf`**: Constructs a `Real` that is the infimum of `A`, given an element `a0 ∈ A`, a lower bound `b`, and a locatedness condition `As`.
  - **`isInf`**: Proof that `makeInf` satisfies `IsInf`.
  - **`conv`**: Derives the locatedness condition from `IsInf`.
- **`makeInfTB`**: Constructs `HasInf A` from totally bounded approximation data.
