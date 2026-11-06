### Analysis.Measure.Lebesgue

Lebesgue measure rings and the Lebesgue integral `L¹(ℝⁿ)`, built from the ring of rational intervals by tensor products and completion.

The ring `B₀⁽¹⁾` of finite unions of rational intervals is `IntervalRing` (from `Analysis.Measure.IntervalRing`); it exists only in dimension one. Higher dimensions come from the tensor product of measure rings: `LebesgueRing0 n` is `B₀⁽ⁿ⁾`, with `B₀⁽⁰⁾ = 2` (`UnitMeasureRing`) and `B₀⁽ⁿ⁺¹⁾ = B₀⁽ⁿ⁾ ⊗ B₀⁽¹⁾`. The Lebesgue measure ring `λₙ` is its completion, `LebesgueMeasureRing n`, and the Lebesgue integral space `L¹(ℝⁿ)` is `LebesgueL1 n`, the completion of the simple-function integration space over `B₀⁽ⁿ⁾`.

The module then identifies `B₀⁽ⁿ⁾` concretely. The map `lebPts` sends an element to its set of rational points in `Rat^n` (`∑ᵢ aᵢ ⊗ cᵢ ↦ ⋃ᵢ aᵢ × cᵢ`); it is injective, its image is the finite unions of half-open rational boxes, and the measure of a box is its volume (`boxElem-meas`). It also shows `B₀⁽ᵐ⁾ ⊗ B₀⁽ⁿ⁾ ≅ B₀⁽ᵐ⁺ⁿ⁾` (`lebesgueTensor-equiv`), its completed form `λₘ ⊠ λₙ ≅ λₘ₊ₙ` (`lebesgueBoxtimes-equiv`), and `λₙ ≅ λ₁^⊠ⁿ` (`lebesgue-pow-equiv`), so the Lebesgue integral is determined by the line `λ₁` and the product of measure rings.

#### Definition of the Lebesgue rings and integral

- **`LebesgueRing0`**: `B₀⁽ⁿ⁾` as a `MeasureRing`, defined recursively: `UnitMeasureRing` for `n = 0`, and the tensor of `LebesgueRing0 n` with `IntervalRing` for `n + 1`.
- **`IntervalRing-equiv`**: The unit map `unitL` of `IntervalRing` is an equivalence: `IntervalRing` agrees with `B₀⁽¹⁾` as defined by the recursion.
- **`LebesgueMeasureRing`**: The Lebesgue measure ring `λₙ`, the `MeasureRingCompletion` of `LebesgueRing0 n`.
- **`LebesgueL1`**: The Lebesgue integral `L¹(ℝⁿ)`, the integration space completion of `SFuncIntegrationSpace (LebesgueRing0 n)`.
- **`LebesgueL1-equiv`**: `L¹(ℝⁿ) ≅ L¹(λₙ)`: the map of integration completions induced by `measureCompletion` on simple functions is an equivalence.
- **`LebesgueRing0-rational`**: Each `LebesgueRing0 n` has rational measure values (`IsRationalMeasure`).

#### Tensor decomposition

- **`lebesgueTensor`**: The homomorphism from the tensor `PseudoMeasureRingTensor (LebesgueRing0 m) (LebesgueRing0 n)` to `LebesgueRing0 (m + n)`.
- **`lebesgueTensor-equiv`**: `B₀⁽ᵐ⁾ ⊗ B₀⁽ⁿ⁾ ≅ B₀⁽ᵐ⁺ⁿ⁾`: `lebesgueTensor` is an equivalence.
- **`lebesgueTensor-rect`**: `lebesgueTensor` sends a rectangle `a ⊗ b` to the product of point sets: a concatenated point `x ++' y` lies in the image iff `x` lies in `a` and `y` lies in `b`.
- **`lebesgueBoxtimes`**: The homomorphism `λₘ ⊠ λₙ -> λₘ₊ₙ` from the completed tensor `CMeasureRingTensor` of Lebesgue measure rings, induced by `lebesgueTensor`.
- **`lebesgueBoxtimes-rect`**: `lebesgueBoxtimes` sends the rectangle of the completions of `a` and `b` to the completion of the image of `a ⊗ b` under `lebesgueTensor`.
- **`lebesgueBoxtimes-equiv`**: `λₘ₊ₙ ≅ λₘ ⊠ λₙ`: `lebesgueBoxtimes` is an equivalence.

#### Points of an element

- **`PointRing`**: The Boolean ring of all subsets of `X`, as `Bool`-valued predicates, with symmetric difference and intersection.
- **`initA`** / **`lastA`**: The first `n` coordinates, and the last coordinate, of a point of `A^(n+1)`.
- **`lebPts`**: The ring homomorphism from `LebesgueRing0 n` to `PointRing (Array Rat n)` giving the rational points of an element: `∑ᵢ aᵢ ⊗ cᵢ ↦ ⋃ᵢ aᵢ × cᵢ`.
- **`lebPts-rect`**: The points of a rectangle `a ⊗ c` of `B₀⁽ⁿ⁾ ⊗ B₀⁽¹⁾` are the points whose initial part lies in `a` and whose last coordinate lies in `c`.
- **`lebPts-zro`** / **`lebPts-join`**: The zero element has no points, and the points of a join are the union of the points.
- **`lebPts-kernel`**: Only `0` has no points.
- **`lebPts-inj`**: Elements of `B₀⁽ⁿ⁾` with the same points are equal.
- **`lebPts-image`**: Every element of `B₀⁽ⁿ⁾` has the points of a finite union of boxes.

#### Boxes

- **`RatBox`**: A box `∏ᵢ [pᵢ,qᵢ)` in `Rat^n`, an array of `n` rational intervals.
- **`inRatBox`**: Boolean membership of a point in a box.
- **`inRatBoxes`**: Boolean membership of a point in the union of a finite list of boxes.
- **`ratVolume`**: The volume `∏ᵢ (qᵢ - pᵢ)` of a box (zero if it is empty).
- **`ratBoxElem`**: The element of `B₀⁽ⁿ⁾` corresponding to a box.
- **`ratBoxUnion`**: The element of `B₀⁽ⁿ⁾` corresponding to a finite union of boxes.
- **`lebPts-box`** / **`lebPts-boxes`**: The points of `ratBoxElem n b` (resp. `ratBoxUnion l`) are those given by `inRatBox b` (resp. `inRatBoxes l`).
- **`boxElem-meas`**: The measure of the element of a box is its volume.
- **`lebPts-volume`**: If an element has the points of a box `b`, its measure is the volume of `b`.
- **`RatBoxUnion`**: The ring of finite unions of rational boxes in `Rat^n`, as a subset of the predicates on `Rat^n`.
- **`toRatBoxUnion`**: The map from `B₀⁽ⁿ⁾` to `RatBoxUnion n` taking an element to its points.
- **`toRatBoxUnion-equiv`**: `B₀⁽ⁿ⁾` is isomorphic to the ring of finite unions of half-open rational boxes in `Rat^n`.

#### Powers of the line

- **`LebesgueLine`**: The Lebesgue measure ring of the line, the `MeasureRingCompletion` of `IntervalRing`.
- **`LebesgueLine-equiv`**: `LebesgueLine ≅ λ₁`: the map `measureTensorUnit` for `IntervalRing` is an equivalence.
- **`LebesgueLinePowT`**: The uncompleted power of the line: `UnitMeasureRing` for `n = 0`, and the tensor of the completion of the previous power with `LebesgueLine` for `n + 1`.
- **`LebesgueLinePow`**: The power `λ₁^⊠ⁿ`, the completion of `LebesgueLinePowT n`.
- **`lebesguePowMap`**: The homomorphism `B₀⁽ⁿ⁾ -> LebesgueLinePowT n` before completion.
- **`lebesguePowMap-dense`**: `lebesguePowMap n` has dense image.
- **`lebesgue-pow-equiv`**: `λₙ ≅ λ₁^⊠ⁿ`: the induced map of completions is an equivalence.
- **`LebesgueL1-pow-equiv`**: `L¹(ℝⁿ) ≅ L¹(λ₁^⊠ⁿ)`: the Lebesgue integral is determined by `λ₁` and the product.
