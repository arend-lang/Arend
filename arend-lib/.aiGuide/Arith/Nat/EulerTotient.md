### Arith.Nat.EulerTotient

Defines Euler's totient function and develops the algebraic machinery linking residues mod n, units of finite rings, and the Chinese remainder theorem.

This module establishes the equivalence between numbers coprime to `n` and the group of units of `Z/nZ` (realized as `FinRing`), defining the totient as the finite cardinality of the coprime set. The construction goes through Bezout-based inverse formulas: for `k` coprime to `n+1`, the modular inverse is computed via the Bezout coefficients of the integer Euclidean domain. The module also identifies `FinRing` with the factor ring `Z/(n+1)Z` by combining the kernel/image factorization of the integer coefficient map, and sets up the Chinese remainder map for proving multiplicativity of the totient on coprime arguments.

#### Coprime Residues and the Totient

- **`coPrimes`**: `Nat -> FinSet`. The finite set of `k : Fin (suc n)` with `gcd(k, suc n) = 1`, defined as a sigma over a decidable predicate.
- **`EulerTotient`**: `Nat -> Nat`. Euler's totient `φ(n+1)`, the finite cardinality of `coPrimes n`.

#### Group of Units of a Monoid

- **`InvSubMonoid`**: `Monoid M -> SubMonoid M`. The submonoid of invertible elements (units), with `contains x = Inv x`.
- **`InvGroup`**: `Monoid M -> Group`. Promotes the units submonoid to a group, providing inverses by swapping the components of an `Inv` witness.

#### Coprimes ↔ Units of FinRing

- **`coPrimes-eq`**: For `n > 0`, an equivalence `QEquiv {coPrimes n} {InvGroup (FinRing {n})}`. The forward map sends a coprime `k` to a unit using `finv-formula`; the inverse uses Bezout's identity to show that any unit must be coprime to `n+1`.
- **`coPrimes-eq.finv-formula`** (where-block): Computes the modular inverse of `x : Fin (suc n)` via Bezout coefficients from `IntEuclidean`, taking the result mod `suc n`.
- **`coPrimes-eq.gcd-inv`** (where-block): Lemma showing `x * finv-formula x = 1 mod (suc n)` when `gcd(suc n, x) = 1`.

#### FinRing as Z/nZ

- **`ker-int-Coef`**: The kernel of the canonical map `IntRing -> FinRing {n}` (sending an integer to its image) equals the principal ideal `(suc n)` in `IntEuclidean`. Proved via `modEq-ldiv` characterizing congruence as ideal membership.
- **`Z/nZ=FinRing`**: An isomorphism `FactorRing (closure1 (suc n)) ≃ FinRing {n}`, obtained by transporting through `ker-int-Coef` and the surjectivity of the integer coefficient map, then applying the kernel/image factorization `ringKerImageHom-iso`.

#### Chinese Remainder Theorem

- **`chinese-map`**: For moduli `n, m`, the map `FinRing {suc n * m + n} -> FinRing {n} × FinRing {m}` reducing an element modulo each factor via `Fin.fromNat`.
- **`chinese-map-surj`**: Surjectivity of `chinese-map` when `suc n` and `suc m` are coprime — the CRT statement used to prove multiplicativity of the totient.
- **`chinese-map-surj.mod-inv`** (where-block): Auxiliary lemma `x mod (n*m) mod n = x mod n` for `n /= 0`, used to verify the components of the surjectivity proof.

#### Auxiliary

- **`natSemiring-unit`**: Any unit in `NatSemiring` equals `1` — the natural numbers have only the trivial unit.
