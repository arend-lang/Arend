### Analysis

This directory formalizes mathematical analysis: convergence of nets and series, limits, derivatives, power series, and measure theory, developed over general topological/normed structures rather than fixed to the reals.

#### Limits and Convergence

- **`Limit.md`** — Convergence and limits of nets indexed by directed sets in cover spaces, with specializations to topological abelian groups and metric spaces, plus directed-set machinery (`SubPointDirectedSet`, `InvDirectedSet`) for limits at a point.
- **`FuncLimit.md`** — Convergence of parametrized families of functions, uniform convergence, and limits along directed sets in cover/uniform/metric/topological-group settings.

#### Series

- **`Series.md`** — Infinite series in topological abelian groups: ordinary and absolute convergence, mid-sums, Cauchy criterion, comparison/ratio/Weierstrass M-tests, and the `seriesSum` partial-valued sum operation.
- **`PowerSeries.md`** — Formal power series over extended pseudo-normed rings, the radius of convergence as a `LowerReal`, absolute convergence inside the radius, and ratio-style convergence tests.

- **`CauchyProduct.md`** — Cauchy product `conv`/`uconv` of series, norm bounds, and convergence of the product of absolutely convergent series to the product of the sums.
- **`SeriesComposition.md`** — Convolution powers, Tannery's theorem, and convergence of the composition of power series under several hypotheses.

#### Polynomials

- **`Poly.md`** — Norm bounds for polynomial evaluation via coefficient bounds, and continuity of `polyEval` over a complete normed ring.

#### Differentiation

- **`Derivative.md`** — Directional and total (Fréchet-style) derivatives for maps between topological left modules over a near-skew field, defined via limits of difference quotients, with linearity, Leibniz, and chain rules.
- **`StrongDerivative.md`** — Strong (Carathéodory-style) derivatives, defined by the existence of a continuous difference-quotient function, yielding linear-map derivatives and standard calculus rules at the quotient level.

#### Subdirectories

- **`Calculus/`** — Synthetic-differential-geometry style derivatives over rings with nilpotent infinitesimals.
- **`Integration/`** — Integration spaces (Riesz spaces with positive integral), their morphisms, completions and categories, and the adjunction with measure rings via simple functions.
- **`Measure/`** — Measure theory: simple functions, measure rings with categories and completion, tensor and product measure rings, the interval ring, and Lebesgue measure rings with `L¹(ℝⁿ)`.
- **`PowerSeries/`** — Termwise derivative of a power series, which keeps the radius of convergence.
