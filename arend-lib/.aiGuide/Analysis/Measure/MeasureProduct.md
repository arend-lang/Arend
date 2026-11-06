### Analysis.Measure.MeasureProduct

The tensor product of Boolean rings and of measure rings, built from simple functions, together with its universal property for measure bimorphisms and its functoriality.

For Boolean rings `A` and `B` the tensor product is the type `SFunc A B` of simple functions (finite formal sums of rectangles `a ⊗ b`), made into a Boolean ring by `BooleanRingTensor`; the rectangle `a ⊗ b` is `rect a b`, and multiplication is first defined on rectangles and extended additively. For pseudo-measure rings `PseudoMeasureRingTensor` adds the measure `μ(∑ aᵢ ⊗ bᵢ) = ∑ μ(aᵢ) ν(bᵢ)` over a disjoint family `bᵢ`, so that `meas_rect` computes the measure of a rectangle. The universal property (`tensor-univ`) says that measure ring homomorphisms `A ⊗ B -> C` correspond to measure bimorphisms `A × B -> C` (`MeasureBimorphism`), the correspondence being `toBimorphism` / `fromBimorphism`; its Boolean-ring analogue is `fromBoolBimorphism`. Homomorphisms out of the tensor product are determined by their values on rectangles (`tensor-hom-ext`). The module also shows that the tensor product of measure rings is again a measure ring when the second factor has a discrete measure (`MeasureRingTensor`), that it preserves rationality of measures (`tensor-rational`), and that it is functorial in both arguments (`tensorMap`, `tensorMap-id`, `tensorMap-comp`, `tensorMap-equiv`).

#### Tensor Product of Boolean Rings

- **`BooleanRingTensor`**: The tensor product `A ⊗ B` of Boolean rings, a `BooleanRing` structure on `SFunc A B` with the additive group of simple functions and multiplication that is `rect a b * rect a' b' = rect (a * a') (b * b')` on rectangles. Its `\where` block contains the auxiliary lemmas (`mulR`, `rmul`, `mul-common`, ...) used to define and verify the multiplication.
- **`rect`**: The rectangle `a ⊗ b`, the simple function with a single term `(a, b)`.
- **`rect_+-left`** / **`rect_+-right`**: Rectangles are additive in each argument: `(a + a') ⊗ b = a ⊗ b + a' ⊗ b` and `a ⊗ (b + b') = a ⊗ b + a ⊗ b'`.
- **`rect_*`**: Rectangles are multiplicative: `(a * a') ⊗ (b * b') = (a ⊗ b) * (a' ⊗ b')`.
- **`rect_zro-left`** / **`rect_zro-right`**: `0 ⊗ b = 0` and `a ⊗ 0 = 0`.

#### Tensor Product of Measure Rings

- **`PseudoMeasureRingTensor`**: The tensor product of pseudo-measure rings: the Boolean ring `BooleanRingTensor A B` with the measure of a simple function computed on a reduced (disjoint) representation as `∑ μ(aᵢ) ν(bᵢ)`. Its `\where` block holds the supporting definitions (`measL`, `meas-reduced`, `merge`, `makeCommon`, `tmeas-modular`, ...).
- **`meas_rect`**: The measure of a rectangle is the product of the measures: `μ(a ⊗ b) = μ(a) ν(b)`.
- **`MeasureRingTensor`**: The tensor product of measure rings is a `MeasureRing` (null elements are zero) provided the measure on the second factor `B` is discrete (`B.IsDiscreteMeasure`).
- **`meas-zero-factor`**: If `μ(a) ν(b) = 0` and `ν(b) > 0` then `μ(a) = 0`.
- **`tensor-rational`**: If the measures of `A` and `B` are rational-valued, so is the measure of `A ⊗ B`.

#### Bimorphisms

- **`BoolBimorphism`**: A Boolean bimorphism `A × B -> C` of Boolean rings: a function `bfunc` additive in each variable and multiplicative on pairs, `bfunc a b * bfunc a' b' = bfunc (a * a') (b * b')`.
- **`BoolBimorphism.bfunc-zro-left`** / **`BoolBimorphism.bfunc-zro-right`**: A Boolean bimorphism vanishes when either argument is zero.
- **`MeasureBimorphism`**: A Boolean bimorphism between pseudo-measure rings that is also multiplicative on measures: `μ(bfunc a b) = μ(a) ν(b)`.
- **`toBimorphism`**: The measure bimorphism `(a, b) => f (a ⊗ b)` associated to a measure ring homomorphism `f` out of the tensor product.
- **`fromBoolBimorphism`**: The ring homomorphism `∑ aᵢ ⊗ bᵢ => ∑ h aᵢ bᵢ` out of `BooleanRingTensor A B` associated to a Boolean bimorphism `h`. Its `\where` block contains the underlying additive map `hom`.
- **`fromBoolBimorphism_rect`**: `fromBoolBimorphism h` sends `a ⊗ b` to `h a b`.
- **`fromBimorphism`**: The measure ring homomorphism out of `PseudoMeasureRingTensor A B` associated to a measure bimorphism.
- **`tensor-univ`**: The universal property of the tensor product: `toBimorphism` and `fromBimorphism` are mutually inverse, so measure ring homomorphisms `A ⊗ B -> C` correspond to measure bimorphisms `A × B -> C`.

#### Extensionality and Measure Preservation

- **`tensor-add-ext`**: Two additive maps out of the tensor product into an additive monoid that agree on all rectangles are equal.
- **`tensor-fun-ext`**: Two functions out of the tensor product that preserve zero and addition and agree on all rectangles are equal.
- **`tensor-hom-ext`**: Two measure ring homomorphisms out of the tensor product that agree on all rectangles are equal.
- **`tensor-meas-scale`**: If a ring homomorphism `h` out of the tensor product into a measure ring satisfies `μ(h (a ⊗ b)) = k μ(a) ν(b)` on rectangles, then `μ(h x) = k μ(x)` for all `x`.
- **`tensor-meas-hom`**: A ring homomorphism out of the tensor product that preserves the measure of rectangles preserves the measure of all elements.

#### Functoriality

- **`tensorBimorphism`**: For measure ring homomorphisms `f : A -> A'` and `g : B -> B'`, the measure bimorphism `(a, b) => f a ⊗ g b` into `A' ⊗ B'`.
- **`tensorMap`**: The tensor product `f ⊗ g : A ⊗ B -> A' ⊗ B'` of two measure ring homomorphisms.
- **`tensorMap-rect`**: `(f ⊗ g)(a ⊗ b) = f a ⊗ g b`.
- **`tensorMap-id`**: The tensor product of identities is the identity.
- **`tensorMap-comp`**: The tensor product of composites is the composite of tensor products.
- **`tensorMap-equiv`**: The tensor product of two isomorphisms of measure rings is an isomorphism.
