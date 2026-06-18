### Algebra.Domain.IntegrallyClosed

Integrally closed domains: integral domains that are integrally closed in their field of fractions.

An integrally closed domain is an integral domain `R` such that every element of its fraction field which is integral over `R` already lies in `R`. The formalization expresses this via the localization map `locMap` at the submonoid of nonzero elements (which embeds `R` into its field of fractions) and the abstract predicate `isIntegrallyClosed` on ring homomorphisms.

#### Classes

- **`IntegrallyClosedDomain`**: Extends `IntegralDomain` with the property `isIntegrallyClosedDomain`, asserting that the canonical localization map `locMap` from the domain into its field of fractions (built from the submonoid of nonzero elements) is integrally closed — i.e., any element of the fraction field that is a root of a monic polynomial over `R` is in the image of `R`.
