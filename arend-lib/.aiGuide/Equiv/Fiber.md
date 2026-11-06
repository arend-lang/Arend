### Equiv.Fiber

Characterizes equivalences in terms of contractibility of their fibers.

This module establishes the standard homotopy-theoretic equivalence: a map `f : A -> B` is an equivalence iff every fiber `Σ(a : A), f a = b` is contractible. The forward direction extracts a quasi-equivalence by taking the contraction center as a section, with the fiber-contraction property witnessing both `ret_f` and `f_sec`. The reverse direction proves contractibility from any `IsEquiv` (and more generally from a `Section` paired with a `Retraction`), giving the standard logical equivalence between the two characterizations of equivalence.

#### Definition

- **`HasContrFibers`**: Proposition-valued predicate on `f : A -> B` asserting that for every `b : B`, the fiber `Σ (a : A) (f a = b)` is contractible. The standard "contractible fibers" characterization of an equivalence.

#### Conversions

- **`contrFibers=>QEquiv`**: From `HasContrFibers f` builds a `QEquiv f`, taking the center of each fiber as `ret` and its contraction to get `ret_f` and `f_sec`.
- **`contrFibers=>IsEquiv`**: `HasContrFibers f` implies `IsEquiv f`.
- **`IsEquiv=>contrFibers`**: Converse: an equivalence has contractible fibers.
- **`IsEquiv=>contrFibers.fromSection`**: Strengthening showing that a `Section f` together with a `Retraction f` already suffices to produce `HasContrFibers f`, using the half-adjoint correction `HAEquiv.coh_f_sec` of the section homotopy.
