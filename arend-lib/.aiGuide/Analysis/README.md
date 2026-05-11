### Analysis

This directory formalizes mathematical analysis: convergence of nets and series, limits, derivatives, power series, and measure theory, developed over general topological/normed structures rather than fixed to the reals.

#### Limits and Convergence

- **`Limit.md`** — Convergence and limits of nets indexed by directed sets in cover spaces, with specializations to topological abelian groups and metric spaces, plus directed-set machinery (`SubPointDirectedSet`, `InvDirectedSet`) for limits at a point.
- **`FuncLimit.md`** — Convergence of parametrized families of functions, uniform convergence, and limits along directed sets in cover/uniform/metric/topological-group settings.

#### Series

- **`Series.md`** — Infinite series in topological abelian groups: ordinary and absolute convergence, mid-sums, Cauchy criterion, comparison/ratio/Weierstrass M-tests, and the `seriesSum` partial-valued sum operation.
- **`PowerSeries.md`** — Formal power series over extended pseudo-normed rings, the radius of convergence as a `LowerReal`, absolute convergence inside the radius, and ratio-style convergence tests.

#### Differentiation

- **`Derivative.md`** — Directional and total (Fréchet-style) derivatives for maps between topological left modules over a near-skew field, defined via limits of difference quotients, with linearity, Leibniz, and chain rules.
- **`StrongDerivative.md`** — Strong (Carathéodory-style) derivatives, defined by the existence of a continuous difference-quotient function, yielding linear-map derivatives and standard calculus rules at the quotient level.

#### Subdirectories

- **`Calculus/`** — Synthetic-differential-geometry style derivatives over rings with nilpotent infinitesimals.
- **`Measure/`** — Measure theory: measure rings, outer measures, and integration of simple functions.
