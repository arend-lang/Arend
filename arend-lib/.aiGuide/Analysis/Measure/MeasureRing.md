### Analysis.Measure.MeasureRing

Boolean rings equipped with a measure valued in the extended upper reals, viewed as topological abelian groups whose topology is induced by the measure.

A `PremeasureRing` combines a `BooleanRing` with a `TopAbGroup` structure, where the topology comes from "measure balls" `OBall a eps c` consisting of elements `z` such that `meas (a * (c + z)) < eps`. The measure is required to be non-negative, vanish on `0`, be monotone, and additive on disjoint elements (so countable subadditivity for `+` follows). A `MeasureRing` adds a separation axiom (zero measure implies zero), making the induced uniform structure Hausdorff, and a `CompleteMeasureRing` further requires Cauchy filters (characterized in terms of measure balls) to converge. The construction yields canonical extended (pseudo)normed abelian group structures, bridging measure theory with the library's normed/cover-space infrastructure.

#### Premeasure Rings

- **`PremeasureRing`**: Class extending `BooleanRing` and `TopAbGroup`. Carries a measure `meas : E -> ExUpperReal` satisfying `meas_zro`, non-negativity, disjoint additivity, and induces the topology via measure balls. Monotonicity (`meas-mono`) and subadditivity (`meas_+`) are derivable defaults.
- **`meas`**: The measure function `E -> ExUpperReal`.
- **`meas_zro`**: `meas 0 = 0`.
- **`meas>=0`**: Non-negativity of the measure.
- **`meas-disjoint`**: For `x * y = 0`, `meas (x ∨ y) = meas x + meas y`.
- **`meas-mono`**: Monotonicity in the Boolean order: `x <= y -> meas x <= meas y`.
- **`meas_+`**: Subadditivity: `meas (x + y) <= meas x + meas y`.
- **`isOpen`**: Default topology — `U` is open iff every point has a measure ball `(meas (a * (x + y))).U eps` contained in `U`.
- **`+-cont`**: Continuity of addition with respect to the measure topology.

#### Measure Balls

- **`OBall`**: The measure ball `OBall a eps c = { z | meas (a * (c + z)) < eps }`, parameterized by a "scaling" element `a`, radius `eps : Rat`, and center `c`.
- **`OBall-open`**: Each ball is open in the induced topology.
- **`OBall-center`**: For `eps > 0`, the center belongs to its own ball.
- **`OBall-center_<=<`**: The singleton at the center is way-below (`<=<`) the ball, expressing that balls are uniform neighborhoods.
- **`meas-<=<-ball`**: Conversely, every uniform neighborhood `U` of `x` contains some `OBall a eps x` — measure balls form a base for the uniformity.

#### Pseudonormed Structure

- **`PremeasureRing.toPseudoNormed`**: Reinterprets a `PremeasureRing` as an `ExPseudoNormedAbGroup` with `norm = meas`.

#### Measure Rings

- **`MeasureRing`**: Class extending `PremeasureRing` and `SeparatedCoverSpace`. Adds the separation axiom `meas-ext`: zero measure implies zero. Both axioms are interderivable here, and the cover-space separation is established via measure balls.
- **`meas-ext`**: `meas x = 0 -> x = 0`.
- **`MeasureRing.toNormed`**: Promotes a `MeasureRing` to an `ExNormedAbGroup` via `toPseudoNormed`, using `meas-ext` for `norm-ext`.

#### Modularity and Cauchy Characterization

- **`meas-modular`**: Inclusion–exclusion / modularity law: `meas x + meas y = meas (x ∨ y) + meas (x ∧ y)`.
- **`cauchyFilter-measure-char`**: A filter `F` is Cauchy iff for every `a` and every `eps > 0`, some measure ball `OBall a eps x` lies in `F` — a measure-theoretic restatement of the Cauchy condition.

#### Complete Measure Rings

- **`CompleteMeasureRing`**: Class extending `MeasureRing` and `CompleteTopAbGroup`. Completeness is equivalently formulated as `isCompleteMeasure`, the existence of a limit point witnessed uniformly across all measure balls.
- **`isCompleteMeasure`**: For any proper filter `F` satisfying the measure-ball Cauchy condition, there exists `x` such that every `OBall a eps x` (for `eps > 0`) belongs to `F`.
- **`CompleteMeasureRing.toCompleteNormed`**: Packages the result as a `CompleteExNormedAbGroup` built on `toNormed`.
