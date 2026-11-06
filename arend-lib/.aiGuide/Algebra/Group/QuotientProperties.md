### Algebra.Group.QuotientProperties

The quotient group notation `G // H` and the First Isomorphism Theorem for groups.

This module gives the infix notation for the quotient of a group by a normal subgroup, the homomorphism `kerQuotHom` induced by a homomorphism `f : G → H` on the quotient `G // KernelGroup f`, and the First Isomorphism Theorem: if `f` is surjective, then the induced homomorphism is an isomorphism `G/ker f ≅ H`.

#### Notation

- **`//`**: Infix notation for the quotient group `G // H = H.Quot`, where `H : NormalSubGroup G`.

#### First Isomorphism Theorem

- **`kerQuotHom`**: The homomorphism `G // KernelGroup f -> H` induced by a group homomorphism `f : G -> H`, obtained from the universal property of the quotient since the kernel is sent to the identity.
- **`FirstIsoTheorem`**: For surjective `f`, `kerQuotHom f` is a group isomorphism, i.e. `G/ker f ≅ H`.
