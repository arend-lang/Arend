### Set.Fin.Instances

Constructions showing that finite sets are closed under sigma-types, products, and dependent function spaces, with cardinality computed by the corresponding sums and products.

This module assembles the standard closure properties of `FinSet` under type-theoretic constructors. Sigma-types over a finite indexed family of finite sets are finite, with cardinality given by `BigSum` of the fiber cardinalities; binary products specialize this via replication into multiplication; dependent products yield `BigProd` cardinalities. The proofs build explicit equivalences between `Fin`-indexed enumerations and the structured types (using `OrFin.aux` for inductive disjoint-union splits), then transfer these along finiteness witnesses, leveraging `Semiring.FinSum_replicate` and `BigSum_replicate` to convert between `BigSum` and multiplication.

#### Sigma and Product Instances

- **`SigmaFin`**: Given `S : FinSet` and a family `Q : S -> FinSet`, makes `\Sigma (i : S) (Q i)` a `FinSet` with cardinality `AbMonoid.FinSum (\lam i => (Q i).card)`.
- **`SigmaFin.aux`**: Core `QEquiv` between `Fin (BigSum Q)` and `\Sigma (i : Fin n) (Fin (Q i))`, built by induction on `n` via `OrFin.aux` to split the sum into the first fiber and the recursive remainder.
- **`SigmaFin.DecSubSet-isFin`**: Any decidable subset of a finite set is itself a `FinSet`.
- **`ProdFin`**: Binary product instance: `\Sigma A B` is a `FinSet` with cardinality `A.card Nat.* B.card`, derived from `SigmaFin` with the constant family `B` together with `Semiring.FinSum_replicate`.
- **`ProdFin.prod_equiv`**: `QEquiv` `Fin (n Nat.* m) ≃ \Sigma (Fin n) (Fin m)`, obtained by transporting `SigmaFin.aux` along `Semiring.BigSum_replicate`.

#### Dependent Function Spaces


#### Finite Product Lemmas

