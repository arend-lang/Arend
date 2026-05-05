### Topology.NormedAbGroup.Real

Normed abelian group structures on the rationals and reals, plus tools for lifting cover maps from rationals to reals via density.

#### Normed Group Instances

- **`RatNormed`**: `NormedAbGroup` instance on `RatField` with norm given by absolute value.
- **`RealNormed`**: `CompleteNormedAbGroup` instance on the reals, extending `RealNormedAbGroup` with strong completeness.
- **`RealNormed.RealNormedAbGroup`**: The underlying `NormedAbGroup` on `RealAbGroup` with norm `abs`.

#### Rational-to-Real Embedding

- **`rat_real`**: The `NormedIsometricMap` from `RatNormed` to `RealNormedAbGroup` given by `Real.fromRat`, embedding rationals isometrically into the reals.
- **`rat_real.dense`**: The rational embedding has dense image in the reals.
- **`rat_real.dense-coverEmbedding`**: The rational embedding is a dense cover embedding, enabling lifting of cover maps along it.

#### Cauchy Completion

- **`RealNormed.fromCF`**: Constructs a `Real` from a `StronglyRegularCauchyFilter` on `RatNormed`, defining its lower/upper rational cuts via small open balls in the filter; witnesses strong completeness of the reals.

#### Open Rational Intervals

- **`open-rat-int`**: The open interval `(a, b)` as a `Set Real`, defined by `x.L a` (lower cut contains `a`) and `x.U b` (upper cut contains `b`).
- **`<=<-open-int`**: If `single x <=< U` (the singleton is rather-below `U`), then `U` contains an open rational interval around `x`.

#### Lifting Cover Maps from Rationals to Reals

- **`dense-lift-real-char`**: Characterizes when a lifted cover map `cauchy-lift f fd g y` lands in an open rational interval `(a, b)`, in terms of preimages of slightly larger intervals along the dense embedding.
- **`dense-lift-real-char.makeRealCover`**: For any `eps > 0`, the family of open rational intervals of width `eps` forms a Cauchy cover of the reals.
- **`dense-lift-real-char.<=<_open-rat-int`**: Strict containment of open rational intervals: `(a', b') <=< (a, b)` whenever `a < a'` and `b' < b`.
- **`dense-lift-real-char.point_<=<`**: A point `x` with `x.L a` and `x.U b` satisfies `single x <=< open-rat-int a b`.
- **`real-lift2`**: Lifts a cover map `RatNormed ⨯ RatNormed -> X` (with `X` complete) to `RealNormed ⨯ RealNormed -> X` via density of rationals in reals.
- **`real-lift2-char`**: Characterizes the value of `real-lift2 f (x, y)` lying in an open rational interval, in terms of rational approximations of `x` and `y` and the behavior of `f` on rational pairs.

#### Real Distance

- **`real-dist>0`**: If the lattice distance `ldist x y` between two reals is positive, then `x < y` or `y < x` (positive distance implies strict order in some direction).
