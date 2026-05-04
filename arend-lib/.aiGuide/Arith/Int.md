### Arith.Int

This module provides arithmetic operations, ordering, and properties for integers (`Int`).

#### Successor and Predecessor

- **`isuc`**: Integer successor; `isuc (pos n) = pos (suc n)`, `isuc (neg (suc n)) = neg n`.
- **`ipred`**: Integer predecessor; `ipred (pos 0) = neg 1`, `ipred (pos (suc n)) = pos n`, `ipred (neg n) = neg (suc n)`.
- **`ipred_isuc`**: `ipred (isuc x) = x`.
- **`isuc_ipred`**: `isuc (ipred x) = x`.

#### Signum

- **`signum`**: Returns `0`, `1`, or `-1` depending on the sign of the integer.
  - **`*-comm`**: `signum (x * y) = signum x * signum y`.
  - **`signum_pos`**: `signum n = 1` when `n ≠ 0` (for `Nat`).
  - **`signum_neg`**: `signum (neg n) = -1` when `n ≠ 0`.
  - **`signum_neg/=1`**: `signum (neg n) = 1` is impossible.
  - **`signum_-`**: `signum (x - y) = negative (signum (y - x))`.

#### IntRing Instance

- **`IntRing`**: Instance of `OrderedCRing.Dec` for `Int`, providing `+`, `*`, `negative`, `zro`, `ide`, commutativity, associativity, distributivity, positivity predicate (`isPos` via `signum`), and decidable trichotomy.
  - **`lldistr`**: `(n + m) - k = pos n + (m - k)`.
  - **`lrdistr`**: `(n + m) - k = (n - k) + pos m`.
  - **`rldistr`**: `n - (m + k) = neg m + (n - k)`.
  - **`rrdistr`**: `n - (m + k) = (n - m) + neg k`.
  - **`minus+pos`**: `(n - m) + pos k = pos n + (k - m)`.
  - **`minus+neg`**: `(n - m) + neg k = neg m + (n - k)`.
  - **`minus__`**: `n - n = 0`.
  - **`suc-left`**: `pos (suc n) + x = isuc (pos n + x)`.
  - **`suc-right`**: `x + pos (suc n) = isuc (x + pos n)`.
  - **`pred-left`**: `neg (suc n) + x = ipred (neg n + x)`.
  - **`pred-right`**: `x + neg (suc n) = ipred (x + neg n)`.
  - **`neg*pos`**: `neg n * pos m = neg (n * m)`.
  - **`neg*neg`**: `neg n * neg m = pos (n * m)`.
  - **`pos_neg_+`**: `pos (n + k) + neg (m + k) = pos n + neg m`.
  - **`pos_minus-ldistr`**: `pos n * (m - k) = pos (n * m) + neg (n * k)`.
  - **`neg_minus-ldistr`**: `neg n * (m - k) = neg (n * m) + pos (n * k)`.
  - **`neg<=0`**: `neg n <= 0`.
  - **`pos>=0`**: `0 <= pos n`.

#### Absolute Value (`iabs`)

- **`iabs`**: Returns the natural number absolute value of an integer.
  - **`signum_/=0`**: `iabs (signum x) = 1` when `x ≠ 0`.
  - **`signum_*`**: `pos (iabs x) = x * signum x`.
  - **`*_signum`**: `pos (iabs x) * signum x = x`.
  - **`negative-comm`**: `iabs (negative x) = iabs x`.
  - **`equals0`**: `iabs x = 0` implies `x = 0`.
  - **`ofPos`**: `pos (iabs x) = x` when `0 <= x`.
  - **`ofNeg`**: `pos (iabs x) = negative x` when `x <= 0`.
- **`iabs=abs`**: `pos (iabs x) = IntRing.abs x`.
- **`iabs_*`**: `iabs (x * y) = iabs x * iabs y`.

#### Ordering Lemmas

- **`<_+1_<=`**: `x < y + 1` implies `x <= y`.
- **`id<isuc`**: `x < isuc x`.
- **`pos<pos`**: `n < m` (as `Nat`) implies `pos n < pos m`; with `conv` for the converse.
- **`neg<neg`**: `n < m` (as `Nat`) implies `neg m < neg n`; with `conv` for the converse.
- **`pos<=pos`**: `n <= m` implies `pos n <= pos m`; with `conv` for the converse.
- **`neg<=pos`**: `neg n <= pos m`.
- **`pos/<=neg`**: `pos n <= neg m` implies `m = 0`.
- **`neg<=neg`**: `n <= m` implies `neg m <= neg n`; with `conv` for the converse.

#### Divisibility

- **`ldiv_iabs`**: `LDiv x y` implies `LDiv (iabs x) (iabs y) (iabs d.inv)`.
- **`iabs_ldiv`**: `LDiv (iabs x) (iabs y)` implies `LDiv x y`.

#### Nat–Int Interaction

- **`pos_iabs`**: `pos (iabs (x - y)) = x - y` when `y <= x`.
- **`neg_iabs`**: `neg (iabs (x - y)) = x - y` when `x <= y`.
- **`iabs_-_suc`**: `iabs (suc n - i) = suc (iabs (n - i))` when `i <= n`.
- **`zro-id=neg`**: `0 - n = neg n`.
- **`-'=-`**: `pos (n -' m) = n - m` when `m <= n`.
- **`unpos`**: `pos n = pos m` implies `n = m`.

#### Sign and Absolute Value Identity

- **`signum_iabs_eq`**: If `signum x = signum y` and `iabs x = iabs y`, then `x = y`.

#### Units

- **`intUnits`**: `x * y = 1` implies `y = 1` or `y = -1`.

#### Decidable Ordering

- **`int_<=-dec`**: Decidable `<=` for integers via boolean check `int_<=_Bool`.
  - **`int_<=_Bool`**: Boolean comparison function for integers.
- **`int_<-dec`**: Decidable `<` for integers via `int_<=_Bool (isuc x) y`.
