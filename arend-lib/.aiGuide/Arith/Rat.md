### Arith.Rat

Construction of the rational numbers as a discrete ordered field with reduced fractions.

A rational is represented by a triple `(nom, denom, denom/=0, reduced)` where `gcd (iabs nom) denom = 1` enforces a canonical reduced form. This makes equality of rationals decidable and definitional, but requires `makeRat` to renormalize after arithmetic by dividing out the gcd. The module proves the canonical form is equivalent (`rat_alt`) to the localization-based field `LocRing` of integers at the positive monoid, transferring algebraic structure across this isomorphism. Order, density, floor/ceiling/round, and decidable comparisons are then derived on top.

#### Core Type and Projections

- **`Rat`**: Inductive type of rationals with constructor `rat (nom : Int) (denom : Nat) (denom/=0) (reduced)`; the `reduced` field forces canonical form.
- **`Rat.fromInt`**: Coercion `Int -> Rat` sending `x` to `rat x 1 _ _`.
- **`Rat.AltRat`**: Alternative presentation of `Rat` as the localization `LocRing` of `Int` at the positive submonoid.
- **`Rat.eta`**, **`Rat.ext`**: Eta law and extensionality (equality determined by numerator and denominator).
- **`ratNom`**, **`ratDenom`**: Projections to numerator and denominator.
- **`ratDenom/=0`**, **`ratReduced`**: The denominator is nonzero and the fraction is reduced.

#### Smart Constructor

- **`makeRat`**: Builds a `Rat` from an arbitrary `(nom, denom)` by reducing via the gcd; specializes to `nom` directly when `denom = 1`.
- **`makeRat.makeRat'`**: Internal worker that case-splits on the sign of the numerator and applies `reduce`.
- **`makeRat.reduce1/=0`**, **`makeRat.reduce2/=0`**: The reduced numerator/denominator stays nonzero when inputs are.
- **`makeRat.gcd_reduced`**: After `reduce`, the resulting numerator and denominator are coprime.
- **`makeRat.signum_nom`**: `makeRat` preserves the sign of the input numerator.
- **`makeRat.eta`**: `makeRat (ratNom x) (ratDenom x) _ = x`.
- **`makeRat.simp`**, **`makeRat.div0`**: Auxiliary simplification lemmas.

#### Bridge to the Localization

- **`rat_alt`**: Maps `Rat` into `AltRat` as `in~ (nom, denom, _)`.
- **`rat_alt-inj`**: `rat_alt` is injective, allowing equalities to be transferred from `AltRat`.
- **`rat_alt_makeRat`**: `rat_alt (makeRat nom denom _) = in~ (nom, denom, _)`, computing `rat_alt` through the smart constructor.
- **`rat_alt_makeRat.aux`**, **`rat_alt_makeRat.aux2`**, **`rat_alt_makeRat.alt`**: Cross-multiplication identities used to bridge `makeRat` and the localization.

#### Field Structure

- **`RatField`**: Instance `DiscreteOrderedField Rat` providing `+`, `*`, `negative`, `finv`, decidable equality, positivity (`isPos = isPos ∘ ratNom`), and lattice operations `meet`, `join` (defined via decidable comparison of cross products).
- **`RatField.productNonZero`**: Product of nonzero naturals is nonzero (used for denominators).
- **`RatField.+'`**, **`RatField.+=+'`**: Uniform addition formula and proof it agrees with `+` on all cases.
- **`RatField.+_alt`**, **`RatField.*_alt`**, **`RatField.negative_alt`**: `rat_alt` is a ring homomorphism into `AltRat`; this is the main tool used to discharge ring axioms.
- **`RatField.ratNom=0`**: A rational with zero numerator equals the canonical zero.
- **`RatField.<-char`**, **`RatField.<-char-conv`**, **`RatField.<=-char`**, **`RatField.<=-char-conv`**: Order on `Rat` is equivalent to cross-multiplication of numerators and denominators.
- **`RatField.<-char-conv-aux`**: Helper relating sign of a difference to the cross-product inequality.
- **`RatField.suc-inv`**: `suc n` is invertible in `RatField`.
- **`RatField.mid`**, **`RatField.mid>left`**, **`RatField.mid<right`**, **`RatField.mid-between`**: Midpoint `(a+b)/2` strictly between `a` and `b`.
- **`RatField.half`**, **`RatField.half>0`**, **`RatField.half<id`**: Halving preserves positivity and is strictly less than the original.

#### Coercions and Sign Lemmas

- **`fromInt_<=`**, **`fromInt_<=.conv`**: `Int`-order embeds faithfully into `Rat`-order (≤).
- **`fromInt_<`**, **`fromInt_<.conv`**: Same for strict order.
- **`fromNat_>=0`**: Every natural is `>= 0` as a rational.
- **`<=_ratNom`**, **`<=_ratNom.conv`**: `0 <= x` iff `0 <= ratNom x`.
- **`intRat/=0`**, **`natRat/=0`**: A nonzero integer/natural maps to a nonzero rational.

#### Numerator/Denominator Identities

- **`rat*denom-right`**: `q * ratDenom q = ratNom q`.
- **`rat*denom-left`**: `ratDenom q * q = ratNom q`.
- **`rat-split`**: `q = ratNom q * finv (ratDenom q)`.
- **`ratio`**: Convenience `Int -> Nat -> Rat`, returning `0` when the denominator is `0`.

#### Density

- **`RatDenseOrder`**: Instance `UnboundedDenseLinearOrder.Dec` for `Rat`, derived from `RatField` via `RatField.suc-inv`.

#### Floor, Ceiling, and Rounding

- **`rat_floor`**: Integer floor of a rational, using `div`/`mod` and a sign correction for negative non-integer values.
- **`rat_floor<=id`**, **`rat_floor>id-1`**, **`rat_floor-univ`**: Universal property of `rat_floor`: it is `<= r`, `> r - 1`, and is the largest integer `<= r`.
- **`rat_ceiling`**: Defined as `- floor (- r)`.
- **`rat_ceiling>=id`**, **`rat_ceiling-univ`**: Universal property of `rat_ceiling`.
- **`rat_round`**: Banker-style rounding using `dec<_<= (r - floor r) (1/2)`.
- **`rat_round-dist`**: `|round r - r| <= 1/2`.

#### Decidable Comparison via Booleans

- **`rat_<=-dec`** and **`rat_<=-dec.rat_<=_Bool`**: Boolean test for `r <= q` via cross-multiplication, with proof that `So` of the boolean implies the inequality.
- **`rat_<-dec`** and **`rat_<-dec.rat_<_Bool`**: Same for strict `<` (uses `isuc` of the cross product).

#### Bounds and Power Bounds

- **`rat_natBound`**: For any rational `q`, produces a natural `n` with `q < n` (via `rat_ceiling (q + 1)`).
- **`rat_>1_pow-bound`**: For `q > 1` and any `B`, finds `n` with `B < q^n`; proved via Bernoulli's inequality `1 + n*eps <= (1 + eps)^n`.
- **`rat_>1_pow-bound.aux`**: Bernoulli inequality on `Rat` for nonnegative `eps`.
- **`rat_<1_pow-bound`**: For `0 <= q < 1` (or `q <= 0`) and any `eps > 0`, finds `n` with `q^n < eps`, deduced by inverting `rat_>1_pow-bound` for the positive case.
