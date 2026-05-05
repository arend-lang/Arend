### Analysis.Limit

Limits of nets (functions from directed sets) in cover spaces, with characterizations in uniform, topological-group, and metric settings.

#### Convergence

- **`IsConvergent`**: A net `f : I -> X` from a directed set `I` to a cover space `X` is convergent iff it lifts to a `CoverMap` from the directed cover space of `I` into `X`.
- **`convergent-char`**: Characterizes convergence: for every Cauchy cover `C` of `X`, eventually `f n` lies in some `U : C`. The inner `conv` lemma gives the converse direction.
- **`limit-conv`**: If `f` has a limit `l` in `X`, then `f` is convergent.
- **`convergent-compose`**: Convergence is preserved by composition with a `CoverMap`.

#### The Limit Operation

- **`limit`**: The limit of a net `f : I -> X` into a `CompleteCoverSpace`, returned as a `Partial X` defined exactly when `f` is convergent. Its value is obtained by lifting the eventuality filter through completion.
- **`limit.infPoint`**: The point in `Completion (DirectedCoverSpace I)` corresponding to the eventuality filter on `I`.
- **`limit.char`**: Identifies `limit fc` with the filter-point of `fc.func-cauchy EventualityFilter`.
- **`limit-isLimit`**: The value `limit f fc` is in fact a limit of `f` in the sense of `X.IsLimit`.
- **`limit-char`**: `limit f = defined l` iff `l` is a limit of `f`.
- **`limit-apply`**: Continuity of limits: for a `CoverMap` `g`, `g (limit f) = limit (g ∘ f)`.
- **`limit-ext`**: Pointwise equal nets have equal limits.

#### Uniform and Topological Group Characterizations

- **`convergent-uniform-char`**: Convergence in a `RegularPreuniformSpace` characterized via uniform covers (Cauchy condition on uniform covers). Includes a converse `conv` lemma.
- **`convergent-topAbGroup-char`**: TFAE characterization of convergence in a topological abelian group: net convergence ⇔ Cauchy condition on differences `f m - f n` ⇔ Cauchy condition on differences `f n - f N`.

#### Metric Characterizations

- **`convergent-metric-char`**: In an `ExPseudoMetricSpace`, `f` is convergent iff for every rational `eps > 0`, eventually `dist (f n) (f N) < eps`. The `double` helper produces a two-sided Cauchy witness.
- **`convergent-metric-real`**: Same as above but with real `eps`, in a `PseudoMetricSpace`.
- **`limit-metric-char`**: `l` is a limit of `f` iff for every `eps > 0`, eventually `dist l (f n) < eps` (extended-rational version).
- **`limit-metric-real`**: Real-`eps` version of `limit-metric-char` for `PseudoMetricSpace`.
- **`nat-limit-metric`**: A geometric convergence criterion for `Nat`-indexed sequences: if distances satisfy `dist l (f (suc n)) <= d * dist l (f n)` with `0 <= d < 1` and the initial distance is bounded, then `l` is the limit. The `induction` helper gives the explicit bound `dist l (f n) <= q^n * B`.

#### Limits at a Point (Topological)

- **`SubPointDirectedSet`**: Directed set of open neighborhoods of a limit point `a` of a set `U`, ordered by reverse inclusion. Elements are tuples `(V, V open, V a, x, V x, U x)`. Used to express `lim_{x -> a, x ∈ U}`.
- **`SubPointDirectedSet.limit-comp`**: Composition of net limits with limits at a point: if `f n -> lx` along `f` taking values in `S` and `g(h) -> ly` along the neighborhood directed set, then `g (f n, _) -> ly`.
- **`SubPointDirectedSet.map`**: Functoriality of the neighborhood directed set under a continuous map with a section.
- **`SubPointDirectedSet.limit-id`**: The "tag" projection from `SubPointDirectedSet` to `X` has limit `a`.
- **`SubPointDirectedSet.limit-char`**: Characterization of `lim_{x -> a, S x} f x = y` via the open-neighborhood/epsilon-delta-style condition.

#### Punctured Neighborhood of Zero (for Skew Fields)

- **`InvDirectedSet`**: The neighborhood directed set at `0` in a `NearSkewField`, restricted to invertible elements lying in an open set `S` containing `0`. Used for limits like `lim_{h -> 0, h invertible} f(h)`.
- **`InvDirectedSet.aux`**: `0` is a limit point of the invertible elements in `S`.
- **`InvDirectedSet.limit-id`**: The projection to `R` has limit `0`.
- **`InvDirectedSet.limit-char`**: Open-neighborhood characterization of limits taken as `h -> 0` over invertible `h ∈ S`.
