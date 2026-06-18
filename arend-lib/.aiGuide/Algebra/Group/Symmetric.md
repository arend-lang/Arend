### Algebra.Group.Symmetric

The symmetric group `Sym n` of permutations of `Fin n`, with cycle/transposition decompositions and the sign homomorphism.

This module formalizes finite symmetric groups as `Equiv {Fin n} {Fin n}`, exploiting the pigeonhole principle to construct permutations from injective endofunctions. Permutations are decomposed in two complementary ways: every permutation factors into adjacent transpositions `transposition1 a` (swapping `a` and `suc a`), and every permutation factors into disjoint cycles via `cycle-decomp`. The sign of a permutation is defined combinatorially via the parity of its inversion count, and the key result `sign_*` shows it is a monoid homomorphism by analyzing how multiplying by an adjacent transposition flips inversion parity. Supports of permutations (the set of non-fixed points) are tracked explicitly to reason about disjoint/independent permutations commuting.

#### The Symmetric Group

- **`Sym`**: `Sym n = Equiv {Fin n} {Fin n}` — the type of permutations of `Fin n`.
- **`Sym.fromEquiv`**: Coerces an `Equiv` into `Sym`.
- **`Sym.equals`**: Extensionality: pointwise equality implies equality of permutations.
- **`Sym.reduce`**: Given `e : Sym (suc n)`, produces `Sym n` by skipping `e 0`; the inductive step for permutation recursion.
- **`Sym.reduce_id`**: `reduce idEquiv = idEquiv`.
- **`SymmetricGroup`**: The group instance on `Sym n` with `idEquiv` as identity, `transEquiv` as multiplication, and `symQEquiv` as inverse.

#### Support of Permutations

- **`Sym.Support`**: `Support e i = (e i /= i)` — the proposition that `i` is moved by `e`.
- **`Sym.SupportFin`**: Finiteness instance for the sigma `(i : Fin n) × Support e i`.
- **`Sym.Support_inverse`**: Support is preserved under taking the inverse.
- **`Sym.Support_ret`**: If `i` is in the support, so is `e.ret i`.
- **`Sym.Support_Not`**: If `i` is not in the support, then `e i = i`.

#### Cycles and Independence

- **`isCycle`**: A permutation is a cycle iff some support point's orbit covers the entire support.
- **`independent_*`**: Permutations with disjoint supports commute.
- **`independent_Support-right` / `independent_Support-left`**: Support of a product when factors have disjoint supports.
- **`independent_Support-conv`**: A point in the support of `e * e'` lies in the support of `e` or `e'`.
- **`independent_Support_BigProd`** (with **`.conv`**): Support behavior for a big product of pairwise-disjoint permutations.

#### Cycle Construction

- **`cycle`**: Given an injective array `l : Array (Fin n) k`, builds the cyclic permutation sending `l i` to `l (i+1 mod k)`.
- **`cycle.func`**: The underlying function of `cycle` (defined via `index-dec`).
- **`cycle.step`**: `cycle l inj (l i) = l ((suc i) mod k)`.
- **`cycle.steps`**: Iterating `cycle l` `m` times sends `l 0` to `l m`.
- **`Support_cycle`**: A support point of `cycle l` must equal some `l j`.
- **`cycle_Support`**: For `k > 1`, every `l j` is in the support of `cycle l inj`.
- **`cycle-isCycle`**: For `k > 1`, `cycle l inj` is a cycle in the sense of `isCycle`.

#### Transpositions

- **`transposition`**: `transposition (a/=b : a /= b) : Sym n` — the swap of `a` and `b`, defined as a 2-cycle.
- **`transposition.transposition-left/right`**: Swaps `a` and `b`.
- **`transposition.transposition_/=`**: Fixes points distinct from `a` and `b`.
- **`transposition.transposition-isInv`**: Transpositions are involutions.
- **`transposition1`**: `transposition1 a : Sym (suc n)` — the adjacent transposition swapping `a` and `suc a`.
- **`transposition1.transposition1-left/right/_/=/_</_>`**: Computational rules for `transposition1`, including its action above and below the swap.
- **`transposition1.transposition1-isInv`**: Adjacent transpositions are involutions.

#### Recursive Structure and Finiteness

- **`symmetric-rec`**: The equivalence `Sym (suc n) ≃ Fin (suc n) × Sym n`, sending `e` to `(e 0, reduce e)`.
- **`cyclePerm`**: For `k : Fin n`, the permutation rotating `0` to `k`.
- **`SymFin`**: `FinSet` instance with `|Sym n| = n!`.
- **`SymFin.fin_equiv`**: Explicit equivalence `Fin (n!) ≃ Sym n`, built recursively via `symmetric-rec`.
- **`EquivFin`**: `FinSet` instance for `Equiv {A} {B}` between two finite sets — empty if `|A| ≠ |B|`, else `|B|!`.
- **`EquivFin.equiv_fin`**: Explicit `(k, Equiv (Equiv {Fin n} {Fin m}) (Fin k))` witness.

#### Lifting Permutations

- **`lift`**: Embeds `Sym n` into `Sym (suc n)` by fixing `0` and shifting.
- **`lift_ide`**: Lifting preserves the identity.
- **`lift_*`**: Lifting is a homomorphism.
- **`lift_BigProd`**: Lifting commutes with big products.
- **`lift_cycle`**: Lifting a cycle gives the cycle on the shifted array.
- **`lift_transposition` / `lift_transposition1`**: Lifting of transpositions.

#### Decomposition into Adjacent Transpositions

- **`transposition1-decomp`**: Every `e : Sym (suc n)` factors as a product of adjacent transpositions; returns `(l, e = BigProd (map transposition1 l))`.
- **`transposition1-decomp.aux`**: Recursive lift step: `e = lift (reduce e) * (rotation of 0 to e 0)`.
- **`transposition1-decomp.aux1`**: A product of consecutive `transposition1`s sends `0` to `k`.
- **`transposition1-decomp.BigProd-fixed`**: A big product fixes `i` if every factor does.
- **`transposition1-decomp.BigProd-unique`**: Computes the action of a big product when only one factor moves a given point.

#### The Sign Homomorphism

- **`sign`**: `sign e = (-1)^(inversions e)` in any ring `R`.
- **`sign.inversions`**: The number of inversions, defined recursively as `e 0 + inversions (reduce e)`.
- **`sign.inversions_ide`**: The identity has zero inversions.
- **`sign.Inversions`**: The set `{(i, j) | i < j, e j < e i}` of inversion pairs.
- **`sign.InversionsFin`**: `FinSet` structure showing this set has size `inversions e`, via the explicit equivalence in **`InversionsFin.aux`**.
- **`sign.sign_hom`**: Ring homomorphisms preserve signs.
- **`sign_ide`**: `sign ide = ide`.
- **`sign_*`**: `sign (e * e') = sign e * sign e'` — the multiplicativity of sign.
- **`sign_*.Inverstions_transposition_>` / `Inverstions_transposition_<`**: Bijections relating the inversion sets of `e` and `transposition1 a * e` depending on whether `e a < e (suc a)` or vice versa.
- **`sign_*.inverstions_transposition_>` / `inverstions_transposition_<`**: Multiplying by an adjacent transposition changes the inversion count by exactly one.
- **`sign_*.inversions_transposition`**: Parity of inversions flips when multiplying by an adjacent transposition.
- **`sign_*.inversions_*`**: Inversion count of a product is the sum of inversion counts mod 2.
- **`sign_inverse`**: `sign (inverse e) = sign e`.
- **`signHom`**: The monoid homomorphism `SymmetricGroup n → R` sending `e` to `sign e`.

#### Cycle Decomposition

- **`cycle-decomp`**: Every permutation decomposes as a product of pairwise-disjoint cycles.
- **`cycle-decomp.aux`**: Recursive constructor: peels off one cycle (the orbit of a non-fixed point) and recurses on the remaining permutation, with the bound `k` controlling termination via support cardinality.
- **`cycle-decomp.pow-lem`**: If `pow e n x = pow e k x` with `n < k`, then `pow e (k-'n) x = x`.
- **`cycle-decomp.pow_mod`**: `pow e (k mod m) i = pow e k i` when `m` is a period of `i`.
- **`cycle-decomp.fixPoint-iter`**: Powers preserve fixed points.
- **`cycle-decomp.pow-rotate`**: `e (pow e k i) = pow e k (e i)`.
