### Equiv.HalfAdjoint

Half-adjoint equivalences: a refined notion of equivalence where the section and retraction homotopies satisfy a coherence condition, making the type of such equivalences a proposition.

A `QEquiv` provides independent section and retraction homotopies but no compatibility between them, so its space is generally not propositional. A `HAEquiv` adds the *triangle identity* `pmap f (ret_f x) = f_sec (f x)`, which uniquely pins down the higher coherence data. Crucially, every `QEquiv` can be promoted to a `HAEquiv` by adjusting one of its homotopies (`coh_f_sec`) so the triangle holds, so the two notions are equivalent up to inhabitation while `HAEquiv` enjoys the better propositional property.

#### Main Record

- **`HAEquiv`**: Extends `QEquiv` with the coherence field `f_ret_f=f_sec_f : pmap f (ret_f x) = f_sec (f x)`, expressing that the action of `f` on the retraction homotopy agrees with the section homotopy at `f x`. This is the standard half-adjoint triangle identity.

#### Construction from a Quasi-Equivalence

- **`coh_f_sec`**: Given a `Section s` and any homotopy `r : s (s.ret y) = y`, builds a *corrected* section homotopy `inv (r (s (s.ret y))) *> pmap s (s.ret_f (s.ret y)) *> r y`. This adjustment is what makes the triangle identity hold.
- **`coh_f_ret_f=f_sec_f`**: Proof that with `coh_f_sec` as the section homotopy, the triangle identity `pmap s (s.ret_f x) = coh_f_sec s r (s x)` holds. Uses `homotopy_app-comm` and `homotopy-isNatural` to manipulate the path algebra.
- **`fromQEquiv`**: Coercion turning any `QEquiv` into an `HAEquiv` by replacing its `f_sec` with `coh_f_sec` and supplying the triangle identity. Marked `\coerce`, so quasi-equivalences are silently promoted to half-adjoint equivalences when needed.

#### Propositional Property

- **`levelProp`**: For fixed `f : A -> B`, the type `HAEquiv f` is a proposition. This is the key advantage of the half-adjoint formulation over `QEquiv` and underlies its use in proving that being an equivalence is a property.
