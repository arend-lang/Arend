### Topology.Locale.Real

The locale of real numbers, presented as a frame of rational intervals.

This module constructs the real-line locale `RealLocale` as the locale `SiteLocale (RealPres RatField)` of the preorder site `RealPres` on rational intervals `(p, q)`, using two kinds of basic covers: a "subdivision" cover splitting an interval `(p, s)` into the overlapping pieces `(p, c)` and `(b, s)` whenever `p < b < c < s`, and a "shrinking" cover expressing `(p, q)` as the union of all strictly interior subintervals `(r, s)` with `p < r` and `s < q`. The module proves the resulting locale is locally compact, regular, and has enough (strongly dense) points — including a canonical `ratPoint` for each rational — and builds derived constructions like closed intervals as nuclei and the embedding of the discrete rationals.

#### Interval Order Structures

- **`Interval`**: An interval over a strict poset `Q`: a triple `(a, b, a < b)` of endpoints with `a < b`.
- **`IntervalPoset`**: Poset structure on `Interval Q` for a `BiorderedSet` `Q`, where `x <= y` iff `y.1 <= x.1` and `x.2 <= y.2` (containment of open intervals).
- **`IntervalPoset.<=`**: The underlying inclusion order on intervals.
- **`IntervalBiordered`**: Biordered (strict + non-strict) structure on intervals over a `BiorderedSet`, with `x < y` iff `y.1 < x.1` and `x.2 < y.2` (strict containment).
- **`IntervalBiordered.<`**: The strict "well-inside" order on intervals.

#### Frame Presentation of the Reals

- **`RealPres`**: The preorder site of the real line over a decidable linear order `Q`. Its objects are intervals `Interval Q` ordered by containment, and the basic covers of `a` are (1) two-piece subdivisions of `a` into `(a.1, c)` and `(b, a.2)` for `a.1 < b < c < a.2`, and (2) the family of all interior subintervals `x` with `a.1 < x.1` and `x.2 < a.2`.

#### Pointwise Cover Characterization

- **`RealPres.toPointwiseCover`**: Over a dense decidable linear order, a cover `Cover a U` of an interval `a` by a set `U` of intervals implies that for every `x` with `a.1 < x < a.2`, some `b : U` satisfies `b.1 < x < b.2`.

#### Compactness Properties

- **`RealPres.locallyCompact`**: The presentation `RealPres Q` is locally compact whenever `Q` is a dense decidable linear order.

#### The Real Locale

- **`RealLocale`**: The locale of real numbers, defined as `SiteLocale (RealPres RatField)`.
- **`RealLocale.locallyCompact`**: `RealLocale` is locally compact.
- **`RealLocale.<=<-intervals`**: Strict interval inclusion `x < y` yields a well-inside relation `embed x <=< embed y` on the embedded generators.
- **`RealLocale.regular`**: `RealLocale` is a regular locale.
- **`RealLocale.ratPoint`**: For each rational `x`, the canonical point of `RealLocale` whose membership in `(a, b)` is `a < x < b`. It is the `site-point` of the `SitePoint` `RealLocale.ratPoint.sitePoint x` of `RealPres RatField`.
- **`RealLocale.hasStronglyDensePoints`**: `RealLocale` has strongly dense points — the rational points are enough to detect frame elements.

#### Derived Constructions

- **`lowerHalf`**: The open lower half-line `(-∞, x)` for a rational `x`, formed as the join of all `embed (a, b)` with `b <= x`.
- **`upperHalf`**: The open upper half-line `(x, +∞)` for a rational `x`, dual to `lowerHalf`.
- **`closedInterval`**: The closed interval `[x, y]` realized as the locale `(nucleus x y).locale` — the closed sublocale complementary to `lowerHalf x ∨ upperHalf y`.
- **`closedInterval.nucleus`**: The nucleus `closed (lowerHalf x ∨ upperHalf y)` carving `[x, y]` out of `RealLocale` as the complement of the open exterior `lowerHalf x ∨ upperHalf y`.
- **`halves-cover`**: For rationals `x < y`, the two half-lines cover the line: `top <= lowerHalf y ∨ upperHalf x`.
- **`halves3-cover`**: If `a.1 < x` and `y < a.2`, then `top <= lowerHalf x ∨ upperHalf y ∨ embed a`.
- **`halves-inter`**: If `c.1 <= x` and `y <= c.2`, then `upperHalf x ∧ lowerHalf y <= embed c`.
- **`closedInterval.compact`**: Heine–Borel: every closed interval `[x, y]` is compact.

#### Embedding the Rationals

- **`rat_real`**: The locale homomorphism `LocaleHom (discreteLocale Rat) RealLocale` embedding the discrete rational locale into the reals. Constructed via `LocaleSite.adjointMap` from a `PreorderSiteHom` sending an interval `(p, q)` to the predicate `p < x < q` on rationals, with monotonicity and basic-cover conditions verified using lattice properties and density of `Rat`.
