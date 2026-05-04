### Topology.Locale.Real

The locale of real numbers, constructed as a presented frame on intervals over the rationals, with rational points and closed-interval compactness.

#### Interval Order Structure

- **`Interval`**: An interval `\Sigma Q Q` over a poset `Q`, representing a pair `(a, b)`.
- **`IntervalPoset`**: Poset structure on `Interval Q` where `x <= y` iff `y.1 <= x.1` and `x.2 <= y.2` (containment of intervals).
- **`IntervalPoset.<=`**: The interval containment order.
- **`IntervalBiordered`**: Biordered structure on `Interval Q` with strict containment `<` (both endpoints strictly inside).
- **`IntervalBiordered.<`**: Strict interval containment.
- **`IntervalSemilattice`**: Meet-semilattice on `Interval Q` for a lattice `Q`, with `meet (a,b) = (a.1 ∨ b.1, a.2 ∧ b.2)` (intersection of intervals).

#### Frame Presentation

- **`RealPres`**: The frame presentation of the real-number locale on intervals over a decidable linear order `Q`. Uses interval intersection as conjunction and a basic cover indexed by two cases: subdivision of `(p,s)` into `(p,q)` and `(r,s)` when `p<r<q<s`, and refinement of `(p,q)` by all sub-intervals `(r,s)` strictly inside.

#### Cover Lemmas for `RealPres`

- **`RealPres.<=-cover`**: Interval containment implies a one-element cover (`Cover1 x y` from `x <= y`).
- **`RealPres.cover-empty`**: A degenerate interval (`x.2 <= x.1`) is covered by anything.
- **`RealPres.cover-pair`**: Two-element subdivision cover: `x` is covered by `(x.1, z2)` and `(z1, x.2)` whenever `z1 < z2`.
- **`RealPres.point`**: Embeds a point `x : Q` as the degenerate interval `(x, x)`.
- **`RealPres.toPointwiseCover`**: A frame cover of `a` yields, for each point strictly inside `a`, an index `j` with that point strictly inside `g j`.
- **`RealPres.cover-factor-left`**, **`RealPres.cover-factor-right`**: Technical helpers used to decompose pointwise covers by removing one element from the cover and finding a slightly smaller interval still covered by the remainder.
- **`RealPres.fromPointwiseCover`**: Converse of `toPointwiseCover` for dense decidable linear orders: pointwise coverage by points strictly inside implies a frame cover.
- **`RealPres.wayBelow`**: Strict interval containment `x < y` implies the way-below relation `x << y` in the frame presentation.
- **`RealPres.locallyCompact`**: The presentation is locally compact (over a dense decidable linear order).

#### Real Locale

- **`RealLocale`**: The locale of reals, defined as `PresentedFrame (RealPres RatField)`.
- **`RealLocale.locallyCompact`**: `RealLocale` is locally compact.
- **`RealLocale.wellInside`**: Strict interval containment `x < y` implies the well-inside relation `embed x <=< embed y`.
- **`RealLocale.regular`**: `RealLocale` is a regular locale.
- **`RealLocale.ratPoint`**: A rational `x : Rat` as a point (complete filter) of `RealLocale`, given by intervals `(a, b)` with `a < x < b`.
- **`RealLocale.hasStronglyDensePoints`**: `RealLocale` has strongly dense points (the rational points suffice).

#### Half-Lines and Closed Intervals

- **`lowerHalf`**: The lower half-line `(-∞, x]` as a sublocale, defined as the join of all `embed (a, b)` with `b <= x`.
- **`upperHalf`**: The upper half-line `[x, +∞)` as the join of all `embed (a, b)` with `x <= a`.
- **`closedInterval`**: The closed interval `[x, y]` as the locale obtained from the closed nucleus on `lowerHalf x ∨ upperHalf y`.
- **`closedInterval.nucleus`**: The closed nucleus defining `closedInterval x y`.
- **`closedInterval.compact`**: Closed intervals are compact.
- **`closedInterval.compact.generalized`**: Generalized form: the nucleus locale for any `a < x`, `y < b` is compact.

#### Rational Inclusion

- **`rat_real`**: Locale homomorphism `Hom (discrete Rat) RealLocale` from the discrete locale of rationals to `RealLocale`, sending an interval `p` to the predicate "`x` lies strictly between `p.1` and `p.2`".
