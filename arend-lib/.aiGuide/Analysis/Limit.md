### Analysis.Limit

Convergence and limits of nets indexed by directed sets in cover spaces, with specializations to topological abelian groups and metric spaces.

The module defines convergence as a `CoverMap` from the directed cover space of an index set, unifying nets, sequences, and limits along filter bases. In a `CompleteCoverSpace`, every convergent net has a canonical `limit` (a partial function), constructed via the universal property of the completion applied to the eventuality filter on the index set. The Cauchy-style characterizations specialize the abstract definition to uniform spaces, topological abelian groups (where convergence reduces to differences entering neighborhoods of zero), and metric spaces (with rational and real `eps` formulations). The auxiliary `SubPointDirectedSet` and `InvDirectedSet` constructions provide directed-set indexing for limits at a point of a topological space, supporting limits of partial functions and pointwise limits at zero in near-skew-fields.

#### Convergence

- **`IsConvergent`**: A net `f : I -> X` is convergent iff it is a `CoverMap` from `DirectedCoverSpace I` to `X`.
- **`convergent-char`**: Cauchy-style characterization: `f` is convergent iff for every cauchy cover `C`, some element `U : C` eventually contains `f n`.
  - **`convergent-char.conv`**: The reverse implication, extracting the eventual-membership property from convergence.
- **`limit-conv`**: A net with a limit (`X.IsLimit f l`) is convergent.
- **`convergent-compose`**: Convergence is preserved under composition with a `CoverMap`.

#### The Limit Operation

- **`limit`**: For `X : CompleteCoverSpace`, the partial function `I -> X` defined on convergent nets, returning the value obtained by lifting to the completion and evaluating at the eventuality filter point.
  - **`limit.infPoint`**: The completion point built from the eventuality filter on the directed set.
  - **`limit.char`**: Identifies the limit value with the filter-point of the cauchy filter induced by `f`.
- **`limit-isLimit`**: The computed `limit f fc` is indeed a limit of `f` in the sense of `X.IsLimit`.
- **`limit-char`**: `limit f = defined l` iff `X.IsLimit f l`.
- **`limit-apply`**: Limits commute with cover-continuous maps: `g (limit f) = limit (g ∘ f)`.
- **`limit-ext`**: Pointwise-equal nets have equal limits.

#### Specialized Convergence Criteria

- **`convergent-uniform-char`**: For regular preuniform spaces, convergence is characterized by eventual entry into uniform-cover elements.
  - **`convergent-uniform-char.conv`**: The reverse direction.
- **`convergent-topAbGroup-char`**: For topological abelian groups, three equivalent forms (TFAE): `IsConvergent f`, the Cauchy condition `U (f m - f n)` eventually for `n <= m`, and `U (f n - f N)` eventually with a fixed `N`.
- **`convergent-metric-char`**: For extended pseudo-metric spaces, convergence is `∀ eps > 0, ∃ N, ∀ n >= N, (dist (f n) (f N)).U eps`.
  - **`convergent-metric-char.double`**: Two-sided Cauchy version: `(dist (f n) (f m)).U eps` for `n, m >= N`.
- **`convergent-metric-real`**: Real-valued `eps` variant for ordinary pseudo-metric spaces.

#### Metric Limit Characterizations

- **`limit-metric-char`**: `X.IsLimit f l` iff `∀ eps > 0, ∃ N, (dist l (f n)).U eps` eventually (extended pseudo-metric form).
- **`limit-metric-real`**: Real `eps` variant of the above.
- **`nat-limit-metric`**: Geometric convergence: if `dist l (f (suc n)) <= d * dist l (f n)` with `0 <= d < 1` and `dist l (f 0)` bounded, then `f` converges to `l`.
  - **`nat-limit-metric.induction`**: Inductive bound `dist l (f n) <= q^n * B` driving the convergence proof.

#### Directed Sets at a Point

- **`SubPointDirectedSet`**: For a limit point `a` of a set `U` in a topological space, the directed set of tuples `(V, Va, x, Vx, Ux)` ordered by reverse inclusion of the open neighborhood `V`. Used to take limits of partial functions defined on `U` as `x -> a`.
  - **`SubPointDirectedSet.limit-comp`**: Composition lemma: a limit of `g` along `SubPointDirectedSet` transfers to a limit of `g ∘ f` along any net `f` converging to `lx` and staying in `S`.
  - **`SubPointDirectedSet.map`**: Functoriality: continuous maps with sections induce maps of `SubPointDirectedSet`s.
  - **`SubPointDirectedSet.limit-id`**: The projection `h.4` (the `x` component) tends to `a`.
  - **`SubPointDirectedSet.limit-char`**: Characterization of limits along `SubPointDirectedSet` in `(open V, V y) <-> (open U, U a, ...)` form — the standard ε-δ-style continuity at a limit point.
- **`InvDirectedSet`**: For an open `S` containing `0` in a near-skew-field, the directed set of nonzero invertible elements approaching `0` within `S`. Built as a `SubPointDirectedSet` with predicate `Inv ∧ S`.
  - **`InvDirectedSet.aux`**: `0` is a limit point of `Inv ∧ S` whenever `S` is open and contains `0`.
  - **`InvDirectedSet.limit-id`**: The element itself tends to `0`.
  - **`InvDirectedSet.limit-char`**: Open-neighborhood characterization of limits indexed by `InvDirectedSet`, used for limits like `f(h)/h` as `h -> 0` through invertible elements.
