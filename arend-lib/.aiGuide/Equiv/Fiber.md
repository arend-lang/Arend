### Equiv.Fiber

Characterizes equivalences in terms of contractibility of their fibers.

This module establishes the standard homotopy-theoretic equivalence: a map `f : A -> B` is an equivalence iff every fiber `Σ(a : A), f a = b` is contractible. The forward direction extracts a quasi-equivalence by taking the contraction center as a section, with the fiber-contraction property witnessing both `ret_f` and `f_sec`. The reverse direction proves contractibility from any `Equiv` (and more generally from a `Section` paired with a `Retraction`), giving the standard logical equivalence between the two characterizations of equivalence.

#### Definition

- **`hasContrFibers`**: Predicate `(f : A -> B)` asserting that for every `b : B`, the fiber `Σ (a : A) (f a = b)` is contractible. The standard "contractible fibers" characterization of an equivalence.
- **`hasContrFibers.levelProp`**: Proof that `hasContrFibers f` is a proposition, since contractibility is itself a proposition.

#### Conversions

- **`contrFibers=>Equiv`**: From `hasContrFibers f`, constructs a `QEquiv f`. The inverse `ret y` is the first component of the contraction center of the fiber over `y`; `ret_f` follows by transporting the fiber-contraction along `(x, idp)`, and `f_sec` is the second component of the center.
- **`Equiv=>contrFibers`**: Any `Equiv e` has contractible fibers — the converse direction completing the characterization.
- **`Equiv=>contrFibers.fromSection`**: Strengthening showing that a `Section s` together with a `Retraction s` already suffices to produce `hasContrFibers s`, without needing the full half-adjoint coherence.
