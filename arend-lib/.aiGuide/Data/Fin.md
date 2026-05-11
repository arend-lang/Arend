### Data.Fin

Basic operations and lemmas for finite types `Fin n`.

This module provides the elementary toolkit for working with `Fin n`, the type of natural numbers strictly less than `n`. It defines the standard constructors `fzero`/`fsuc` along with case analysis (`fcase`), predecessor operations (`fpred`, `fpredP`), and a collection of injectivity and disequality lemmas relating `Fin` to its underlying `Nat` representation. The design treats `Fin (suc n)` as the disjoint union of zero and successor cases, mirroring `Nat`, so most lemmas are simple transport/inversion results that let later modules manipulate finite indices without unfolding to `Nat`.

#### Constructors

- **`fzero`**: The zero element of `Fin (suc n)`, defined as `0`.
- **`fsuc`**: The successor function `Fin n -> Fin (suc n)`, defined as `suc`.

#### Case Analysis and Predecessor

- **`fpred`**: Predecessor with a default value: given `def : Fin n` and `x : Fin (suc n)`, returns `def` if `x = 0` and the underlying value otherwise.
- **`fcase`**: Eliminator for `Fin (suc n)` into a type `A`: takes a value `a : A` for the zero case and a function `f : Fin n -> A` for the successor case.
- **`fpredP`**: Predecessor with a proof of nonzero-ness: given `j : Fin (suc n)` and `j /= 0`, produces `Fin n`, using the proof to discharge the zero case via `absurd`.
- **`finLast`**: The largest element of `Fin (suc n)`, defined recursively as `suc (finLast n)`.

#### Injectivity and Disequality

- **`unfsuc`**: Injectivity of `fsuc`: `suc x = suc y` implies `x = y`.
- **`fsuc/=`**: `fsuc` preserves disequality: `x /= y` implies `fsuc x /= fsuc y`.
- **`fsuc/=0`**: `fsuc x` is never equal to `0`.
- **`fsuc/=-conv`**: Converse of `fsuc/=`: `fsuc x /= fsuc y` implies `x /= y`.

#### Conversion to/from Nat

- **`nat_fin_=`**: Equality in `Nat` lifts to equality in `Fin n`.
- **`fin_nat_/=`**: Disequality in `Fin n` descends to disequality of the underlying naturals.

#### Predecessor/Successor Roundtrips

- **`fsuc_fpred`**: For `x : Fin (suc n)` with `x /= 0` and any default `d`, `fsuc (fpred d x) = x`.
- **`fsuc_fpredP`**: Variant for `fpredP`: `fsuc (fpredP x p) = x` when `p : x /= 0`.
