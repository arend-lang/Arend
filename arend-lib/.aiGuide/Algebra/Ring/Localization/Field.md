### Algebra.Ring.Localization.Field

Conditions under which a localization of a commutative ring is a local ring, field, discrete field, or ordered field.

This module bridges localization theory with the field hierarchy: starting from a `CRing` `R` and a submonoid `S`, it identifies progressively stronger hypotheses on `S` and `R` that promote `LocRing S` from a generic localization up to a field, a discrete field, and an ordered field. The key technical inputs are an invertibility criterion (`localization-inv`) and a vanishing criterion (`localization-zro`), which together let property checks on `LocRing S` be reduced to existence/decision statements about elements `r : R` with `S (x * r)`. The ordered case is built by localizing at the submonoid of positive elements, with the lattice structure on `LocRing S` defined by reducing fractions to a common positive denominator.

#### Invertibility and Vanishing in Localizations

- **`localization-inv`**: Given `a : SType S` and `r : R` with `S (a.1 * r)`, produces an explicit inverse of `inl~ a` in `LocRing S`. The inverse is `(a.2 * r) / (a.1 * r)`.
- **`localization-inv.converse`**: If `inl~ a` is invertible, then there merely exists `r : R` with `S (a.1 * r)` — the converse extraction of a witness from invertibility.
- **`localization-zro`**: If `a.1 * s = 0` for some `s : R` with `S s`, then `inl~ a = 0` in `LocRing S` — the standard characterization of zero in a localization.
- **`localization-nonTrivial`**: If `S` does not contain `0`, then `0 /= 1` in `LocRing S`.

#### Local and Field Structures on Localizations

- **`localization-isLocal`**: `LocRing S` is a `LocalCRing` provided `0 ∉ S` and the locality condition: for every `x, y : R` with `S y`, either `x` or `x + y` becomes a unit after multiplying by some `r` with `S (· * r) ∈ S`.
- **`localization-isField`**: `LocRing S` is a `Field` under the locality hypothesis plus a "field" hypothesis `fp`: any `x` whose multiples never enter `S` must annihilate some `s ∈ S`. This expresses constructively that non-units are zero divisors against `S`.
- **`localization-isDiscreteField`**: `LocRing S` is a `DiscreteField` when, for each `x : R`, one can decide between "`x` is annihilated by some `s ∈ S`" and "some multiple `x * r` lies in `S`". Provides `zro/=ide` and `eitherZeroOrInv` directly.

#### Ordered Field Structure via Positive Elements

- **`localization-isOrderedField`**: For an `OrderedCRing R`, builds an `OrderedField` by localizing at the submonoid of strictly positive elements. Defines positivity, the lattice operations `meet`/`join` (componentwise on the numerator after rescaling to common denominator), and supplies all `OrderedField` axioms via the localization criterion.
- **`localization-isOrderedField.positiveSubset`**: The submonoid `{x : R | isPos x}` of an `OrderedCRing`; positivity is preserved by `ide` and `*`.
- **`localization-isOrderedField.isPositive`**: The well-defined positivity predicate on `Type {R} {positiveSubset R}`, descended from `isPos` on numerators using positivity-cancellation across the equivalence relation.

#### Discrete Ordered Fields

- **`localization-isDiscreteOrderedField`**: For an `OrderedCRing.Dec` (decidable order), the localization at positive elements is a `DiscreteOrderedField`. Decidability of the field hypothesis is supplied by trichotomy `+_trichotomy` on `R`. Combines `localization-isDiscreteField` and `localization-isOrderedField` into a single discrete ordered field structure.
