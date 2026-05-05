### Set.Fin.Instances

Finiteness instances for sigma types, products, and dependent function types over finite sets, with cardinality formulas.

#### Sigma and Product Finiteness

- **`SigmaFin`**: `FinSet` instance for `\Sigma (i : S) (Q i)` where `S` is finite and each `Q i` is finite; cardinality is `AbMonoid.FinSum` of the fiber cardinalities.
- **`SigmaFin.aux`**: Underlying equivalence `Fin (BigSum Q) ≃ \Sigma (i : Fin n) (Fin (Q i))`.
- **`SigmaFin.DecSubSet-isFin`**: A decidable subset of a finite set is itself finite.
- **`ProdFin`**: `FinSet` instance for `\Sigma A B` with cardinality `A.finCard * B.finCard`, derived from `SigmaFin` via `FinSum_replicate`.
- **`ProdFin.prod_equiv`**: Equivalence `Fin (n * m) ≃ \Sigma (Fin n) (Fin m)`.

#### Dependent Function Finiteness

- **`pi_equiv`**: Equivalence `Fin (BigProd Q) ≃ \Pi (j : Fin n) -> Fin (Q j)`, built recursively on `n` by combining `ProdFin.prod_equiv`, `sigma-right`, and a sigma-to-pi rearrangement.
- **`PiFin`**: `FinSet` instance for `\Pi (i : S) -> Q i` when `S` and each `Q i` are finite; cardinality is `CMonoid.FinProd` of the fiber cardinalities over `NatSemiring`.
- **`PiFin.pi-equiv-ext`**: Lifts a fiberwise equivalence `B j ≃ B' j` to an equivalence between dependent function types `\Pi (j : Fin n) -> B j ≃ \Pi (j : Fin n) -> B' j`.
- **`PiFin.pi-left`**: Reindexing equivalence: given a half-adjoint equivalence `e : A ≃ A'`, transports `\Pi (a : A) -> B (e a)` to `\Pi (a' : A') -> B a'`.
- **`PiFin.transport_idp`**: Transport of a section `h : \Pi x, B x` along a path `p : a = a'` yields `h a'`.
- **`PiFin.transport_pmap`**: Transport along `pmap f p` equals transport in the pulled-back family `\lam x => C (f x)` along `p`.
- **`PiFin.pi-card`**: The cardinality of `PiFin S Q` equals the `FinProd` of fiber cardinalities.

#### Finite Product Lemmas

- **`FinProd_char`**: For a commutative monoid `M` and finite `A`, the `FinProd` of `x : A -> M` equals `BigProd` along some equivalence `Fin A.finCard ≃ A`.
- **`FinProd_replicate`**: `FinProd` of a constant family `\lam _ => x` equals `M.pow x A.finCard`.
