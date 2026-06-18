### Set.Fin.Instances

Constructions showing that finite sets are closed under sigma-types, products, and dependent function spaces, with cardinality computed by the corresponding sums and products.

This module assembles the standard closure properties of `FinSet` under type-theoretic constructors. Sigma-types over a finite indexed family of finite sets are finite, with cardinality given by `BigSum` of the fiber cardinalities; binary products specialize this via replication into multiplication; dependent products yield `BigProd` cardinalities. The proofs build explicit equivalences between `Fin`-indexed enumerations and the structured types (using `OrFin.aux` for inductive disjoint-union splits), then transfer these along finiteness witnesses, leveraging `Semiring.FinSum_replicate` and `BigSum_replicate` to convert between `BigSum` and multiplication.

#### Sigma and Product Instances

- **`SigmaFin`**: Given `S : FinSet` and a family `Q : S -> FinSet`, makes `\Sigma (i : S) (Q i)` a `FinSet` with cardinality `AbMonoid.FinSum (\lam i => (Q i).finCard)`.
- **`SigmaFin.aux`**: Core `QEquiv` between `Fin (BigSum Q)` and `\Sigma (i : Fin n) (Fin (Q i))`, built by induction on `n` via `OrFin.aux` to split the sum into the first fiber and the recursive remainder.
- **`SigmaFin.DecSubSet-isFin`**: Any decidable subset of a finite set is itself a `FinSet`.
- **`ProdFin`**: Binary product instance: `\Sigma A B` is a `FinSet` with cardinality `A.finCard Nat.* B.finCard`, derived from `SigmaFin` with the constant family `B` together with `Semiring.FinSum_replicate`.
- **`ProdFin.prod_equiv`**: Equivalence `Fin (n Nat.* m) ≃ \Sigma (Fin n) (Fin m)`, obtained by transporting `SigmaFin.aux` along `Semiring.BigSum_replicate`.

#### Dependent Function Spaces

- **`pi_equiv`**: Equivalence `Fin (BigProd Q) ≃ \Pi (j : Fin n) -> Fin (Q j)`, built recursively: at `suc n`, factor through `ProdFin.prod_equiv` and `sigma-right` to peel off the first index, then reassemble via case analysis on `Fin (suc n)`.
- **`PiFin`**: For `S : FinSet` and `Q : S -> FinSet`, makes `\Pi (i : S) -> Q i` a `FinSet` with cardinality `CMonoid.FinProd (\lam x => (Q x).finCard)`.
- **`PiFin.pi-equiv-ext`**: Pointwise lifting of an equivalence family `\Pi j -> Equiv (B j) (B' j)` to an equivalence between the dependent products `\Pi j -> B j` and `\Pi j -> B' j`.
- **`PiFin.pi-left`**: Reindexing equivalence: a half-adjoint equivalence `e : A ≃ A'` induces an equivalence `(\Pi (a : A) -> B (e a)) ≃ (\Pi (a' : A') -> B a')`.
- **`PiFin.transport_idp`**: Pointwise transport-of-section lemma: `transport B p (h a) = h a'` for `h : \Pi x -> B x`.
- **`PiFin.transport_pmap`**: Compatibility of transport with `pmap`: `transport C (pmap f p) c = transport (C ∘ f) p c`.
- **`PiFin.pi-card`**: The cardinality of `PiFin S Q` equals `CMonoid.FinProd (\lam i => (Q i).finCard)`.

#### Finite Product Lemmas

- **`FinProd_char`**: Characterization of `CMonoid.FinProd` over a `FinSet`: there merely exists an enumeration `e : Fin A.finCard ≃ A` such that `FinProd x = BigProd (\lam j => x (e j))`.
- **`FinProd_replicate`**: A constant finite product collapses to a power: `FinProd (\lam _ => x) = M.pow x A.finCard`.
