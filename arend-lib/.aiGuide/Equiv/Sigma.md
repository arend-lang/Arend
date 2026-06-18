### Equiv.Sigma

Equivalences for sigma types, dependent function types, and related contractibility results.

This module collects standard equivalences used to manipulate sigma and pi types up to equivalence: contracting away a contractible component, transporting an equivalence through one factor, and combining equivalences on both factors. The key technique is exploiting that singleton sigmas `(Σ x, a₀ = x)` and `(Σ x, x = a₀)` are contractible (the J-rule packaged as an equivalence), which lets us reduce many fibered statements to their fiberwise content. These building blocks underlie fiber-wise reasoning, the characterization of equivalences via fibers (`totalEquiv`), and rewriting of dependent types along base equivalences.

#### Contractibility of Singletons

- **`lsigma`**: `Contr (Σ (x : A) (a0 = x))` — the right-based path space at `a0` is contractible, with `(a0, idp)` as center.
- **`rsigma`**: `Contr (Σ (x : A) (x = a0))` — dual: the left-based path space at `a0` is contractible.

#### Univalence Helper

- **`equiv=`**: Turns a quasi-equivalence `e : QEquiv` into a path `e.A = e.B` via `iso`.

#### Contracting a Sigma Component

- **`contr-left`**: If `A` is contractible, then `Σ (x : A) (B x) ≃ B c.center` — the base may be contracted away.
- **`contr-right`**: If each fiber `C a` is contractible, then `Σ (x : A) (C x) ≃ A` — the fiber may be contracted away, leaving the base.

#### Pi over Singleton Path Spaces

- **`pi-contr-left`**: `(Π (a' : A) (p : a = a') -> B a' p) ≃ B a idp`, the dependent-function form of the J-rule.
- **`pi-contr-right`**: Dual variant: `(Π (a : A) (p : a = a') -> B a p) ≃ B a' idp`, using right-based J (`Jr`).

#### Sigma Transport Along Equivalences

- **`sigma-left`**: Given a half-adjoint equivalence `e : A ≃ A'`, transports a sigma along the base: `Σ (a : A) (B' (e a)) ≃ Σ (a' : A') (B' a')`. Uses the half-adjoint coherence `f_ret_f=f_sec_f` to construct the round-trip path.
  - **`sigma-left.path-func`**: A path-level version: when `A = A'`, derives the corresponding equality of sigma types.
- **`sigma-right`**: Given fiberwise equivalences `q a : B a ≃ B' a`, lifts to `Σ (a : A) (B a) ≃ Σ (a : A) (B' a)`.
- **`sigma-equiv`**: Combines the two: from `e1 : A ≃ A'` and `e2 a : B a ≃ B' (e1 a)`, builds `Σ (a : A) (B a) ≃ Σ (a' : A') (B' a')`.

#### Miscellaneous

- **`unit-func`**: `(Σ -> A) ≃ A` — functions out of the unit type are just elements of `A`.

#### Total Equivalences and Fibers

- **`totalEquiv`**: For families `A, B : J -> Type` and a fiberwise map `f j : A j -> B j`, the proposition that each `f j` is an equivalence equals the proposition that the total map `(j, a) ↦ (j, f j a)` is an equivalence.
  - **`totalEquiv.total`**: The induced total map `Σ (j : J) (A j) -> Σ (j : J) (B j)`.
  - **`totalEquiv.totalFiber`**: Identification of fibers: `Fib total (j, b) = Fib (f j) b`, proven by an equational chain that characterizes equality of pairs (via `sigmaEquiv`) and contracts the resulting singleton path space (via `rsigma`).
