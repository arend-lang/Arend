### Arith.Int

Integer arithmetic, ordering, and basic number-theoretic operations on the `Int` type.

This module equips `Int` (the inductive type with `pos n` and `neg n` constructors) with the structure of a decidable ordered commutative ring (`IntRing`), implemented by case-splitting addition and multiplication on the sign of operands. The module also defines successor/predecessor, sign, and absolute-value functions, and supplies the lemmas needed to translate between integer-level statements and their natural-number counterparts (via `pos`/`neg` injections and `iabs`). Many auxiliary distributivity lemmas inside `IntRing`'s `\where`-block exist to make the case-split definitions usable in proofs without unfolding them everywhere.

#### Successor and Predecessor

- **`isuc`**: Integer successor: `pos n ↦ pos (suc n)`, `neg (suc n) ↦ neg n`.
- **`ipred`**: Integer predecessor: `pos 0 ↦ neg 1`, `pos (suc n) ↦ pos n`, `neg n ↦ neg (suc n)`.
- **`ipred_isuc`**: `ipred (isuc x) = x`.
- **`isuc_ipred`**: `isuc (ipred x) = x`.
- **`id<isuc`**: `x < isuc x`.

#### Sign Function

- **`signum`**: Sign of an integer, returning `0`, `1`, or `-1` as an `Int`.
- **`signum.*-comm`**: `signum (x * y) = signum x * signum y`.
- **`signum.signum_pos`**, **`signum.signum_neg`**: Sign of a nonzero positive/negative natural.
- **`signum.signum_neg/=1`**: `signum (neg n) ≠ 1`.
- **`signum.signum_-`**: `signum (x - y) = -(signum (y - x))`.

#### Ring Structure

- **`IntRing`**: Instance of `OrderedCRing.Dec` on `Int`. Defines `+`, `*`, `negative`, `0`, `1` by case-splitting on signs of operands, with positivity given by `signum x = 1`. Includes the `natCoef` embedding `pos : Nat → Int`.

#### Ring-Level Helper Lemmas (inside `IntRing`)

- **`lldistr`**, **`lrdistr`**, **`rldistr`**, **`rrdistr`**: Distribute `pos`/`neg` summands across `Nat` subtraction `n - m : Int`.
- **`minus+pos`**, **`minus+neg`**: Rearrange `(n - m) + pos k` and `(n - m) + neg k`.
- **`minus__`**: `n - n = 0`.
- **`suc-left`**, **`suc-right`**: Pull `pos (suc n)` out of an integer sum as `isuc`.
- **`pred-left`**, **`pred-right`**: Pull `neg (suc n)` out of an integer sum as `ipred`.
- **`neg*pos`**, **`neg*neg`**: Sign rules for products of `neg`/`pos` naturals.
- **`pos_neg_+`**: Cancel a common `k` from `pos (n+k) + neg (m+k)`.
- **`pos_minus-ldistr`**, **`neg_minus-ldistr`**: Distribute `pos n *` / `neg n *` over `Nat`-subtraction.
- **`neg<=0`**, **`pos>=0`**: Sign-based ordering bounds.

#### Absolute Value

- **`iabs`**: Absolute value as a `Nat`: `pos n ↦ n`, `neg n ↦ n`.
- **`iabs.signum_/=0`**: `iabs (signum x) = 1` for nonzero `x`.
- **`iabs.signum_*`**, **`iabs.*_signum`**: Recover `x` (or `pos (iabs x)`) from `iabs` and `signum`.
- **`iabs.negative-comm`**: `iabs (-x) = iabs x`.
- **`iabs.equals0`**: `iabs x = 0 ⇒ x = 0`.
- **`iabs.ofPos`**, **`iabs.ofNeg`**: `pos (iabs x) = x` (or `-x`) when `x ≥ 0` (or `x ≤ 0`).
- **`iabs=abs`**: Agreement of `iabs` with the ring-theoretic `abs`.
- **`iabs_*`**: `iabs (x * y) = iabs x * iabs y`.

#### Divisibility and Units

- **`ldiv_iabs`**: A divisibility witness `LDiv x y` transfers to `LDiv (iabs x) (iabs y)`.
- **`iabs_ldiv`**: Conversely, an `iabs`-level divisibility lifts to `LDiv x y` by case analysis on signs.
- **`intUnits`**: If `x * y = 1` then `y = 1` or `y = -1`.

#### Order and Sign Reflection

- **`<_+1_<=`**: `x < y + 1 ⇒ x ≤ y`.
- **`pos_iabs`**, **`neg_iabs`**: Express `pos`/`neg` of `iabs (x -ℕ y) : Int` directly as the integer subtraction.
- **`iabs_-_suc`**: `iabs (suc n -ℕ i) = suc (iabs (n -ℕ i))` when `i ≤ n`.
- **`zro-id=neg`**: `0 -ℕ n = neg n` as integers.
- **`-'=-`**: Truncated `Nat` subtraction `-'` agrees with integer subtraction when no underflow.
- **`unpos`**: Injectivity of `pos`.
- **`pos<pos`**, **`pos<pos.conv`**: Strict order on `Nat` corresponds to strict order on `pos n`.
- **`neg<neg`**, **`neg<neg.conv`**: Order-reversing correspondence on negatives.
- **`pos<=pos`**, **`pos<=pos.conv`**: Non-strict version.
- **`neg<=pos`**: Every `neg n` is `≤` every `pos m`.
- **`pos/<=neg`**: `pos n ≤ neg m` forces `m = 0`.
- **`neg<=neg`**, **`neg<=neg.conv`**: Order-reversing non-strict version.
- **`signum_iabs_eq`**: Two integers are equal iff their signs and absolute values agree.

#### Decidable Order via Booleans

- **`int_<=-dec`**: Reflects the boolean `int_<=_Bool x y` into `x <= y` via a `So` proof.
- **`int_<=-dec.int_<=_Bool`**: Boolean comparison on `Int` by sign/case analysis.
- **`int_<-dec`**: Strict variant: `So (int_<=_Bool (isuc x) y)` reflects to `x < y`.
