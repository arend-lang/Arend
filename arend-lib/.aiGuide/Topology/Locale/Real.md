### Topology.Locale.Real

The locale of real numbers, presented as a frame of rational intervals.

This module constructs the real-line locale `RealLocale` as the frame presentation `RealPres` over rational intervals `(p, q)`, using two basic cover relations: a "subdivision" cover splitting an interval `(p, s)` into overlapping pieces `(p, q) ∪ (r, s)` whenever `p < r < q < s`, and a "shrinking" cover expressing `(p, q)` as the union of all strictly interior subintervals `(r, s)`. Conjunction of intervals is given by the meet `(p ∨ p', q ∧ q')` in the interval lattice. The module proves the resulting locale is locally compact, regular, and has enough (strongly dense) points — including a canonical `ratPoint` for each rational — and builds derived constructions like closed intervals as nuclei and the embedding of the discrete rationals.

#### Interval Order Structures

- **`Interval`**: An interval over a poset `Q` is a pair `\Sigma Q Q`, intended as endpoints `(p, q)`.
- **`IntervalPoset`**: Poset structure on `Interval Q` where `x <= y` iff `y.1 <= x.1` and `x.2 <= y.2` (containment of open intervals).
- **`IntervalPoset.<=`**: The underlying inclusion order on intervals.
- **`IntervalBiordered`**: Biordered (strict + non-strict) structure on intervals over a `BiorderedSet`, with `x < y` iff `y.1 < x.1` and `x.2 < y.2` (strict containment).
- **`IntervalBiordered.<`**: The strict "well-inside" order on intervals.
- **`IntervalSemilattice`**: Meet-semilattice structure on intervals over a lattice; `meet (a, b) = (a.1 ∨ b.1, a.2 ∧ b.2)` (intersection of intervals).

#### Frame Presentation of the Reals

- **`RealPres`**: The frame presentation of the real line over a decidable linear order `Q`. Generators are intervals `Interval Q`, conjunction is interval meet, and basic covers consist of (1) two-piece subdivisions of `(p, s)` via points `r < q` strictly inside, and (2) covers of `(p, q)` by all interior subintervals `(r, s)` with `p < r < s < q`.
- **`RealPres.<=-cover`**: Inclusion of intervals yields a single-element cover `Cover1 x y`.
- **`RealPres.cover-empty`**: A degenerate interval (with `x.2 <= x.1`) is covered by the empty family — i.e., it represents the bottom of the frame.
- **`RealPres.cover-pair`**: For any `z1 < z2`, the interval `x` is covered by the two-element family `{(x.1, z2), (z1, x.2)}`.
- **`RealPres.point`**: Embeds a point `x : Q` as the degenerate interval `(x, x)`, used to represent points abstractly.

#### Pointwise Cover Characterization

- **`RealPres.toPointwiseCover`**: Over a dense decidable linear order, any cover of `a` by `g` implies that for every point `x` strictly inside `a`, some `g j` strictly contains `x`.
- **`RealPres.cover-factor-left`**: Technical decomposition lemma: given a cover by `z :: l` where `z` overlaps `a` on the left, extracts a strict bound `b > z.1` and a pointwise cover of `(a.1, b)` by `l` alone.
- **`RealPres.cover-factor-right`**: Symmetric counterpart of `cover-factor-left`, factoring out an interval that overlaps on the right (proved by duality through `Q.op`).
- **`RealPres.fromPointwiseCover`**: Converse to `toPointwiseCover`: a family `l` that covers every point strictly inside `a` is in fact a cover of `a` in the frame presentation.

#### Compactness Properties

- **`RealPres.wayBelow`**: If `x < y` (strict interval inclusion), then `x` is way-below `y` in the frame presentation.
- **`RealPres.locallyCompact`**: The presentation `RealPres Q` is locally compact whenever `Q` is a dense decidable linear order.

#### The Real Locale

- **`RealLocale`**: The locale of real numbers, defined as the presented frame `PresentedFrame (RealPres RatField)`.
- **`RealLocale.locallyCompact`**: `RealLocale` is locally compact.
- **`RealLocale.wellInside`**: Strict interval inclusion `x < y` yields a well-inside relation `embed x <=< embed y` on the embedded generators.
- **`RealLocale.regular`**: `RealLocale` is a regular locale.
- **`RealLocale.ratPoint`**: For each rational `x`, the canonical point of `RealLocale` whose membership in `(a, b)` is `a < x < b`. Built via `framePres-point` from the interval covering rules and density of the rationals.
- **`RealLocale.hasStronglyDensePoints`**: `RealLocale` has strongly dense points — the rational points are enough to detect frame elements.

#### Derived Constructions

- **`lowerHalf`**: The open lower half-line `(-∞, x)` for a rational `x`, formed as the join of all `embed (a, b)` with `b <= x`.
- **`upperHalf`**: The open upper half-line `(x, +∞)` for a rational `x`, dual to `lowerHalf`.
- **`closedInterval`**: The closed interval `[x, y]` realized as the locale `Nucleus.locale {nucleus x y}` — the closed sublocale complementary to `lowerHalf x ∨ upperHalf y`.
- **`closedInterval.nucleus`**: The closed nucleus carving `[x, y]` out of `RealLocale` as the complement of the open exterior `lowerHalf x ∨ upperHalf y`.
- **`closedInterval.compact`**: Heine–Borel: every closed interval `[x, y]` is compact.
- **`closedInterval.compact.generalized`**: Strengthened form: for any rationals `a < x` and `y < b`, the corresponding closed sublocale (cut out by the same nucleus) is compact.

#### Embedding the Rationals

- **`rat_real`**: The locale homomorphism `Hom (discrete Rat) RealLocale` embedding the discrete rational locale into the reals. Constructed via `FrameReflectiveSubcat.adjointMap` from the frame-presentation map sending an interval `(p, q)` to the predicate `p < x < q` on rationals, with conjunction, basic-cover, and image conditions verified using lattice properties and density of `Rat`.
