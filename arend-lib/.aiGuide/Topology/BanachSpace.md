### Topology.BanachSpace

Banach spaces: complete normed abelian groups equipped with rational-valued (or real-valued) scalar multiplication, together with bounded linear maps between them.

#### Pre-Banach Spaces

- **`PreBanachSpace`**: Extends `BoundedExPseudoNormedAbGroup` and `DivisibleGroup`. A divisible group with a bounded extended pseudo-norm valued in a `RatValue` `A`, satisfying `norm (n *n x) = A.norm n * norm x`. Boundedness of the norm follows automatically from divisibility.
- **`SeparatedPreBanachSpace`**: Extends `PreBanachSpace`, `ExNormedAbGroup`, and `QModule`. A separated (Hausdorff) pre-Banach space; the separation axiom plus divisibility yields a torsion-free `QModule` structure over the rationals.
- **`BanachSpace`**: Extends `SeparatedPreBanachSpace` and `CompleteExNormedAbGroup`. A complete separated pre-Banach space — the standard notion of a Banach space over `ℚ`-scalars valued in `A`.

#### Real Pre-Banach and Banach Spaces

- **`RealPreBanachSpace`**: Extends `PreBanachSpace` with `A := RatValuedRing`. Specializes to real-valued norms via the extra axiom `norm-double : norm x + norm x <= norm (x + x)`, which together with the doubling argument forces `norm_*n` to be exact (not merely bounded by absolute value).
- **`RealPreBanachSpace.steps`**: Lemma that for any `ExPseudoNormedAbGroup` satisfying the doubling inequality, `2^k *n norm x = norm (2^k *n x)` — the inductive step underlying the proof of `norm_*n` for `RealPreBanachSpace`.
- **`RealBanachSpace`**: Extends `RealPreBanachSpace` and `BanachSpace`. A complete real Banach space.

#### Scalar Multiplication Continuity

- **`*q-right-uniform`**: For a fixed rational `q`, the map `x ↦ q *q x : X → X` is a uniform map of normed abelian groups on any `SeparatedPreBanachSpace X`.
- **`*q-uniform`**: The full scalar multiplication `(q, x) ↦ q *q x : A ⨯ X → X` is a locally uniform map.

#### Bounded Linear Maps

- **`BoundedLinearMap`**: Extends `UniformNormedAbGroupMap` with `Dom`, `Cod : RealBanachSpace`. Adds `isBounded`: existence of a positive rational constant `C` with `norm (func x) <= C * norm x` for all `x`. Provides a two-way derivation showing that boundedness is equivalent to uniform continuity of the norm pullback, so either field can be supplied to instantiate the class.

#### Reflections and Completions

- **`SeparatedBanachReflection`**: Instance constructing a `SeparatedPreBanachSpace` from any `PreBanachSpace X` via the separated normed reflection — the universal Hausdorff quotient preserving the pre-Banach structure.
- **`BanachCompletion`**: Instance constructing a `BanachSpace` from any `PreBanachSpace X` by taking the extended-normed-abelian-group completion — the universal complete Banach space over `X`.
