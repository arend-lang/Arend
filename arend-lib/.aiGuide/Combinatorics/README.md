### Combinatorics Directory Overview

Combinatorial functions, counting arguments, and bijective proofs over finite types.

#### Basic Combinatorial Functions

- **`Factorial.md`**: Factorial function (`fac`) with positivity lemma.
- **`Binom.md`**: Binomial coefficients (`binom`) with Pascal's rule, boundary cases, and the binomial theorem for commutative rings.

#### Combinations

- **`Combinations.md`**: Combinations of `k` elements from `n` realized as strictly monotone maps `Fin k → Fin n`. Includes finiteness instances for arrays and combinations, a Pascal splitting equivalence (`Combinations (suc n) (suc k) ≃ Or (Combinations n k) (Combinations n (suc k))`), and the main counting result `Fin (binom n k) ≃ Combinations n k`.

#### Compositions

- **`Compositions.md`**: Compositions and weak compositions of `n` into `k` parts. Provides finiteness instances, partial-sum machinery, and a stars-and-bars bijection `WeakCompositions n (suc k) ≃ Combinations (n + k) k`, yielding `weakCompositions-binom : finCard (WeakCompositions n (suc k)) = binom (n + k) k`.
