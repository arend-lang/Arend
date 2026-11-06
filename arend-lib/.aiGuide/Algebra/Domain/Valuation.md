### Algebra.Domain.Valuation

Valuation rings: integral domains in which divisibility is total.

A valuation ring is a strict Bézout domain that is also a local commutative ring, characterized by the property that for any two elements one divides the other. The module derives the local and strict-Bézout structure from this divisibility totality, and exposes the associated valuation as a monoid homomorphism into the totally ordered divisibility quotient. This presentation makes valuation rings a refinement of Bézout domains rather than a parallel structure, so existing GCD/divisibility infrastructure transfers automatically.

#### Main Class

- **`ValuationRing`**: Extends `StrictBezoutDomain` and `LocalCRing`. Adds the field `isValuationRing : LDiv a b || LDiv b a` asserting that divisibility is total. Locality and strict Bézout-ness are derived from this: locality follows because for any `a`, either `a` or `a + 1` is invertible (witnessed by inverting one side of the divisibility); strict Bézout-ness picks the trivial coefficient combination on whichever side divides. The `isValuationRing` default implementation (overriding the inherited one) reconstructs totality from the Bézout coefficients via a case analysis on which summand of `1` is invertible, using `sum1Array` to extract the unit component.

#### Associated Structures

- **`ValuationMonoid`**: The `OrderedCMonoid` of divisibility classes, defined as `DivQuotient.DivQuotientMonoid \this`. Elements are equivalence classes of ring elements under mutual divisibility, with the partial order induced by divisibility.
- **`ValuationTotalOrder`**: Promotes `ValuationMonoid` to a `TotalOrder`, exploiting the fact that divisibility is total in a valuation ring.
- **`valuation`**: The canonical valuation as a `MonoidHom` from the ring (as a multiplicative monoid) to `ValuationMonoid`, sending each element to its divisibility class via `in~`. This is the algebraic analogue of a valuation map `v : R → Γ` into a totally ordered value monoid.
