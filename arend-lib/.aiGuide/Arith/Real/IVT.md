### Arith.Real.IVT

Intermediate Value Theorem for strictly monotone continuous real-valued functions on a closed interval, with applications to constructing inverse functions.

This module formalizes the IVT constructively: given a strictly monotone continuous function `f` on an interval-like subset of the reals with `f(a) <= y <= f(b)`, it builds the unique preimage point as a Dedekind cut whose lower and upper sets describe rationals approximating it from below and above. The construction uses the `IntervalSubset` abstraction — a subset of `Real` containing a closed interval `[a, b]` — and characterizes the cut point via continuity at every internal location. As a corollary, strictly monotone continuous functions yield equivalences onto their image, giving constructive inverses.

#### Interval Subsets

- **`IntervalSubset`**: A `\Sigma` packaging a set `U : Set Real` together with endpoints `a < b` and a witness that `U` contains every `x` with `a <= x <= b`. Used as the domain shape for IVT.
- **`IntervalSubset.left`**: The left endpoint as an element of `S.1`.
- **`IntervalSubset.right`**: The right endpoint as an element of `S.1`.
- **`IntervalSubset.inside`**: The closed interval `[a, b]` as a `Set Real`.

#### Intermediate Value Theorem

- **`IVT-monotone`**: The main IVT: for a strictly monotone continuous `f : Elem S.1 -> Real` and `y` with `f(left) <= y <= f(right)`, the type of `(x, f x = y)` is contractible — yielding a unique preimage of `y`.

#### Cut Construction (inside `IVT-monotone`)

- **`point`**: The preimage of `y` constructed as a `Real` (Dedekind cut). Its lower set consists of rationals `q` such that some `x` in `[a, b]` exceeds `q` and satisfies `f x <= y`; its upper set is dual.
- **`point>=left`**, **`point<=right`**: The cut point lies in `[a, b]`.
- **`point<-char`**, **`point>-char`**: Characterize `x < point` and `point < x` in terms of existence of an interval point witnessing `f <= y` or `y <= f`.
- **`elem`**: Packages `point` together with its membership in `S.1` as an `Elem S.1`.
- **`point<-cont-char`**, **`point>-cont-char`**: Continuity-based criteria: if `f x < y` (resp. `y < f x`) at a point `x`, then `x < point` (resp. `point < x`).
- **`point>=-cont-char`**, **`point<=-cont-char`**: Conversely, if `point <= x` (resp. `x <= point`), then `y <= f x` (resp. `f x <= y`).
- **`image`**: The defining property: `f (elem fm) = y`, established using continuity of `f` at the cut point.

#### Inverse Functions

- **`monotone-inverse`**: Builds an `Equiv` between `Elem U` and `Elem V` from a strictly monotone continuous `f : Elem U -> Real` whose image lands in `V`, provided every `v : V` is sandwiched by `f` on some sub-interval inside `U`. Uses `IVT-monotone` pointwise to invert `f`.

#### Continuity and Order

- **`real-cont_<-inv`**: For `f` continuous at `x`, `f x < f y` implies `x < y || y < x` — order can be reflected through continuous maps disjunctively.
- **`real-cont_<-inv-mono`**: Strengthens the above: if additionally `y < x` would force `f y <= f x` (a monotonicity hypothesis), then `f x < f y` implies `x < y`.
