### Algebra.Module.LinearMap

Linear maps between modules over a ring, with constructions for matrix representations, multilinear maps, and alternating multilinear maps.

#### Linear Maps

- **`LinearMap`**: Class extending `AddGroupHom` for `R`-linear maps between `LModule R`s; adds `func-*c` proving the underlying function commutes with scalar multiplication.
- **`LinearMap.id`**: The identity linear map `M -> M`.
- **`LinearMap.compose`** (alias **`∘`**): Composition of linear maps `LinearMap V W -> LinearMap U V -> LinearMap U W`.

#### Universal Property of Bases

- **`LinearMap.extend`**: Given a basis `l` of `U` and a target array `lv : Array V`, builds the unique linear map `U -> V` sending `l j` to `lv j` (defined via `basis-split`).
- **`LinearMap.extend-char`**: `extend lb lv (l j) = lv j` — the extension agrees with prescribed values on basis vectors.
- **`LinearMap.extend-unique`**: The pair `(f, p)` of an extending map and its values on the basis is a proposition.
- **`LinearMap.basis-ext`**: Two linear maps agreeing on a generating set are equal pointwise.
- **`LinearMap.extendSet`**: Extension of a function `J -> V` to a linear map `U -> V` along a set-indexed basis `u : J -> U`.
- **`LinearMap.extendSet-char`**: `extendSet ub v (u j) = v j` characterization on basis elements.
- **`LinearMap.extendSet-unique`**: Two linear maps agreeing on a set-indexed generating set are equal.

#### Matrix Representation

- **`LinearMap.toMatrix`**: Given bases of `U` and `V`, converts a `LinearMap U V` to a `Matrix R lu.len lv.len` whose rows are basis-coordinate decompositions of `f (lu i)`.
- **`LinearMap.toLinearMap`**: Inverse direction — turns a matrix into a linear map by extending along the basis.
- **`LinearMap.toMatrix_ide`**: `toMatrix l lb id = 1` — identity map corresponds to the identity matrix.
- **`LinearMap.toMatrix_*`**: `toMatrix (g ∘ f) = toMatrix f * toMatrix g` — composition corresponds to matrix product.
- **`LinearMap.toLinearMap-basis`**: Evaluation of `toLinearMap` on a basis vector equals `BigSum (A i j *c lv j)`.
- **`LinearMap.toLinearMap_toMatrix-left`**, **`toLinearMap_toMatrix-right`**: Compatibility lemmas relating `toLinearMap` and `toMatrix` under change of basis on each side.
- **`LinearMap.matrix-equiv`**: Equivalence `LinearMap U V ≃ Matrix R lu.len lv.len` induced by chosen bases.
- **`LinearMap.toLinearMap_ide`**: `toLinearMap` of the identity matrix is the identity map.
- **`LinearMap.toLinearMap_*`**: `toLinearMap` of a matrix product equals the composition of the corresponding linear maps.
- **`LinearMap.change-basis-left`**: `f ∘ toLinearMap bu lv A = toLinearMap bu (map f lv) A` — composing post-multiplies by applying `f` to the basis.

#### Linear Maps as a Module

- **`linearMap_zro`**: The zero function is a linear map.
- **`linearMap_+`**: Pointwise sum of linear maps is linear.
- **`linearMap_negative`**: Pointwise negation of a linear map is linear.
- **`linearMap_BigSum`**: A finite sum of linear maps is linear.

#### Free Module Maps

- **`arrayLinearMap`**: For `v : Array V`, the linear map `R^n -> V` sending coefficients `c` to `BigSum (c j *c v j)`.
- **`arrayLinearMap.surj-char`**: `arrayLinearMap v` is surjective iff `v` generates `V`.
- **`arrayLinearMap.inj-char`**: `arrayLinearMap v` is injective iff `v` is linearly independent.
- **`arrayLinearMap.equiv-char`**: `arrayLinearMap v` is an equivalence iff `v` is a basis.

#### Bilinear Maps

- **`BilinearMap`**: Class for maps `A -> B -> C` linear in each argument separately (`linear-left`, `linear-right`).

#### Multilinear Maps

- **`isMultiLinear`**: Predicate stating that `f : Array A n -> B` is linear in each coordinate (formulated via `insert`).
- **`isMultiLinear.reduce`**: Fixing the head argument preserves multilinearity in the tail.
- **`isMultiLinear.reduce0`**: Multilinearity gives a linear map in the head argument with the tail fixed.
- **`isMultiLinear.fromReplace`**, **`isMultiLinear.toReplace`**: Equivalent formulation of multilinearity using `replace` instead of `insert`.
- **`isMultiLinear.IsLinearComb`**: Type expressing that `x : U` is a linear combination of `g : J -> U` with explicit coefficient array.
- **`isMultiLinear.makeLinearComb`**: Builds an `IsLinearComb` witness from a coefficient array.
- **`isMultiLinear.linearComb-map`**: A linear map sends a linear combination to a linear combination.
- **`isMultiLinear.linearComb-trans`**: Substituting linear combinations for each generator yields a linear combination in the deeper basis.
- **`isMultiLinear.linearComb-div`**: In a commutative ring, if `d` divides each generator then it divides any linear combination of them.
- **`isMultiLinear.multiLinear-comb`**: Applying a multilinear `f` to arguments each given as a linear combination produces a linear combination of `f` evaluated on combinations of generators.
- **`isMultilinear_zro`**, **`isMultilinear_+`**, **`isMultilinear_negative`**, **`isMultilinear_BigSum`**: Closure of multilinearity under zero, sum, negation, and finite sums.
- **`isMultilinear_linear-left`**: Pre-composing each argument with a linear map preserves multilinearity.
- **`isMultilinear_linear-right`**: Post-composing the result with a linear map preserves multilinearity.

#### Alternating Maps

- **`isAlternating`**: Predicate combining multilinearity with the alternating property: `f` vanishes on arrays with two equal entries at distinct strict-order indices.
- **`isAlternating.to/=`**: Vanishing extends to any pair of distinct (not necessarily ordered) indices with equal entries.
- **`isAlternating.reduce`**: Fixing the head preserves the alternating property on the tail.
- **`isAlternating.substract1from0`**, **`isAlternating.add0to1`**: Row-operation lemmas — subtracting/adding one of the first two arguments from/to the other does not change `f`.
- **`isAlternating.alternating_perm`**: Permuting arguments multiplies the value by the sign: `f l = sign p *c f l'`.
- **`isAlternating_zro`**, **`isAlternating_+`**, **`isAlternating_negative`**: Closure of the alternating property under zero, sum, and negation.
- **`isAlternating_linear-left`**, **`isAlternating_linear-right`**: Pre/post-composition with linear maps preserves alternating-ness.
- **`alternating-unique`**: Two alternating multilinear maps that agree on one generating array agree everywhere.
- **`alternating-unique.alternating-unique_zro`**: Specialization: an alternating map vanishing on a generating array vanishes everywhere.
- **`alternating-unique.aux`**: Helper showing a multilinear map vanishing on all index-tuples drawn from a generating array vanishes everywhere.
