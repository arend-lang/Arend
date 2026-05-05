### Combinatorics.Factorial

The factorial function on natural numbers and its basic positivity property.

#### Definitions

- **`fac`**: Factorial function `Nat -> Nat`, defined by `fac 0 = 1` and `fac (suc n) = (suc n) * fac n`.

#### Lemmas

- **`fac>0`**: The factorial is always strictly positive: `0 < fac n` for any `n : Nat`.
