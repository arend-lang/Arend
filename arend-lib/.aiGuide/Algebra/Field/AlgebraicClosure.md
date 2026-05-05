### Algebra.Field.AlgebraicClosure

Definitions and constructions of algebraically closed fields and algebraic closures, including the existence of algebraic closures for countable discrete fields.

#### Algebraic Closedness

- **`IsAlgebraicallyClosed`**: Predicate on a `Ring K` asserting every nonzero polynomial of positive degree with leading coefficient `1` has a root in `K`.
- **`algebraicallyClosed<->split`**: TFAE on a `CRing K`: (1) `K` is algebraically closed; (2) every monic polynomial splits as a product of linear factors `padd 1 (-a)`; (3) every polynomial of degree `≤ n` with leading coefficient `1` splits into exactly `n` such linear factors.
- **`algebraicallyClosed<->split.aux`**: Auxiliary lemma extracting the `n`-array splitting from algebraic closedness given degree and leading-coefficient data.

#### Integral Closure Property

- **`algebraicallyClosed-integrallyClosed`**: If `K` is algebraically closed, then any ring hom `f : RingHom K E` into a `StrictIntegralDomain` is integrally closed (every element of `E` integral over `K` lies in the image of `f`).

#### Algebraic Closures

- **`IsAlgebraicClosure`**: Predicate on a ring hom `f`: its codomain is algebraically closed and `f` is an integral extension.
- **`algebraicClosure-split`**: Builds an `IsAlgebraicClosure f` from `f : RingHom K E` (with `E` a `StrictIntegralDomain`) given that `f` is an integral extension and that every monic polynomial over `K` splits into linear factors over `E`.

#### Countable Algebraic Closure

- **`countableAlgebraicClosure`**: Constructs, for a countable `DiscreteField k`, a `DiscreteField K` together with a ring hom `k -> K` that is an algebraic closure. Built as a colimit of iterated splitting field extensions enumerating all polynomials over `k`.
- **`countableAlgebraicClosure.sequence`**: Recursively defines, for each `n : Nat`, a discrete field with a countable structure, integral extension hom from `k`, by iteratively adjoining a splitting field for the `n`-th polynomial in the enumeration.
- **`countableAlgebraicClosure.sequence3`**: Coherence lemma identifying the natural transition map of the colimit functor (from level `0` to level `n`) with the composed extension hom `(sequence kc f n).3` on elements of `k`.
