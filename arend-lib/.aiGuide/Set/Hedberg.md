### Set.Hedberg

Hedberg's theorem and related lemmas: types with decidable (or relation-mediated) equality are sets.

#### Set Lemmas

- **`Set-lemma'`**: General set criterion via a reflexive, propositional relation `R : A -> A -> \Type` that implies equality. If `R a a'` is always a proposition, `R` is reflexive, and `R a a' -> a = a'`, then `A` is a set.
- **`Set-lemma`**: Specialization of `Set-lemma'` to a `\Prop`-valued relation `R`, dropping the explicit propositionality hypothesis.

#### Hedberg's Theorem

- **`Hedberg`**: Hedberg's theorem: any type `A` with decidable equality (`\Pi (a a' : A) -> (a = a') `Or` (a /= a')`) is a set.
