### Algebra.Module.LinearMap

Linear maps between modules over a ring, together with bilinear, multilinear, and alternating maps.

This module formalizes the basic theory of `R`-linear maps between `LModule R`s as a record extending `AddGroupHom` with the scalar-compatibility law `func-*c`. It develops the matrix representation of a linear map relative to chosen bases (yielding an equivalence between `LinearMap U V` and `Matrix R lu.len lv.len`), the universal property of bases via `extend`/`extendSet`, and pointwise additive structure on linear maps. It also introduces multilinear and alternating maps with their closure under the usual operations, plus tools for analyzing them via linear combinations and permutation actions.

#### Linear Maps

- **`LinearMap`**: Record of `R`-linear maps extending `AddGroupHom`, with `func-*c : func (r *c x) = r *c func x`. Domain and codomain are forced to be `LModule R`.
- **`LinearMap.linearComb`**: Linear maps preserve linear combinations: `func (Σ cⱼ *c lⱼ) = Σ cⱼ *c func (lⱼ)`.
- **`LinearMap.inj->independent`**: Independence reflects along an injective linear map: if `map f l` is independent and `f` is injective, then `l` is independent.
- **`LinearMap.IsIndependentSet_func`**, **`LinearMap.IsIndependentDec_func`**: Independence of a family `g` is implied by independence of `f ∘ g`, in the set-indexed and decidable variants.
- **`LinearMap.IsIndependentSet-left-inj`**: Conversely, an injective linear map preserves set-indexed independence.

#### Identity, Composition, and Universal Properties

- **`LinearMap.id`**: Identity linear map on a module.
- **`LinearMap.compose`** / **`∘`**: Composition of linear maps; `infixl 8`.
- **`LinearMap.extend-unique`**: Two linear maps agreeing on a basis are equal — uniqueness of basis-prescribed extensions.
- **`LinearMap.extend`**: The unique linear map sending each basis element `l j` to `lv j`, defined via `basis-split`.
- **`LinearMap.extend-char`**: Computation rule: `extend lb lv (l j) = lv j`.
- **`LinearMap.basis-ext`**: Two linear maps agreeing on a generating family agree everywhere.
- **`LinearMap.extendSet`**: Set-indexed analogue of `extend` for `IsBasisSet`.
- **`LinearMap.extendSet-char`**: Computation rule for `extendSet` on basis elements.
- **`LinearMap.extendSet-unique`**: Two linear maps agreeing on a generating set agree everywhere.

#### Matrix Representation

- **`LinearMap.toMatrix`**: Matrix of a linear map `f` relative to bases `lu` (domain) and `lv` (codomain), entries given by `V.basis-split bv (f (lu i))`.
- **`LinearMap.toMatrix_ide`**: The identity has identity matrix in any basis.
- **`LinearMap.toMatrix_*`**: Composition corresponds to matrix product: `toMatrix (g ∘ f) = toMatrix f · toMatrix g`.
- **`LinearMap.toLinearMap`**: Inverse construction — builds a linear map from a matrix relative to chosen bases.
- **`LinearMap.toLinearMap-basis`**: Action of `toLinearMap` on a basis vector: `Σ A i j *c lv j`.
- **`LinearMap.toLinearMap_toMatrix-left`**, **`LinearMap.toLinearMap_toMatrix-right`**: Compatibility laws for changing the domain/codomain basis when round-tripping through matrices.
- **`LinearMap.matrix-equiv`**: Equivalence `LinearMap U V ≃ Matrix R lu.len lv.len` induced by chosen bases.
- **`LinearMap.toLinearMap_ide`**: Identity matrix yields the identity linear map.
- **`LinearMap.toLinearMap_*`**: Matrix product corresponds to composition: `toLinearMap (A · B) = toLinearMap B ∘ toLinearMap A`.
- **`LinearMap.change-basis-left`**: Post-composing with `f` corresponds to mapping the codomain basis: `f ∘ toLinearMap bu lv A = toLinearMap bu (map f lv) A`.

#### Pointwise Module Structure on Linear Maps

- **`linearMap_zro`**: The zero map is linear.
- **`linearMap_+`**: Pointwise sum of linear maps is linear.
- **`linearMap_negative`**: Pointwise negation of a linear map is linear.
- **`linearMap_BigSum`**: Finite pointwise sum of linear maps is linear.

#### Linear Maps from Coordinate Modules

- **`arrayLinearMap`**: For `v : Array V`, the linear map `ArrayLModule v.len (RingLModule R) → V` sending coefficient vector `c` to `Σ cⱼ *c vⱼ`.
- **`arrayLinearMap.surj-char`**: `arrayLinearMap v` is surjective iff `v` generates `V`.
- **`arrayLinearMap.inj-char`**: `arrayLinearMap v` is injective iff `v` is independent.
- **`arrayLinearMap.equiv-char`**: `arrayLinearMap v` is an equivalence iff `v` is a basis.

#### Bilinear and Multilinear Maps

- **`BilinearMap`**: Record of bilinear maps `A → B → C` requiring linearity in each argument separately (`linear-left`, `linear-right`).
- **`isMultiLinear`**: Predicate that `f : Array A n → B` is linear in each coordinate, expressed via `insert` of one entry into a fixed surrounding tuple.
- **`isMultiLinear.reduce`**: Fixing the first argument of a multilinear map yields a multilinear map of arity `n`.
- **`isMultiLinear.reduce0`**: Linearity in the head argument as a `LinearMap`.
- **`isMultiLinear.fromReplace`**, **`isMultiLinear.toReplace`**: Equivalent characterization of multilinearity using `replace` instead of `insert`.
- **`isMultiLinear.IsLinearComb`**: Type of linear-combination witnesses: `x = Σ cⱼ *c g (jⱼ)` for some array of `(R, J)` pairs.
- **`isMultiLinear.makeLinearComb`**: Packages an array of coefficient/index pairs into a `Σ`-bundle carrying the resulting linear combination witness.
- **`isMultiLinear.linearComb-map`**: Linear maps push forward linear combinations.
- **`isMultiLinear.linearComb-trans`**: Transitivity of linear combinations: substitute combination expressions into a combination to obtain a flattened combination.
- **`isMultiLinear.linearComb-div`**: Over a commutative ring, divisibility transfers along linear combinations: if `d` divides each `g j`, then `d` divides `x` (truncated).
- **`isMultiLinear.multiLinear-comb`**: Applying a multilinear map to entries that are themselves linear combinations yields a linear combination of values of the map.

#### Closure Properties of Multilinear Maps

- **`isMultilinear_zro`**, **`isMultilinear_+`**, **`isMultilinear_negative`**, **`isMultilinear_BigSum`**: The zero map, sums, negatives, and finite sums of multilinear maps are multilinear.
- **`isMultilinear_linear-left`**: Pre-composing each coordinate by a linear map preserves multilinearity.
- **`isMultilinear_linear-right`**: Post-composing the output by a linear map preserves multilinearity.

#### Alternating Maps

- **`isAlternating`**: Multilinear maps that vanish whenever two distinct entries are equal (with `i < j`).
- **`isAlternating.to/=`**: Vanishing for any pair of equal entries with distinct indices, dropping the `<` requirement.
- **`isAlternating.reduce`**: Fixing the first argument preserves alternatingness.
- **`isAlternating.substract1from0`**, **`isAlternating.add0to1`**: Elementary row-style operations: `f (a :: a' :: l) = f (a - a' :: a' :: l)` and `f (a :: a' :: l) = f (a :: a + a' :: l)`.
- **`isAlternating.alternating_perm`**: Permuting arguments multiplies the value by the sign of the permutation: `f l = sign p *c f l'`.

#### Closure Properties of Alternating Maps

- **`isAlternating_zro`**, **`isAlternating_+`**, **`isAlternating_negative`**: Zero, sums, and negatives of alternating maps are alternating.
- **`isAlternating_linear-left`**, **`isAlternating_linear-right`**: Pre/post-composition with linear maps preserves alternatingness.

#### Uniqueness of Alternating Maps

- **`alternating-unique`**: Two alternating maps that agree on a generating family `l` of `A` agree everywhere — the basis of the determinant uniqueness argument.
- **`alternating-unique.alternating-unique_zro`**: Specialization: if an alternating `f` vanishes on a generating family, it vanishes identically.
- **`alternating-unique.aux`**: Helper showing a multilinear map vanishes on all tuples once it vanishes on every tuple drawn from a generating family.
