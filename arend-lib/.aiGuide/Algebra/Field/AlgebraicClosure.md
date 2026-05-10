### Algebra.Field.AlgebraicClosure

Algebraic closures of fields, with an explicit construction for countable discrete fields.

This module characterizes algebraic closedness via the existence of roots of nonconstant monic polynomials and shows it is equivalent to the property that every monic polynomial splits into linear factors. An algebraic closure is packaged as a ring homomorphism that is both algebraically closed on the codomain and an integral extension. The countable case is built constructively by iterating splitting field extensions along an enumeration of all polynomials, then taking a lattice colimit over the resulting tower of discrete fields; properties of the closure (splitting, integrality) are transferred through the colimit using the naturality of the inclusion maps.

#### Algebraic Closedness

- **`IsAlgebraicallyClosed`**: A ring `K` is algebraically closed if every nonconstant monic polynomial over `K` has a root: `\Pi {p : Poly K} -> ∃ (n : Nat) (n /= 0) (degree<= p n) (polyCoef p n = 1) -> ∃ (a : K) (polyEval p a = 0)`.
- **`algebraicallyClosed<->split`**: TFAE over a commutative ring: (1) `IsAlgebraicallyClosed K`, (2) every monic polynomial factors as a product of linear factors, (3) every degree-`n` monic polynomial factors as a product of exactly `n` linear factors. The `aux` helper extracts the length-`n` factorization from algebraic closedness.
- **`algebraicallyClosed-integrallyClosed`**: An algebraically closed commutative ring `K` is integrally closed in any strict integral domain extension `f : RingHom K E`.

#### Algebraic Closures

- **`IsAlgebraicClosure`**: A ring homomorphism `f : RingHom K E` is an algebraic closure if `E` is algebraically closed and `f` is an integral extension: `\Sigma (IsAlgebraicallyClosed f.Cod) (isIntegralExt f)`.
- **`algebraicClosure-split`**: Builds an `IsAlgebraicClosure` for `f : RingHom K E` (with `E` a strict integral domain) from the assumptions that `f` is an integral extension and that every monic polynomial over `K` splits into linear factors over `E`.

#### Countable Construction

- **`countableAlgebraicClosure`**: For a countable discrete field `k`, constructs a discrete field `K`, a ring homomorphism `f : RingHom k K`, and a proof `IsAlgebraicClosure f`. Works by enumerating all polynomials over `k`, iteratively adjoining splitting fields, and taking the lattice colimit of the resulting countable tower.
- **`countableAlgebraicClosure.sequence`**: The tower of countable discrete fields `k = K_0 ⊆ K_1 ⊆ ...` where `K_{n+1}` is a splitting field over `K_n` for the `n`-th polynomial in the enumeration; each step records the field, its countability witness, the embedding from `k`, and integrality of that embedding.
- **`countableAlgebraicClosure.sequence3`**: Coherence lemma stating that the natural homomorphism from `K_0` to `K_n` along the tower agrees with the iterated embedding `(sequence kc f n).3`; used to identify maps after passing through the colimit.
