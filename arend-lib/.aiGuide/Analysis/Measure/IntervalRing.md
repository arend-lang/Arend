### Analysis.Measure.IntervalRing

The measure ring of finite unions of half-open rational intervals, with the length measure: the one-dimensional Lebesgue measure ring before completion.

Elements are finite lists of intervals `[p,q)` with rational endpoints (`RatInterval`), identified when they contain the same rational points (`RatIntervalSet`, a quotient by `RatIntervalEq`). Addition is symmetric difference and multiplication is intersection, giving the Boolean ring `IntervalBRing`; together with the length measure `measQ` it forms the measure ring `IntervalRing`. Points are rational because over the reals finite unions of intervals do not form a Boolean ring constructively.

Everything is computed on a common refinement: a sorted list of cut points `T` cuts the line into cells `[tⱼ,tⱼ₊₁)`, and a predicate adapted to `T` is constant on each cell. Boolean operations take the cells of the common refinement on which the combination holds, and the measure is the total length of the cells in the set. Inserting a point does not change that total, so the measure is independent of the refinement. The inner module `Refinement` holds the technical lemmas about refinements.

#### Rational order and intervals

- **`ratLt`**: The decidable order `x < y` of rationals as a `Bool`.
- **`ratLt-char`** / **`ratLt-false`**: `ratLt x y = true` iff `x < y`, and `ratLt x y = false` iff `y <= x`.
- **`RatInterval`**: A half-open interval `[p,q)`, a pair of rationals.
- **`inRI`**: Boolean membership of a rational point in a `RatInterval`.
- **`inRI-char`**: `inRI c t = true` iff `c.1 <= t` and `t < c.2`.
- **`inRIs`**: Boolean membership of a point in the union of a list of intervals.
- **`ends`**: The list of endpoints of a list of intervals.

#### Refinements

- **`snap`**: For a list of cut points `T` and a predicate `P`, the value of `P` at the left end of the cell of `T` containing `t`, and `false` outside the cells.
- **`Adapted`**: `P` is adapted to `T` if `P t = snap T P t` for all `t`, i.e. it is constant on the cells of `T` and vanishes outside them.
- **`cells`**: The list of cells of `T` on which `P` holds.
- **`measT`**: The total length of the cells of `T` on which `P` holds.
- **`measL`**: The measure of a list of intervals, computed with `measT` on the sorted list of its endpoints.
- **`measL-char`**: For a sorted `T` and `P` adapted to it that agrees with `inRIs l`, `measL l = measT T P`: the measure can be computed on any adapted refinement.
- **`refine`**: The common refinement (list of cut points) of two lists of intervals.
- **`refine-left`** / **`refine-right`**: `inRIs l` and `inRIs l'` are both adapted to `refine l l'`.
- **`measL-equiv`**: Two lists of intervals containing the same points have the same measure.

#### The Boolean ring of interval sets

- **`RatIntervalEq`**: Two lists of intervals are equivalent if they contain the same points.
- **`RatIntervalSet`**: The quotient of lists of intervals by `RatIntervalEq`.
- **`ratIntervals`**: The finite union of a list of intervals, as an element of `RatIntervalSet`.
- **`ratPoints`**: The Boolean predicate on rationals giving the points of an element of `RatIntervalSet`.
- **`ratPoints-ext`**: Elements with the same points are equal.
- **`opRIs`**: The boolean combination by `f` of two lists of intervals: the cells of their common refinement on which it holds.
- **`inRIs-opRIs`**: For `f` with `f false false = false`, the points of `opRIs f l l'` are the pointwise `f` of those of `l` and `l'`.
- **`opRISet`**: `opRIs` lifted to `RatIntervalSet`.
- **`ratPoints_opRISet`**: The points of `opRISet f f0 u v` are the pointwise `f` of the points of `u` and `v`.
- **`IntervalBRing`**: The `BooleanRing` structure on `RatIntervalSet`; addition is symmetric difference and multiplication is intersection.
- **`ratPoints_+`** / **`ratPoints_*`** / **`ratPoints_join`**: The points of a sum, product and join are the pointwise `xor`, `and` and `or` of the points.
- **`ratPoints-interval`**: The points of the one-interval union `[p,q)` are given by `inRI`.

#### The measure ring

- **`measQ`**: The rational measure of an element of `RatIntervalSet`, defined as `measL` of a representative.
- **`IntervalRing`**: The `MeasureRing` on `RatIntervalSet` with measure `Real.fromRat` of `measQ`: finite unions of rational intervals with the length measure.
- **`IntervalRing-rational`**: The measure of every element of `IntervalRing` is a rational number.
- **`IntervalRing-point`**: Emptiness is decidable: an element is either `0` or contains a rational point.
- **`meas_interval`**: The measure of the interval `[p,q)` is `max (q - p) 0`.
