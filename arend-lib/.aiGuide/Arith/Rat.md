### Arith.Rat

This module defines the rational numbers (`Rat`) and provides a `DiscreteOrderedField` instance, floor/ceiling/rounding functions, and power-bound lemmas.

#### Rat Data Type

- **`Rat`**: Data type with constructor `rat (nom : Int) (denom : Nat) (denom/=0) (reduced)`, where `reduced : gcd (iabs nom) denom = 1`.
  - **`fromInt`**: Coercion from `Int` to `Rat`.
  - **`AltRat`**: Alternative representation as a localization ring (`LocRing subset`).
    - **`subset`**: The positive subset of `IntRing` used for localization.
  - **`eta`**: `x = rat (ratNom x) (ratDenom x) ...`.
  - **`ext`**: Extensionality: equal numerator and denominator imply equal rationals.

#### Accessors

- **`ratNom`**: Extracts the numerator (`Int`) from a `Rat`.
- **`ratDenom`**: Extracts the denominator (`Nat`) from a `Rat`.
- **`ratDenom/=0`**: Proof that `ratDenom x /= 0`.
- **`ratReduced`**: Proof that `gcd (iabs (ratNom x)) (ratDenom x) = 1`.

#### Construction

- **`makeRat`**: Constructs a `Rat` from a numerator and denominator by reducing to lowest terms.
  - **`makeRat'`**: Helper that performs the actual reduction.
  - **`simp`**: `makeRat nom denom denom/=0 = makeRat' nom denom denom/=0` (for `denom /= 1`).
  - **`reduce1/=0`**: The reduced numerator is nonzero when the original is nonzero.
  - **`reduce2/=0`**: The reduced denominator is nonzero.
  - **`div0`**: `LDiv 0 x` implies `x = 0`.
  - **`gcd_reduced`**: The reduced form has `gcd = 1`.
  - **`signum_nom`**: `signum` of the reduced numerator equals `signum` of the original.
  - **`eta`**: `makeRat (ratNom x) (ratDenom x) ratDenom/=0 = x`.
- **`ratio`**: Convenience constructor `ratio nom denom`; returns `0` when `denom = 0`.

#### Embedding into Localization Ring

- **`rat_alt`**: Embeds `Rat` into `AltRat` (the localization ring).
- **`rat_alt-inj`**: `rat_alt` is injective.
- **`rat_alt_makeRat`**: `rat_alt (makeRat nom denom denom/=0) = in~ (nom, denom, p)`.
  - **`aux`**: `ratNom (makeRat a b b/=0) * b = a * ratDenom (makeRat a b b/=0)`.
  - **`aux2`**: `(reduce a b).1 * b = a * (reduce a b).2`.
  - **`alt`**: Direct form of `rat_alt_makeRat`.

#### RatField Instance

- **`RatField`**: Instance of `DiscreteOrderedField` for `Rat`, providing `+`, `*`, `negative`, `finv`, `<`, `<=`, `meet`, `join`, decidable equality, and `natCoef`.
  - **`productNonZero`**: Product of nonzero naturals is nonzero.
  - **`+'`**: Explicit addition formula via cross-multiplication.
  - **`+=+'`**: `x + y = x +' y`.
  - **`+_alt`**: `rat_alt (x + y) = rat_alt x + rat_alt y`.
  - **`*_alt`**: `rat_alt (x * y) = rat_alt x * rat_alt y`.
  - **`negative_alt`**: `rat_alt (negative x) = negative (rat_alt x)`.
  - **`ratNom=0`**: `ratNom x = 0` implies `x = 0`.
  - **`<-char`**: `ratNom q * ratDenom r < ratNom r * ratDenom q` implies `q < r`.
  - **`<-char-conv`**: Converse of `<-char`.
  - **`<-char-conv-aux`**: Helper for `<-char-conv`.
  - **`<=-char`**: `ratNom q * ratDenom r <= ratNom r * ratDenom q` implies `q <= r`.
  - **`<=-char-conv`**: Converse of `<=-char`.
  - **`suc-inv`**: `suc n` is invertible in `RatField`.
  - **`mid`**: Midpoint: `mid a b = (a + b) * ratio 1 2`.
  - **`mid>left`**: `a < mid a b` when `a < b`.
  - **`mid<right`**: `mid a b < b` when `a < b`.
  - **`mid-between`**: `a < mid a b` and `mid a b < b` when `a < b`.
  - **`half`**: `half a = a * ratio 1 2`.
  - **`half>0`**: `half a > 0` when `a > 0`.
  - **`half<id`**: `half a < a` when `a > 0`.

#### Int–Rat Coercion Lemmas

- **`fromInt_<=`**: `x <= y` (as `Int`) implies `x <= y` (as `Rat`); with **`conv`** for the converse.
- **`fromInt_<`**: `x < y` (as `Int`) implies `x < y` (as `Rat`); with **`conv`** for the converse.
- **`fromNat_>=0`**: `0 <= n` in `Rat` for any `Nat` `n`.
- **`<=_ratNom`**: `0 <= ratNom x` implies `0 <= x`; with **`conv`** for the converse.
- **`intRat/=0`**: `x /= 0` (as `Int`) implies `fromInt x /= 0` (as `Rat`).
- **`natRat/=0`**: `n /= 0` (as `Nat`) implies `fromInt n /= 0` (as `Rat`).

#### Rational–Denominator Interaction

- **`rat*denom-right`**: `q * ratDenom q = ratNom q`.
- **`rat*denom-left`**: `ratDenom q * q = ratNom q`.
- **`rat-split`**: `q = ratNom q * finv (ratDenom q)`.

#### Floor, Ceiling, and Rounding

- **`rat_floor`**: Floor function `Rat -> Int`.
- **`rat_floor<=id`**: `fromInt (rat_floor r) <= r`.
- **`rat_floor-univ`**: `fromInt x <= r` implies `x <= rat_floor r`.
- **`rat_floor>id-1`**: `r - 1 < rat_floor r`.
- **`rat_ceiling`**: Ceiling function: `rat_ceiling r = negative (rat_floor (negative r))`.
- **`rat_ceiling>=id`**: `r <= rat_ceiling r`.
- **`rat_ceiling-univ`**: `r <= fromInt x` implies `rat_ceiling r <= x`.
- **`rat_round`**: Rounding to nearest integer (rounds up on tie).
- **`rat_round-dist`**: `|fromInt (rat_round r) - r| <= 1/2`.

#### Decidable Ordering

- **`rat_<=-dec`**: Decidable `<=` via boolean check.
  - **`rat_<=_Bool`**: Boolean `<=` test using cross-multiplication.
- **`rat_<-dec`**: Decidable `<` via boolean check.
  - **`rat_<_Bool`**: Boolean `<` test using cross-multiplication.

#### Bounds and Powers

- **`rat_natBound`**: Every rational is bounded above by some natural number.
- **`rat_>1_pow-bound`**: For `q > 1` and any bound `B`, there exists `n` with `B < q^n`.
  - **`aux`**: Bernoulli-like inequality: `1 + n * eps <= (1 + eps)^n` for `eps >= 0`.
- **`rat_<1_pow-bound`**: For `q < 1` and `eps > 0`, there exists `n` with `q^n < eps`.

#### Dense Order Instance

- **`RatDenseOrder`**: Instance of `UnboundedDenseLinearOrder.Dec` for `Rat`.
