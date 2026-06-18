### Equiv.Path

Equivalences between path types induced by retractions, embeddings, and sections.

This module shows how structure on a map `f : A -> B` lifts to its action on paths. The central trick is `pathEquiv`: a family of retractions `(a = a') -> R a a'` already entails that each retraction is a quasi-equivalence, because path induction reduces the section-then-retraction round trip to an idempotency calculation. From this, `pmap` of any `Equiv` is again an equivalence, and the same machinery upgrades embeddings and sections so that proofs of equality between points transport along `f` without losing information.

#### Path Equivalences from Retractions

- **`pathEquiv`**: Given a relation `R : A -> A -> \Type` and a retraction `(a = a') -> R a a'` for all `a, a'`, promotes it to a `QEquiv`. The full equivalence is recovered from a one-sided retraction by exploiting idempotency of `sec ∘ f` via path induction.
- **`pathConcatEquiv`**: For `p : a = b`, left-concatenation by `inv p` gives a `QEquiv {a = c} {b = c}`. Provides the standard path-space equivalence induced by a base-point change.

#### Action of Equivalences on Paths

- **`pmapEquiv`**: For an `Equiv e : A -> B` and points `a, a' : e.A`, `pmap e : (a = a') -> (e a = e a')` is a `QEquiv`. The inverse is `inv (e.ret_f a) *> pmap e.ret p *> e.ret_f a'`; the proof uses `HAEquiv` (half-adjoint) coherence to verify the round-trip via naturality of the homotopy `f_sec`.
- **`pmapEmbedding`**: For an `Embedding e`, `pmap e` is itself an `Embedding` on each path space. Follows from `pmapEquiv` applied to the equivalence underlying embedding-ness (`pmap-isEquiv`).
- **`pmapSection`**: For a `Section s`, `pmap s` is a `Section` on each path space, with explicit retraction `\lam q => (inv (s.ret_f a) *> pmap s.ret q) *> s.ret_f a'`. The retract identity is verified by `Jl` on the original path.
