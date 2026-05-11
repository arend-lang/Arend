### Homotopy.Fibration

Homotopy fibers and total spaces of type families.

This module provides the basic homotopy-theoretic notion of a fiber `Fib f b` of a map `f : A -> B` over a basepoint `b : B`, defined as the type of pairs `(a, p)` with `p : f a = b`. The total space of a type family is also defined here as a dependent sum. The key technical content is the path characterization for fibers: paths in `Fib f b0` correspond to pairs `(p, q)` where `p` equates the underlying points and `q` shows that transporting along `f p` matches the fiber proofs, packaged as a `QEquiv` so that fiber equality can be reasoned about componentwise.

#### Total Space

- **`Total`**: Total space of a type family `F : B -> \Type`, defined as `\Sigma (b : B) (F b)`.
- **`Total.proj`**: First projection from the total space, sending `(b, _)` to `b`.

#### Homotopy Fiber

- **`Fib`**: Homotopy fiber of `f : A -> B` over `base : B`, defined as `\Sigma (a : A) (f a = base)`.
- **`Fib.make`**: Constructor for a fiber element from a point `a : A` and a witness `p : f a = base`.

#### Path Characterization

- **`Fib.ext`**: Extensionality for fibers: given `p : x.1 = x'.1` and `q : pmap f p *> x'.2 = x.2`, produces an equality `x = x'` in `Fib f b0`.
- **`Fib.ext.retraction`**: Underlying retraction lemma showing that the constructed path `x = x'` transports the trivial pair `(idp, idp_*> x.2)` to the given `(p, q)`; used to establish that `ext` is a section.
- **`Fib.equiv`**: Quasi-equivalence `(x = x') ≃ \Sigma (p : x.1 = x'.1) (pmap f p *> x'.2 = x.2)`, characterizing path equality in the fiber via componentwise data and built using `pathEquiv` from the retraction provided by `ext`.
