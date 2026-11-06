### Analysis.Integration.MeasureIntegrationSpace

The integration space of simple functions on a measure ring, and the unit and counit of the adjunction between measure rings and integration spaces.

For a pseudo-measure ring `B`, the module shows that the rational-valued simple functions `SFunc RatField B` form an integration space `SFuncIntegrationSpace B`, the integral of a simple function being the sum of coefficients times measures. A homomorphism of measure rings induces a homomorphism of simple-function spaces (`SFuncHom`), which is dense, and an equivalence on completions, when the original homomorphism is dense. The characteristic function `SFunc-char a` gives the unit `integrationUnit B : B -> (SFuncIntegrationSpace B).MeasRing`, and the evaluation of simple functions in an arbitrary integration space gives the counit `integrationCounit Z`. These satisfy the naturality and triangle identities of an adjunction, assembled in `IntegrationSpaceCat`; the unit is an equivalence (`integrationUnit-equiv`) and, for complete spaces, the counit is injective with closed image. Completed versions of the unit and counit are provided too.

#### Simple functions as an integration space

- **`SFuncIntegrationSpace`**: For a `PseudoMeasureRing B`, the integration space of rational simple functions on `B`, with integral the sum of `coefficient * meas`.
- **`SFunc-single-abs`**: The absolute value of the difference of two one-term simple functions with the same coefficient `c` is the one-term simple function with coefficient `abs c` and element `a + b`.
- **`SFunc-single-dist`**: The `lnorm` of the difference of two one-term simple functions with coefficient `c` at `a` and `b` is `abs c * meas (a - b)`.
- **`SFunc-norm-ext`**: If the measure of `B` separates points (`meas e = 0` implies `e = 0`), then a simple function of zero norm is zero.
- **`SFunc-char`**: The characteristic simple function of `a : B`, the one-term simple function with coefficient `1`.

#### Functoriality and density

- **`SFuncHom`**: A `MeasureRingHom` `f : A -> B` induces an `IntegrationSpaceHom` between the spaces of simple functions.
- **`SFuncHom-dense`**: If `f` is dense, the image of `SFuncHom f` is dense in `SFuncIntegrationSpace B`.
- **`SFuncHom-completion-equiv`**: A dense measure ring homomorphism `f` induces an equivalence of the completions of the simple-function spaces.

#### Unit of the adjunction

- **`integrationUnit`**: The unit, a `MeasureRingHom` from `B` to the measure ring of `SFuncIntegrationSpace B`, sending `a` to `SFunc-char a`.
- **`SFunc-round`**: For a simple function `s`, there is `a : B` such that `abs (SFunc-char a - s)` equals `abs` of the defect of `s` (the `TruncRieszSpace.defect`).
- **`SFunc-char-approx`**: For a Boolean element `e` of a truncated Riesz space `Y` and a homomorphism `f` from simple functions into `Y`, every simple function `s` has some `a : B` with `abs (f (SFunc-char a) - e)` bounded by four times `abs (f s - e)`.
- **`integrationUnit-equiv`**: `integrationUnit B` is an equivalence.
- **`completeIntegrationUnit`**: The unit into the measure ring of the completion of `SFuncIntegrationSpace B`.
- **`completeIntegrationUnit-equiv`**: For a `CompleteMeasureRing B`, `completeIntegrationUnit B` is an equivalence.

#### Counit of the adjunction

- **`integrationCounit`**: The counit, an `IntegrationSpaceHom` from the simple functions on the measure ring of `Z` to `Z`, evaluating a simple function in `Z`.
- **`integrationCounit-natural`**: Naturality of `integrationCounit` with respect to homomorphisms `f : Z -> Z'`.
- **`integrationCounit-char`**: `integrationCounit Z` sends `SFunc-char a` to the underlying element of `a`.
- **`integrationCounit-unit`**: The triangle identity: `integrationCounit` composed with `SFuncHom (integrationUnit B)` is the identity on `x`.
- **`integrationCounit-inj`**: For a `CompleteIntegrationSpace Z`, `integrationCounit Z` is injective.
- **`completeIntegrationCounit`**: The counit on the completion of the simple functions on the measure ring of a complete integration space `Z`, as a homomorphism to `Z`.
- **`completeIntegrationCounit-char`**: `completeIntegrationCounit Z` agrees with `integrationCounit Z` on elements coming from simple functions.
- **`completeIntegrationCounit-inj`**: `completeIntegrationCounit Z` is injective.
- **`completeIntegrationCounit-closed`**: The image of `completeIntegrationCounit Z` is closed in `Z`.
