### Equiv.Sigma

Equivalences involving sigma types and contractibility.

#### Contractibility Lemmas

- **`lsigma`**: `\Sigma (x : A) (a0 = x)` is contractible.
- **`rsigma`**: `\Sigma (x : A) (x = a0)` is contractible.
- **`contr-left`**: `\Sigma (x : A) (B x)` ≃ `B c.center` when `A` is contractible.
- **`contr-right`**: `\Sigma (x : A) (C x)` ≃ `A` when each `C x` is contractible.

#### Path-Type Equivalences

- **`equiv=`**: `QEquiv` implies type equality (`A = B` via `iso`).
- **`pi-contr-left`**: `\Pi (a' : A) (p : a = a') -> B a' p` ≃ `B a idp`.
- **`pi-contr-right`**: `\Pi (a : A) (p : a = a') -> B a p` ≃ `B a' idp`.

#### Sigma Functoriality

- **`sigma-left`**: An `HAEquiv {A} {A'}` lifts to `\Sigma (a : A) (B' (e a))` ≃ `\Sigma (a' : A') (B' a')`.
- **`sigma-right`**: Fiberwise equivalences lift to sigma-type equivalences.
- **`sigma-equiv`**: Combines `sigma-left` and `sigma-right` for full sigma equivalences.

#### Miscellaneous

- **`unit-func`**: `(\Sigma -> A)` ≃ `A`.
- **`totalEquiv`**: Fiberwise equivalences ↔ equivalence of total spaces.
  - **`total`**: The total map `\Sigma (j : J) (A j) -> \Sigma (j : J) (B j)`.
  - **`totalFiber`**: Fibers of the total map equal fibers of the fiberwise maps.
