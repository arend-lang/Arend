### Analysis.Derivative

Directional and total derivatives for maps between topological left modules over a near-skew field.

This module formalizes differentiation in a general functional-analytic setting: rather than working over the reals, derivatives are defined for maps `f : Set.Total S -> Y` between topological left modules over a `NearSkewField R`, where `S ⊆ X` is open. The central construction is the *difference quotient* `dquot`, which uses an invertible scalar `h : Inv {R}` to form `h⁻¹ *c (f(x + h·a) - f x)`. Directional derivatives `IsDirDerivAt` are defined as limits of this quotient over a directed set of invertible scalars approaching zero, while the total derivative `IsDerivAt` requires a uniform limit jointly in the scalar `h` and the direction `a`. The structure mirrors classical calculus (linearity, Leibniz rule, chain rule), but parametrizes everything over arbitrary topological modules so the same theory specializes to real, complex, or non-archimedean analysis.

#### Difference Quotient

- **`dquot`**: The fundamental difference quotient `h⁻¹ *c (f(x + h·a) - f x)` for `f : Set.Total S -> Y`, an invertible scalar `h`, and direction `a`; carries a side condition `S(x + h·a)` ensuring the perturbed point lies in the domain.
- **`dquot.dquot_*c`**: Scaling law: `dquot f h (c·a) = c *c dquot f (h·c) a`, relating rescaling of the direction to rescaling of the increment.
- **`dquot.dquot_+`**: Additivity of the difference quotient over pointwise sums of functions.
- **`dquot.dquot_bilinear`**: Leibniz-style expansion of `dquot` for `b(f x, g x)` with a bilinear map `b`, splitting into two terms in the spirit of the product rule.
- **`dquot.dquot-comp`**: Chain-rule expansion of `dquot` for a composition `g ∘ f`, expressing it as `dquot g` evaluated at `dquot f h a`.

#### Directional Derivative

- **`IsDirDerivAt`**: Predicate stating that `d : Y` is the directional derivative of `f` at `x` in direction `a`, defined as the limit of `dquot f h a` along the directed set of nonzero invertible scalars.
- **`IsDirDerivAt.DirSet`**: The directed set of admissible scalars `h` (invertible and with `x + h·a ∈ S`), built via `InvDirectedSet`.
- **`IsDirDerivAt.limit-char`**: Epsilon–delta-style characterization: for every open neighborhood `V` of `0` in `Y` there is an open neighborhood `W` of `0` in `R` such that `V (d - dquot f h a s)` for all invertible `h ∈ W`.
- **`IsDirDerivAt.limit-id`**: Auxiliary identification of the limit point used to set up `DirSet`.
- **`IsDirDerivAt.unique`**: Uniqueness of the directional derivative when `Y` is Hausdorff.

#### Calculus Rules for Directional Derivatives

- **`dirDeriv_zro`**: The directional derivative in direction `0` is `0` (for Hausdorff `Y`).
- **`dirDeriv_+`**: Additivity: `IsDirDerivAt` of `f + g` is `Df + Dg`.
- **`dirDeriv_bilinear`**: Leibniz rule for bilinear maps: if `b` is continuous and bilinear, the directional derivative of `b(f, g)` is `b(Df, g x) + b(f x, Dg)`, requiring continuity of `g` at `x`.

#### Total Derivative

- **`IsDerivAt`**: Predicate stating that `Df : X -> Y` is the (Fréchet-style) total derivative of `f` at `x`, defined as a joint limit over scalar `h` and direction `a` simultaneously.
- **`IsDerivAt.isLimitPoint`**: Shows `(0, a)` is a limit point of the set of admissible `(h, a')` pairs in the product topology, justifying the joint-limit construction.
- **`IsDerivAt.DirSet`**: The joint directed set of pairs `(h, a')` with `h` invertible and `x + h·a' ∈ S`, built via `SubPointDirectedSet` on the product topology.
- **`IsDerivAt.limit-char`**: Joint epsilon–delta characterization quantifying simultaneously over neighborhoods of `0` in `R` and of `a₀` in `X`.
- **`IsDerivAt.limit-id`**: Auxiliary identification of the limit point for the joint directed set.

#### Properties of the Total Derivative

- **`deriv-isDirDeriv`**: A total derivative restricts to a directional derivative in every direction: `IsDerivAt So f x Df` implies `IsDirDerivAt So f x a (Df a)`.
- **`deriv-isCont`**: Differentiability implies continuity at the point: `IsDerivAt` entails `IsContAt {TopSub S} f x`.
- **`deriv-isCont.deriv_zro`**: Auxiliary: `Df 0` lies in every open neighborhood of `0`, used in the continuity proof.
- **`deriv-linear`**: When `Df` is given as a `TopAbGroupMap` and `Y` is Hausdorff, the derivative is automatically `R`-linear, packaging it as a `LinearMap X Y`.
- **`deriv-linear.dirDeriv-*c`**: Scaling of directional derivatives: `IsDirDerivAt` at `a` with value `d` implies `IsDirDerivAt` at `c·a` with value `c·d`, for invertible `c`.

#### Calculus Rules for Total Derivatives

- **`deriv_+`**: Additivity of total derivatives: `IsDerivAt` of `f + g` is `λa. Df a + Dg a`.
- **`deriv_bilinear`**: Total-derivative Leibniz rule: for a continuous bilinear `b`, the derivative of `b(f, g)` at `x` is `λa. b(Df a, g x) + b(f x, Dg a)`.
- **`deriv-comp`**: Chain rule: if `f` is differentiable at `x` with derivative `Df` and `g` is differentiable at `f x` with derivative `Dg`, then `g ∘ f` is differentiable at `x` with derivative `λa. Dg (Df a)`.
