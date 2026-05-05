### Algebra.Ring.Localization.Field

Constructs fields and ordered fields by localizing commutative rings at suitable submonoids, including localizations of local rings, discrete fields, and ordered fields.

#### Basic Localization Lemmas

- **`localization-inv`**: If `a.1 * r ∈ S`, then `inl~ a` is invertible in `LocRing S` with explicit inverse `(a.2 * r, a.1 * r)`.
- **`localization-inv.converse`**: If `inl~ a` is invertible in `LocRing S`, then there merely exists `r : R` with `a.1 * r ∈ S`.
- **`localization-zro`**: If `a.1 * s = zro` for some `s ∈ S`, then `inl~ a = zro` in `LocRing S`.
- **`localization-nonTrivial`**: If `zro ∉ S`, then `0 ≠ 1` in `LocRing S`.

#### Field Structures on Localizations

- **`localization-isLocal`**: Builds a `LocalCRing` instance on `LocRing S` given non-triviality and the locality condition: for any `x, y` with `y ∈ S`, either `x` or `x + y` becomes a unit after multiplication by some element of `S`.
- **`localization-isField`**: Builds a `Field` instance on `LocRing S`, requiring the locality condition plus the field-witness condition `fp`: any `x` whose multiples never land in `S` is annihilated by some element of `S`.
- **`localization-isDiscreteField`**: Builds a `DiscreteField` instance on `LocRing S` given a decidable version of `fp`: every `x : R` is either annihilated by an element of `S` or has a multiple in `S`. Defines `eitherZeroOrInv` by case analysis.

#### Ordered Field on Localization at Positive Elements

- **`localization-isOrderedField`**: Constructs an `OrderedField` on `LocRing (positiveSubset R)` for any `OrderedCRing R`, equipping it with positivity, comparison, lattice operations (`meet`, `join`), and the order axioms (trichotomy comparison, connectedness, positive multiplication, `#0` characterization). Lattice operations are defined componentwise on representatives, with proofs reducing positivity claims back to `R` via `positive_*-cancel`.
- **`localization-isOrderedField.positiveSubset`**: The submonoid of strictly positive elements of an `OrderedCRing`, used as the localization set; closed under multiplication and contains `ide`.
- **`localization-isOrderedField.isPositive`**: Well-defined positivity predicate on `LocRing (positiveSubset R)`: a class `in~ x` is positive iff `x.1` is positive in `R`. Proved invariant under `~-equiv` using cancellation by positive denominators.

#### Discrete Ordered Field

- **`localization-isDiscreteOrderedField`**: For a decidable ordered commutative ring `OrderedCRing.Dec R`, packages `localization-isOrderedField` and `localization-isDiscreteField` together to produce a `DiscreteOrderedField` on `LocRing (positiveSubset R)`, using trichotomy `+_trichotomy` to decide the field-witness condition.
