### Analysis.Derivative

Directional and total derivatives for maps between topological left modules over a near skew field, with the standard calculus lemmas.

#### Difference Quotient

- **`dquot`**: The difference quotient `h⁻¹ *c (f(x + h *c a) - f(x))`, parameterized over an invertible scalar `h : Inv {R}` and a direction `a : X`, defined for points where `x + h *c a` lies in the domain `S`.
- **`dquot.dquot_*c`**: Rescaling the step: `dquot f h (c *c a) = c *c dquot f (h·c) a`.
- **`dquot.dquot_+`**: Difference quotient is additive in the function: `dquot (f + g) = dquot f + dquot g`.
- **`dquot.dquot_bilinear`**: Leibniz-style rule for the difference quotient of a bilinear pairing `b (f x) (g x)`.
- **`dquot.dquot-comp`**: Chain-rule expression for the difference quotient of a composition `g ∘ (f, Tf)`.

#### Directional Derivative

- **`IsDirDerivAt`**: Proposition that `d : Y` is the directional derivative of `f` at `x` in direction `a`, defined as the limit of `dquot f h _` over the directed set of admissible invertible steps.
- **`IsDirDerivAt.DirSet`**: The `InvDirectedSet` of invertible scalars `h` for which `x + h *c a ∈ S`, used to take the limit.
- **`IsDirDerivAt.limit-id`**: Identifies the canonical map underlying the directed-set limit.
- **`IsDirDerivAt.limit-char`**: ε–δ style characterization: for every open neighborhood `V` of `0` in `Y` there is an open neighborhood `W` of `0` in `R` such that `d - dquot f h a s ∈ V` for all `h ∈ W`, `h` invertible.
- **`IsDirDerivAt.unique`**: When `Y` is Hausdorff, the directional derivative is unique.

#### Directional Derivative Calculus

- **`dirDeriv_zro`**: The directional derivative in direction `0` is `0` (Hausdorff target).
- **`dirDeriv_+`**: Sum rule: `D(f + g)(a) = Df(a) + Dg(a)`.
- **`dirDeriv_bilinear`**: Leibniz rule for a continuous bilinear pairing: `D(b(f, g))(a) = b(Df(a), g(x)) + b(f(x), Dg(a))`, given continuity of `g` at `x` and of `b`.

#### Total Derivative

- **`IsDerivAt`**: Proposition that `Df : X -> Y` is the (total) derivative of `f` at `x`: for every direction `a₀`, the difference quotient converges to `Df a₀` as `(h, a) -> (0, a₀)` jointly.
- **`IsDerivAt.isLimitPoint`**: `(0, a)` is a limit point of admissible `(h, a')` pairs in `R ⨯ X`, justifying the limit.
- **`IsDerivAt.DirSet`**: The sub-pointed directed set on `ProductTopSpace R X` used to define the joint limit.
- **`IsDerivAt.limit-char`**: ε–δ characterization: for every `a₀` and open `V ∋ 0` in `Y`, there exist open `W ∋ 0` in `R` and `U ∋ 0` in `X` such that `Df a₀ - dquot f h a s ∈ V` whenever `h ∈ W`, `a₀ - a ∈ U`, `h` invertible.
- **`IsDerivAt.limit-id`**: Identifies the underlying map for the directed-set limit at a given direction.

#### Total Derivative Properties

- **`deriv-isDirDeriv`**: Total differentiability at `x` implies directional differentiability in every direction `a`, with derivative `Df a`.
- **`deriv-isCont`**: Total differentiability at `x` implies continuity at `x` (relative to the subspace topology on `S`).
- **`deriv-isCont.deriv_zro`**: Auxiliary: `Df 0` lies in every open neighborhood of `0`, i.e. `Df 0 = 0` topologically.
- **`deriv-linear`**: When `Y` is Hausdorff and `Df` is a continuous abelian-group map, `Df` is in fact `R`-linear.
- **`deriv-linear.dirDeriv-*c`**: Scalar-rescaling of directions for directional derivatives: `IsDirDerivAt _ _ _ a d` gives `IsDirDerivAt _ _ _ (c *c a) (c *c d)` for invertible `c`.

#### Total Derivative Calculus

- **`deriv_+`**: Sum rule for total derivatives: `D(f + g) a = Df a + Dg a`.
- **`deriv_bilinear`**: Leibniz rule for a continuous bilinear pairing on total derivatives: `D(b(f, g)) a = b(Df a, g(x)) + b(f(x), Dg a)`.
- **`deriv-comp`**: Chain rule: if `f : S -> Y` lands in an open `T ⊆ Y` with derivative `Df` at `x`, and `g : T -> Z` has derivative `Dg` at `f(x)`, then `g ∘ f` has derivative `Dg ∘ Df` at `x`.
