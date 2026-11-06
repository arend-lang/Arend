### Analysis.Measure.MeasureRing

Boolean rings equipped with a real-valued measure, viewed as (pseudo)normed abelian groups whose norm is the measure.

A `PseudoMeasureRing` combines a `BooleanRing` with a `PseudoNormedAbGroup` structure, where the norm is the measure `meas : E -> Real`. The measure is required to be non-negative, vanish on `0`, and be additive on disjoint elements; monotonicity and subadditivity for `+` follow, and `norm` is defined to be `meas`. A `MeasureRing` adds a separation axiom (zero measure implies zero) and is a `NormedAbGroup`, and a `CompleteMeasureRing` is additionally a `CompleteNormedAbGroup`. `MeasureRingHom` is the measure-preserving ring homomorphism, with lemmas relating density of its image to approximation of elements by the image. The metric and topology are those of the normed group, from `Topology.NormedAbGroup` and `Topology.MetricSpace`.

#### Pseudomeasure Rings

- **`PseudoMeasureRing`**: Class extending `BooleanRing` and `PseudoNormedAbGroup` with a real-valued measure `meas`; the norm is `meas` (so `norm_+` comes from `meas_+`).
- **`PseudoMeasureRing.meas`**: The measure function `E -> Real`.
- **`PseudoMeasureRing.meas_zro`**: `meas 0 = 0`.
- **`PseudoMeasureRing.meas>=0`**: Non-negativity of the measure.
- **`PseudoMeasureRing.meas-disjoint`**: For `x * y = 0`, `meas (x ∨ y) = meas x + meas y`.
- **`PseudoMeasureRing.meas-mono`**: Monotonicity in the Boolean order: `x <= y -> meas x <= meas y`.
- **`PseudoMeasureRing.meas_+`**: Subadditivity: `meas (x + y) <= meas x + meas y`.
- **`PseudoMeasureRing.*-metric`**: Multiplication is 1-Lipschitz (a `MetricMap`) from the product with the sum (Manhattan) metric to the ring.
- **`PseudoMeasureRing.meas_BigSum-disjoint`**: The measure of the `BigSum` of an array of pairwise disjoint elements is the sum of their measures.
- **`PseudoMeasureRing.IsDiscreteMeasure`**: The measure of every element is either zero or positive.
- **`PseudoMeasureRing.IsRationalMeasure`**: The measure of every element is (the image of) a rational number.
- **`PseudoMeasureRing.rational-discrete`**: A rational-valued measure is discrete.

#### Modularity

- **`meas-modular`**: Inclusion–exclusion / modularity law for `PseudoMeasureRing.meas`: `meas x + meas y = meas (x ∨ y) + meas (x ∧ y)`.

#### Measure Ring Homomorphisms

- **`MeasureRingHom`**: A ring homomorphism between pseudomeasure rings that is a normed isometric map, i.e. satisfies `func-meas : meas (func x) = meas x`.
- **`MeasureRingHom.IsApprox`**: `y` can be approximated by the image of `func`: for every `eps > 0` there is `x` with `lnorm (y + func x) < eps`.
- **`MeasureRingHom.approx-dense`**: If every element of the codomain is approximable (`IsApprox`), the image is dense (`IsDense`).
- **`MeasureRingHom.dense-approx`**: Conversely, if the image is dense, every element is approximable.
- **`MeasureRingHom.approx-zro`**: `0` is approximable.
- **`MeasureRingHom.approx-+`**: Approximable elements are closed under `+`.
- **`MeasureRingHom.approx-BigSum`**: The `BigSum` of an array of approximable elements is approximable.

#### Measure Rings

- **`MeasureRing`**: Class extending `PseudoMeasureRing` and `NormedAbGroup`. Adds the separation axiom `meas-ext`; `norm-ext` is derived from it.
- **`MeasureRing.meas-ext`**: `meas x = 0 -> x = 0`.

#### Complete Measure Rings

- **`CompleteMeasureRing`**: Class extending `MeasureRing` and `CompleteNormedAbGroup`: a measure ring that is complete as a normed group.
