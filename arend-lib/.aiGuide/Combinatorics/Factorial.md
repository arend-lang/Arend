### Combinatorics.Factorial

The factorial function on natural numbers and its basic positivity property.

This module provides the standard recursive definition of the factorial along with a fundamental lemma stating that factorials are always strictly positive. The factorial is defined directly by pattern matching on natural numbers, using `Nat.*` for the recursive multiplication step. The positivity lemma is essential for using factorials as denominators or as witnesses in combinatorial arguments where non-zero values are required.

#### Definitions

- **`fac`**: The factorial function `Nat -> Nat`, defined recursively: `fac 0 = 1` and `fac (suc n) = suc n * fac n`.

#### Lemmas

- **`fac>0`**: For any `n : Nat`, `0 < fac n`. Establishes that the factorial is always strictly positive, useful whenever non-vanishing of `fac n` is required.
