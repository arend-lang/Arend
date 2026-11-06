### Logic.Choice

Axioms of countable and dependent choice over the natural numbers.

The module postulates two choice principles for families of sets indexed by `Nat`: dependent choice (`adchoice`) and countable choice (`acchoice`). Countable choice is strictly weaker than dependent choice, which is strictly weaker than full choice. Each axiom has a lemma recording that it follows from a stronger principle: `adchoice.fromChoice` from full choice `achoice`, and `acchoice.fromDepChoice` from `adchoice`. The conclusions are propositionally truncated.

#### Choice axioms

- **`adchoice`**: Dependent choice: given sets `A i`, a relation `R` between `A i` and `A (suc i)`, a starting point `a0 : A 0` and the totality condition that every `a : A i` merely has an `R`-related `b : A (suc i)`, there merely exists a sequence `s` with `R (s i) (s (suc i))` for all `i`.
- **`adchoice.fromChoice`**: Dependent choice follows from full choice.
- **`acchoice`**: Countable choice `AC_N`: if each set `A i` is merely inhabited, then the product `\Pi (i : Nat) -> A i` is merely inhabited.
- **`acchoice.fromDepChoice`**: Countable choice follows from dependent choice.
