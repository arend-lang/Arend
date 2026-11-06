### Analysis.Calculus.SyntheticDerivative

Synthetic differential calculus over a commutative ring `R` with a notion of "open" propositions, where derivatives are characterized by an algebraic remainder condition rather than limits.

This module formalizes differentiation in the style of synthetic differential geometry, replacing analytic limits with the algebraic property that `f(x + t·a) - f(x) = t·y` for some unique `y` (the directional derivative). The key structure is the class `SyntheticDiff`, which fixes a commutative ring `R` and a class of open propositions `isOpenProp` (containing the trivial proposition and closed under conjunction). A module `B` is "inverse-dense" (`IsInvDense`) when two functions on an open subset of `R` that agree at all invertible points agree everywhere; this lets one cancel `t` whenever the remainder equation holds. From this, derivatives become honest linear maps, and the standard calculus rules — linearity, composition (chain rule), and the Leibniz rule for bilinear maps — are derived purely algebraically. The predicate `U` on the domain (required to be open pointwise, `IsOpenPred`) allows differentiation on subsets, supporting partial functions.

#### Open Propositions and Inverse Density

- **`SyntheticDiff`**: Parameters (`R : CRing`) and axioms for a synthetic differential setting: a predicate `isOpenProp` on propositions that holds for the trivial proposition (`top-isOpenProp`) and is closed under conjunction (`meet-isOpenProp`).
- **`SyntheticDiff.meet3-isOpenProp`**: Closure of `isOpenProp` under conjunction of three propositions.
- **`SyntheticDiff.IsOpenPred`**: A predicate `U : A -> \Prop` is open when `isOpenProp (U a)` holds for every `a`.
- **`SyntheticDiff.IsInvDense`**: Inverse-density of a set `B`: for an open predicate `U` on `R`, two functions `\Sigma (z : R) (U z) -> B` that agree at all invertible points agree everywhere.
- **`SyntheticDiff.cancel-lem`**: Cancellation lemma for an inverse-dense module `B` — if `p.1 *c f p = p.1 *c g p` for all elements `p` of an open subset of `R`, then `f = g`. Uses `IsInvDense` to extend cancellation from invertibles to the whole subset.

#### Differentiability Predicate

- **`SyntheticDiff.IsDiff`**: `f` is differentiable on the open predicate `U`: for every base point `x`, direction `a` and `t` with `x + t·a` in `U`, there is `y` with `t *c y = f (x + t·a) - f x`.
- **`SyntheticDiff.IsDiff.levelProp`**: `IsDiff Bd Uo f` is a proposition — derivatives are unique when they exist, via `cancel-lem`.
- **`SyntheticDiff.IsDiffT`**: Differentiability of a total function `f : A -> B`, i.e. `IsDiff` on the trivial (everywhere true) open predicate.

#### Derivative as Linear Map

- **`SyntheticDiff.deriv`**: Extracts the derivative of a differentiable `f` at a point `x` as a `LinearMap A B`. The underlying function takes a direction `a` to the witness `y` produced by `IsDiff` at `t = 0`. Linearity (`func-+`, `func-*c`) follows from uniqueness of remainders.
- **`SyntheticDiff.deriv.sub-lem`**: Shows `U (x.1 + 0 *c a)` holds, used to evaluate the derivative at the base point.

#### Calculus Rules

- **`SyntheticDiff.linear_diff`**: Linear maps are differentiable, with derivative equal to themselves: `f a` is the witness, since `f(x + t·a) - f x = t · f a`.
- **`SyntheticDiff.const_diff`**: Constant functions are differentiable with derivative `0`.
- **`SyntheticDiff.+_diff`**: Sum rule — if `f` and `g` are differentiable, so is `f + g`, with derivatives adding pointwise.
- **`SyntheticDiff.o_diff`**: Chain rule — composition `g ∘ f` is differentiable when both `f` and `g` are; the derivative of `f` shifts the base point of `g`'s derivative.
- **`SyntheticDiff.bilinear_diff`**: Leibniz rule for bilinear maps — `m : A × B → C` is differentiable with directional derivative `m x.1 a.2 + m a.1 x.2 + t · m a.1 a.2`, recovering the product rule when `m` is multiplication.
