### Algebra.Ring.Localization.Field

Conditions under which a localization of a commutative ring is a local ring, field, discrete field, or ordered field.

This module bridges localization theory with the field hierarchy: starting from a `CRing` `R` and a submonoid `S`, it identifies progressively stronger hypotheses on `S` and `R` that promote `LocRing S` from a generic localization up to a field, a discrete field, and an ordered field. The key technical inputs are the invertibility and vanishing criteria for fractions from `Algebra.Ring.Localization` and `Algebra.Monoid.Localization`, which let property checks on `LocRing S` be reduced to existence/decision statements about elements `r : R` with `S (x * r)`. The ordered case is built by localizing at the submonoid of positive elements, with positivity of a fraction determined by its numerator.

#### Local and Field Structures on Localizations

- **`localization-isField`**: `LocRing S` is a `Field` under the hypotheses `nt : 0 ∉ S`, `loc` (whenever `x + y ∈ S`, some `r` has `x * r ∈ S` or `y * r ∈ S`, which gives locality) and a "field" hypothesis `fp`: any `x` none of whose multiples enter `S` must annihilate some `s ∈ S`. This expresses constructively that non-units are zero divisors against `S`.
- **`localization-isDiscreteField`**: `LocRing S` is a `DiscreteField` when, for each `x : R`, one can decide between "`x` is annihilated by some `s ∈ S`" and "some multiple `x * r` lies in `S`". Provides `zro/=ide` and `eitherZeroOrInv` directly.

#### Ordered Field Structure via Positive Elements

- **`localization-isOrderedField`**: For an `OrderedCRing R`, builds an `OrderedField` by localizing at the submonoid of strictly positive elements. Uses `localization-isField` for the field structure, defines positivity of a fraction through its numerator (`isPositive`), and checks the remaining `OrderedField` axioms on numerators.
- **`localization-isOrderedField.positiveSubset`**: The submonoid `{x : R | isPos x}` of an `OrderedCRing`; positivity is preserved by `ide` and `*`.
- **`localization-isOrderedField.isPositive`**: The well-defined positivity predicate on `LocType (positiveSubset R)`, descended from `isPos` on numerators using positivity-cancellation across the equivalence relation.

#### Discrete Ordered Fields

- **`localization-isDiscreteOrderedField`**: For an `OrderedCRing.Dec` (decidable order), the localization at positive elements is a `DiscreteOrderedField`. Decidability of the field hypothesis is supplied by trichotomy `+_trichotomy` on `R`. Combines `localization-isDiscreteField` and `localization-isOrderedField` into a single discrete ordered field structure.
