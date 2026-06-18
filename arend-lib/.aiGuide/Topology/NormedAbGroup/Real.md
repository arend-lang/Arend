### Topology.NormedAbGroup.Real

Normed abelian group structures on the rationals and reals, with tools for lifting maps from rationals to reals via density.

The real numbers are presented as the strong completion of the rational normed group, packaging both `RatNormed` and `RealNormed` as normed abelian groups using absolute value as norm. The embedding `rat_real : Q → R` is shown to be a dense isometric cover embedding, which is the key fact powering the lifting machinery: any uniformly continuous map out of the rationals (or pairs of rationals) extends uniquely to the reals. The module also provides characterization lemmas that translate `<=<` (rather-below) relations on the reals into rational interval data, making it possible to reason about lifted functions through their rational approximations.

#### Normed Group Instances

- **`RatNormed`**: Normed abelian group instance for `RatField` with norm `abs`.
- **`RealNormed`**: Complete normed abelian group on `Real` (extends `CompleteNormedAbGroup`); strong completeness witnessed by `fromCF` reconstructing a real from a strongly regular Cauchy filter on `RatNormed`.
- **`RealNormed.RealNormedAbGroup`**: The underlying `NormedAbGroup` on `RealAbGroup` with norm `abs`, before adding completeness.
- **`RealNormed.fromCF`**: Builds a `Real` from a `StronglyRegularCauchyFilter RatNormed` by defining its lower/upper Dedekind cuts via rational open balls in the filter.

#### Rational-to-Real Embedding

- **`rat_real`**: The canonical embedding `Real.fromRat` packaged as a `NormedIsometricMap RatNormed RealNormedAbGroup`.
- **`rat_real.dense`**: The image of `rat_real` is dense in the reals.
- **`rat_real.dense-coverEmbedding`**: `rat_real` is a dense embedding of cover spaces (`CoverMap.IsDenseEmbedding`), enabling unique extension of cover maps from `RatNormed` to `RealNormed`.

#### Open Rational Intervals

- **`open-rat-int`**: For rationals `a b`, the set `{x : Real | x.L a ∧ x.U b}` — the open interval `(a, b)` as a subset of `Real`.

#### Density-Based Lifting Characterization

- **`dense-lift-real-char`**: Characterizes when `cauchy-lift f fd g y` lies in `open-rat-int a b`: there must exist a slightly tighter interval `(a', b')` and a neighborhood `V` of `y` whose `f`-preimage maps into `g^{-1}(a', b')`. Used to compute values of lifted maps to the reals.
- **`dense-lift-real-char.makeRealCover`**: For `eps > 0`, the family `{open-rat-int a (a+eps) | a : Rat}` is a Cauchy cover of `RealNormed`.
- **`dense-lift-real-char.<=<_open-rat-int`**: Strict shrinking of rational intervals gives a rather-below relation: `(a', b') <=< (a, b)` when `a < a'` and `b' < b`.
- **`dense-lift-real-char.point_<=<`**: A real `x` with `x.L a` and `x.U b` satisfies `single x <=< open-rat-int a b`.

#### Neighborhoods of Reals

- **`<=<-open-int`**: Any neighborhood `U` of a point `x : Real` (in the `<=<` sense) contains an open rational interval around `x`: there exist `a, b` with `x.L a`, `x.U b` such that every `y` strictly between `a` and `b` lies in `U`.

#### Binary Lifting on Reals

- **`real-lift2`**: Lifts a cover map `f : RatNormed × RatNormed → X` (for any `CompleteCoverSpace` `X`) to a cover map `RealNormed × RealNormed → X`, using density of `rat_real` on each factor.
- **`real-lift2-char`**: Characterizes when `real-lift2 f (x, y)` lands in `open-rat-int a b` in terms of rational intervals around `x` and `y` and the behavior of `f` on those rational rectangles.

#### Real Distance and Order

- **`real-dist>0`**: If `ldist x y > 0` then `x < y` or `y < x` — strict positivity of the distance forces a strict order between two reals.
