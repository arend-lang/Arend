### Equiv.Sigma

Equivalences for sigma types, dependent function types, and related contractibility results.

This module collects standard equivalences used to manipulate sigma and pi types up to equivalence: contracting away a contractible component, transporting an equivalence through one factor, and combining equivalences on both factors. The key technique is exploiting that singleton sigmas `(Σ x, a₀ = x)` and `(Σ x, x = a₀)` are contractible (the J-rule packaged as an equivalence), which lets us reduce many fibered statements to their fiberwise content. These building blocks underlie fiber-wise reasoning, the characterization of fiberwise equivalences via the total map (`totalEquiv`), and rewriting of dependent types along base equivalences.

#### Contractibility of Singletons

- **`lsigma`**: `Contr (Σ (x : A) (a0 = x))` — the right-based path space at `a0` is contractible, with `(a0, idp)` as center.
- **`rsigma`**: `Contr (Σ (x : A) (x = a0))` — dual: the left-based path space at `a0` is contractible.

#### Contracting a Sigma Component

- **`contr-left`**: If `A` is contractible, then `Σ (x : A) (B x) ≃ B c.center` — the base may be contracted away.
- **`contr-right`**: If each fiber `C a` is contractible, then `Σ (x : A) (C x) ≃ A` — the fiber may be contracted away, leaving the base.
- **`contr-sigma-fiber`**: If the total space `Σ (x : A) (B x)` is contractible, then each fiber `B a` is equivalent to the path type `c.center.1 = a`.
- **`contr-point-equiv`**: For contractible `C` and a family `F` over it, inserting `z : F c0` at any point `c0` gives an equivalence `F c0 -> Σ (x : C) (F x)`.

#### Pi over Singleton Path Spaces

- **`pi-contr-left`**: `(Π (a' : A) (p : a = a') -> B a' p) ≃ B a idp`, the dependent-function form of the J-rule.
- **`pi-contr-right`**: Dual variant: `(Π (a : A) (p : a = a') -> B a p) ≃ B a' idp`, using right-based J (`Jr`).

#### Sigma Transport Along Equivalences

- **`sigma-left`**: Given a half-adjoint equivalence `e : A ≃ A'`, transports a sigma along the base: `Σ (a : A) (B' (e a)) ≃ Σ (a' : A') (B' a')`. Uses the half-adjoint coherence `f_ret_f=f_sec_f` to construct the round-trip path.
  - **`sigma-left.path-func`**: A path-level version: from `p : A = A'`, an equality `Σ (x : A) (B' (coe p x right)) = Σ (x' : A') (B' x')` of sigma types.
- **`sigma-right`**: Given fiberwise equivalences `q a : B a ≃ B' a`, lifts to `Σ (a : A) (B a) ≃ Σ (a : A) (B' a)`.
- **`sigma-equiv`**: Combines the two: from `e1 : A ≃ A'` and `e2 a : B a ≃ B' (e1 a)`, builds `Σ (a : A) (B a) ≃ Σ (a' : A') (B' a')`.
- **`qsigma-left`**: Variant of `sigma-left` for a quasi-inverse equivalence `e : QEquiv`, with the forward map explicitly `(a, b) ↦ (e a, b)`.
- **`IsSigma-right`**: If each `f a : B a -> B' a` is an equivalence, then the map `(a, b) ↦ (a, f a b)` on sigma types is an equivalence.
- **`sigma-prop-equiv`**: An equivalence `f` that preserves and reflects a family of propositions (`Q a -> R (f a)` and back) restricts to an equivalence `Σ (a : A) (Q a) -> Σ (a' : A') (R a')`.
- **`subtype-equiv`**: Same for subtypes of `\Prop`-valued families `P`, `Q` over an equivalence `g`, with `h` and `r` preserving and reflecting membership.
- **`pair-equiv`**: If `g` is an equivalence, then so is `(x, y) ↦ (g x, g y)` on `Σ X X`.
- **`sigma-prop-ext`**: Two pairs in a sigma type with propositional second component are equal as soon as their first components are.

#### Miscellaneous

- **`unit-isContr`**: The empty tuple type `\Sigma` is contractible, with center `()` and the trivial contraction `\lam _ => idp`.
- **`unit-func`**: `(Σ -> A) ≃ A` — functions out of the unit type are just elements of `A`.

#### Total Equivalences and Fibers

- **`totalEquiv`**: For families `A, B : J -> Type` and a fiberwise map `f j : A j -> B j`, each `f j` is an equivalence if and only if the total map `(j, a) ↦ (j, f j a)` is an equivalence (HoTT book, Theorem 4.7.7).
  - **`totalEquiv.total`**: The induced total map `Σ (j : J) (A j) -> Σ (j : J) (B j)`.
  - **`totalEquiv.totalFiber`**: Equivalence of fibers: `Fib (total f) (j, b) ≃ Fib (f j) b`, built by characterizing equality of pairs (via `sigmaEquiv`) and contracting the resulting singleton path space (via `rsigma`).
- **`contr-transport-equiv`**: The fundamental theorem of identity types: if `Σ (a : A) (B a)` is contractible, then for `b0 : B a0` transport `p ↦ transport B p b0` is an equivalence `a0 = a -> B a`.
- **`contr-relation-equiv`**: A relation `R` such that every `Σ (y : Y) (R x y)` and every `Σ (x : X) (R x y)` is contractible is the graph of an equivalence: `x ↦ (cx x).center.1` is an equivalence.
- **`fiberwiseEquiv`**: Given an equivalence `g` of bases, a map `h x : P x -> Q (g x)` is an equivalence for every `x` as soon as the induced map of total spaces is.
- **`fib-parallel-equiv`**: For a square with equivalences `ac`, `bd` and `h : bd (ab a) = cd (ac a)`, the fibers `Fib ab b` and `Fib cd (bd b)` are equivalent.
- **`sigma-fib-equiv`**: For a fiberwise map `T2` over `T1`, the fiber of the total map `(x, y) ↦ (T1 x, T2 x y)` over `(x', y')` is equivalent to the fiber `Fib T1 x'`, provided the fibers of `T2` are contractible where a predicate `P` holds and `P x' y'` holds.

#### Pullbacks

- **`pullback-proj-left`**: If `g` is an equivalence, the projection of the pullback `Σ (a : A) (b : B) (f a = g b)` to `A` is an equivalence.
- **`pullback-proj-right`**: If `f` is an equivalence, the projection of the pullback to `B` is an equivalence.
- **`pullback-equiv`**: Pullbacks of cospans `A -> C <- B` and `A' -> C' <- B'` related by equivalences `eA`, `eB` and an embedding `eC` (with `pmap eC` an equivalence) of the corners along commuting squares `sf`, `sg` are equivalent.
- **`pullback-sigma-equiv`**: Equivalences of pullbacks of types along commuting squares, with `fa`, `fb`, `fc` all equivalences and commutations `su`, `sv`.
