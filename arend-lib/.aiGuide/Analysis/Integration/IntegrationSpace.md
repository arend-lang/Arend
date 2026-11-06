### Analysis.Integration.IntegrationSpace

Abstract integration spaces: truncated Riesz spaces equipped with a positive additive integral, their morphisms, their completions, and the measure ring of Boolean elements.

An `IntegrationSpace` is a Riesz space with a truncation (`TruncRieszSpace`) and a real-valued `integral` that is additive and non-negative on non-negative elements. Its norm is `integral (abs a)`, which makes it a pseudo-normed Riesz space, and `trunc` is non-expanding for the induced metric. The Boolean elements (those fixed by `trunc` after doubling) form a pseudo-measure ring `MeasRing` with `meas = integral`. `IntegrationSpaceHom` are Riesz-space homomorphisms preserving truncation and integral; they induce homomorphisms of measure rings. A `CompleteIntegrationSpace` is an integration space that is complete in its norm. Every integration space has a completion `IntegrationSpaceCompletion X` with an isometric embedding `integrationCompletion`, which is universal among homomorphisms into complete integration spaces (`integrationCompletion-lift`) and functorial (`integrationCompletion-map`). Basic examples are the rationals and the reals.

#### Integration spaces

- **`IntegrationSpace`**: A truncated Riesz space `E` with a real `integral : E -> Real` that is additive (`integral_+`) and non-negative on non-negative elements (`integral>=0`); the norm is `integral (abs a)`.
- **`IntegrationSpace.integral-hom`**: The integral as an `AddGroupHom` from the space to `RealAbGroup`.
- **`IntegrationSpace.integral-mono`**: The integral is monotone: `a <= b` implies `integral a <= integral b`.
- **`IntegrationSpace.integral_abs`**: `abs (integral a) <= integral (abs a)`.
- **`IntegrationSpace.integral_*q`**: The integral commutes with multiplication by a rational: `integral (q *q a) = q * integral a`.
- **`IntegrationSpace.integral-metric`**: The integral is a `NormedAbGroupMap` from the space to `RealNormed`.
- **`IntegrationSpace.trunc-metric`**: Truncation `trunc` is a `MetricMap` from the space to itself.
- **`IntegrationSpace.MeasRing`**: The pseudo-measure ring of Boolean elements, with measure given by the integral.
- **`RatIntegrationSpace`**: The rationals as an integration space, with the integral being the embedding into the reals and truncation `x ∧ 1`.

#### Morphisms of integration spaces

- **`IntegrationSpaceHom`**: A homomorphism of truncated Riesz spaces between integration spaces that is a normed isometric map and preserves the integral (`func-integral`).
- **`IntegrationSpaceHom.func-boolean`**: A homomorphism sends Boolean elements to Boolean elements.
- **`IntegrationSpaceHom.MeasRingHom`**: The induced `MeasureRingHom` between the measure rings of the domain and codomain.

#### Complete integration spaces

- **`CompleteIntegrationSpace`**: An integration space that is a complete normed group (`RieszLSpace`, `CompleteNormedAbGroup`).
- **`CompleteIntegrationSpace.CompleteMeasRing`**: The measure ring of a complete integration space is a `CompleteMeasureRing`.
- **`CompleteIntegrationSpace.IsBoolean-closed`**: In a complete integration space, the set of Boolean elements is closed.
- **`RealIntegrationSpace`**: The reals as a complete integration space, with the identity as integral.

#### Completion

- **`IntegrationSpaceCompletion`**: The completion of an integration space `X`, itself a `CompleteIntegrationSpace`.
- **`integrationCompletion`**: The canonical isometric embedding of `X` into `IntegrationSpaceCompletion X`.
- **`dense-integration-lift`**: Given a dense homomorphism `f : X -> Y` and a homomorphism `g : X -> Z` into a complete integration space, produces a homomorphism `Y -> Z`.
- **`integrationCompletion-lift`**: The universal property of the completion: a homomorphism from `X` to a complete integration space `Z` extends to the completion of `X`.
- **`integrationCompletion-map`**: A homomorphism `X -> Y` induces a homomorphism between the completions.
- **`integrationCompletion-map-equiv`**: If `f` is dense, then `integrationCompletion-map f` is an equivalence.
