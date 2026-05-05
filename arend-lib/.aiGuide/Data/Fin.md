### Data.Fin

This module provides basic constructors, destructors, and utility lemmas for `Fin n` (the type of natural numbers less than `n`).

#### Constructors

- **`fzero`**: The zero element of `Fin (suc n)`.
- **`fsuc`**: Successor embedding: given `x : Fin n`, produces `suc x : Fin (suc n)`.

#### Destructors

- **`fpred`**: Predecessor with a default: `fpred def 0 = def`, `fpred def (suc x) = x`.
- **`fpredP`**: Predecessor with a proof that the argument is nonzero (avoids the default).
- **`fcase`**: Case analysis on `Fin (suc n)`: returns `a` for `0` and `f j` for `suc j`.

#### Equality and Inequality Lemmas

- **`unfsuc`**: `suc x = suc y` implies `x = y` (injectivity of `fsuc`).
- **`fsuc/=`**: `x /= y` implies `fsuc x /= fsuc y`.
- **`fsuc/=0`**: `fsuc x /= 0` for any `x`.
- **`fsuc/=-conv`**: `fsuc x /= fsuc y` implies `x /= y`.
- **`nat_fin_=`**: Equality as `Nat` implies equality in `Fin n`.
- **`fin_nat_/=`**: Inequality in `Fin n` implies inequality as `Nat`.

#### Predecessor–Successor Round-Trip

- **`fsuc_fpred`**: `fsuc (fpred d x) = x` when `x /= 0`.
- **`fsuc_fpredP`**: `fsuc (fpredP x p) = x` when `x /= 0`.

#### Last Element

- **`finLast`**: The largest element of `Fin (suc n)`, defined recursively: `finLast 0 = 0`, `finLast (suc n) = suc (finLast n)`.
