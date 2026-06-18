### Combinatorics

This directory formalizes elementary combinatorial structures over natural numbers and finite sets: factorials, binomial coefficients, combinations, and (weak) compositions, together with their counting equivalences.

#### Basic Functions

- **`Factorial.md`** — The factorial function on `Nat` with its strict positivity lemma.
- **`Binom.md`** — Binomial coefficients defined by Pascal's recurrence, with boundary lemmas and the binomial theorem `(x + y)^n` in a commutative ring.

#### Combinations

- **`Combinations.md`** — `k`-combinations of `Fin n` as strictly monotone maps `Fin k -> Fin n`, with a `FinSet` instance and the counting equivalence `Fin (binom n k) ≃ Combinations n k` via Pascal splitting.

#### Compositions

- **`Compositions.md`** — Compositions and weak compositions of `n` into `k` parts as arrays with sum and positivity constraints, with `FinSet` instances and the partial-sum bijection to combinations giving the "stars and bars" count `binom (n + k) k`.
