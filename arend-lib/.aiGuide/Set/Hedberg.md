### Set.Hedberg

Hedberg's theorem and related criteria for proving a type is a set (h-set).

This module provides standard tools for upgrading a type to an h-set (UIP — uniqueness of identity proofs). The core idea is that if a type carries a reflexive proposition-valued relation that implies equality, then equality is itself propositional, so the type is a set. Hedberg's classical theorem is the special case where the relation is decidable equality, since `(a = a') ∨ (a ≠ a')` always furnishes such a relation.

#### Set Criteria via Relations

- **`Set-lemma'`**: General set criterion: given a relation `R : A -> A -> \Type` that is pointwise propositional (`c`), reflexive (`p`), and implies equality (`q`), concludes `isSet A`. Useful when the relation is not declared in `\Prop` directly.
- **`Set-lemma`**: Streamlined variant taking a `\Prop`-valued relation `R : A -> A -> \Prop` with reflexivity `p` and an implication `R a a' -> a = a'`, yielding `isSet A`.

#### Hedberg's Theorem

- **`Hedberg`**: Hedberg's theorem: any type `A` with decidable equality (`\Pi (a a' : A) -> (a = a') \`Or\` (a /= a')`) is an h-set. Obtained as a corollary of `Set-lemma` using the canonical stable relation derived from decidability.
