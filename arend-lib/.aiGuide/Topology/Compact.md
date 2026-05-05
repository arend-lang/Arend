### Topology.Compact

Total boundedness, compactness, and local uniformity for cover spaces, with characterizations via uniform/Cauchy covers and metric balls.

#### Total Boundedness

- **`IsTotallyBounded`**: A cover space is totally bounded if every Cauchy cover admits a finite subcover (every Cauchy `C` has a finite array `U` whose elements still cover and refine into `C`).
- **`IsTotallyBounded.IsCover`**: `C` covers `X` if every `x : X` lies in some `U : C`.
- **`IsTotallyBounded.Cond`**: Generic condition asserting that every `P`-cover has a finite refinement satisfying `Q`.
- **`IsCompact`**: A complete cover space is compact iff it is totally bounded.

#### Uniform Refinements of Cauchy Covers

- **`totallyBounded-uniform`**: For a regular proper-uniform totally bounded space, every Cauchy cover admits a finite uniform refinement.
- **`totallyBounded-cauchy-uniform`**: Under the same hypotheses, any Cauchy cover is itself uniform.
- **`totallyBounded-strong-uniform`**: Strongly-regular weakly-proper-uniform variant of `totallyBounded-uniform`.
- **`totallyBounded-cauchy-strong-uniform`**: Strongly-regular weakly-proper-uniform variant of `totallyBounded-cauchy-uniform`.
- **`totallyBounded-uniform-char`**: Characterization: total boundedness follows from the finite-refinement condition restricted to uniform covers.
- **`totallyBounded-uniform-char.totallyBounded-closure`**: Auxiliary closure lemma extending the uniform-cover refinement to the closure under `isUniform`.

#### Maps from Totally Bounded Spaces

- **`makeUniformMapTB`**: A `CoverMap` out of a regular proper-uniform totally bounded space is automatically a `UniformMap`.
- **`makeUniformMapSTB`**: Strongly-regular weakly-proper-uniform analogue of `makeUniformMapTB`.

#### Totally Bounded Subsets

- **`IsTotallyBoundedSet`**: A subset `U ⊆ X` is totally bounded if the subspace `\Sigma (x : X) (U x)` (with the cover-transferred structure) is totally bounded.
- **`totallyBoundedSet-char`**: `U` is totally bounded iff every Cauchy cover of `X` has a finite subarray covering `U`.
- **`totallyBoundedSet-subset`**: Total boundedness is closed under taking subsets: `V ⊆ U` and `U` totally bounded implies `V` totally bounded.
- **`totallyBoundedSet-uniform-char`**: Same characterization as `totallyBoundedSet-char` but using uniform covers, in a regular preuniform space.
- **`totallyBoundedSet-metric-char`**: Metric characterization: in an extended pseudometric space, `S` is totally bounded iff for every `eps > 0` there is a finite `eps`-net covering `S`.

#### Local Uniformity

- **`IsLocallyUniform`**: Relation `C` is locally uniform with respect to `E` if for every `U : C` the family of intersections `U ∧ V` (for `V` ranging in a uniform cover) refines into `E`.
- **`IsLocallyUniformSpace`**: A preuniform space is locally uniform if there exists a uniform `C` that is locally uniform with respect to every Cauchy cover.
- **`locallyUniform-cauchy`**: If `C` is Cauchy and `E` is locally uniform over `C`, then `E` is Cauchy.
- **`locallyUniform-cover-char`**: In a strongly regular uniform space, if every member of `C` is totally bounded and inhabited, then `C` is locally uniform with respect to any Cauchy cover.
- **`locallyTotallyBounded-locallyUniform`**: A strongly regular proper-uniform space whose uniform covers consist of totally bounded sets is locally uniform.

#### Metric Local Uniformity

- **`BallsTotallyBounded`**: Property of a metric space asserting that every open ball `OBall eps x` is totally bounded.
- **`metric-locallyUniform`**: A pseudometric space with totally bounded balls is locally uniform.

#### Maps from Locally Uniform Spaces

- **`locallyUniformMapFromCover`**: A `CoverMap` out of a locally uniform regular preuniform space is automatically a `LocallyUniformMap`.
