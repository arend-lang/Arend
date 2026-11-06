### Topology.BanachSpace

Banach spaces and their precursors over rational-valued normed structures, culminating in real Banach spaces and bounded linear maps.

This module builds Banach spaces in stages: first `PreBanachSpace`, a divisible group equipped with a bounded extended pseudo-norm that scales compatibly with natural-number multiplication via a `RatValue` valuation `A`; then `SeparatedPreBanachSpace`, which adds Hausdorff separation and a full `QModule` (rational scalar multiplication) structure; and finally `BanachSpace` adds completeness. The real-valued variant `RealPreBanachSpace` strengthens the norm axiom to `norm-double` (subadditivity for doubling), which is enough to derive scalar compatibility for the discrete rational multiplication, and `RealBanachSpace` extends scalars from `Rat` to `Real` by completing along the dense embedding of rationals into reals. `BoundedLinearMap` packages real-Banach uniform maps satisfying a quantitative bound `‖f x‖ ≤ C·‖x‖`, showing this characterization is equivalent to uniform continuity for the norm.

#### Core Classes

- **`PreBanachSpace`**: Extends `BoundedExPseudoNormedAbGroup` and `DivisibleGroup`, parameterized by a `RatValue` `A`. Requires `norm_*n` (norm scales by `A.norm n` under natural multiplication) and derives `norm-bounded` from divisibility plus norm of zero.
- **`SeparatedPreBanachSpace`**: Extends `PreBanachSpace`, `ExNormedAbGroup`, and `QModule`. Adds the no-torsion property derived from norm separation and provides `norm_*q` for rational scalar multiplication.
- **`BanachSpace`**: Extends `SeparatedPreBanachSpace` and `CompleteExNormedAbGroup` — a separated, complete pre-Banach space.
- **`RealPreBanachSpace`**: A `PreBanachSpace` over `RatValuedRing` (absolute-value valuation) satisfying `norm-double`: `‖x‖ + ‖x‖ ≤ ‖x + x‖`. The `norm_*n` law is derived from `norm-double` via dyadic powers.
- **`RealBanachSpace`**: Extends `RealPreBanachSpace` and `BanachSpace`. Lifts rational scalar multiplication to real scalar multiplication using density of rationals.

#### Norm-Multiplication Lemmas

- **`norm_*n`** (in `PreBanachSpace`): `norm (n *n x) = A.norm n * norm x` for `n : Nat`.
- **`norm_*i`** (in `PreBanachSpace`): Same identity extended to integers.
- **`norm_*q`** (in `SeparatedPreBanachSpace`): Same identity extended to rationals.
- **`norm-double`** (in `RealPreBanachSpace`): The doubling subadditivity axiom `‖x‖ + ‖x‖ ≤ ‖x + x‖`.
- **`RealPreBanachSpace.steps`**: Helper showing `2^k *n norm x = norm (2^k *n x)`, the inductive lift of `norm-double` over dyadic powers.

#### Scalar Multiplication Maps

- **`*q-right-uniform`**: For a `SeparatedPreBanachSpace` `X` and rational `q`, the map `q *q —` is a uniform normed-group map `X → X`.
- **`*q-uniform`**: The pairing `(q, x) ↦ q *q x` is a locally uniform map `A ⨯ X → X`.

#### Real Scalar Multiplication (in `RealBanachSpace`)

- **`*r`**: Real scalar multiplication `Real → E → E`, defined via dense lifting from `*q-uniform` along the rational-to-real embedding.
- **`*r-cover`**: Cover-map continuity of `(r, a) ↦ r *r a` from `RealNormed ⨯ X`.
- **`*r_*q`**: Compatibility `q *r a = q *q a` for rational `q`.
- **`*r_*n`**: Compatibility `n *r a = n *n a` for natural `n`.
- **`norm_*r`**: `norm (r *r a) = |r| * norm a` for real `r`.
- **`norm_*q-ofPos`**: `norm (q *q x) = q * norm x` when `q ≥ 0`.
- **`norm_*n-comm`**: `norm (n *n x) = n *n norm x` reformulated using `*n` on extended uppers.
- **`toRealModule`**: Packages a `RealBanachSpace` as an `LModule` over `RealField` via `*r`.

#### Bounded Linear Maps

- **`BoundedLinearMap`**: Record extending `UniformNormedAbGroupMap` between `RealBanachSpace`s. Requires `isBounded`: existence of `C > 0` with `‖f x‖ ≤ C · ‖x‖` for all `x`. The proof shows uniform continuity (`func-norm-uniform`) and the bound condition are mutually derivable.

#### Reflections and Completions

- **`SeparatedBanachReflection`**: Constructs a `SeparatedPreBanachSpace` over `A` from a `PreBanachSpace` over `A` by quotienting via `SeparatedNormedAbGroupReflection`.
- **`BanachCompletion`**: Constructs a `BanachSpace` over `A` from a `PreBanachSpace` over `A` by completing via `ExNormedAbGroupCompletion`.
