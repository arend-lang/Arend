### Arith.Nat

Arithmetic, order, and divisibility infrastructure for the natural numbers.

This module equips `Nat` with the algebraic and order-theoretic structure needed throughout the library: it defines truncated subtraction `-'` and predecessor `pred`, packages the strict order via the inductive `NatOrder.<`, and assembles the canonical instances `NatSemiring : LinearlyOrderedCSemiring.Dec` and `NatBSemilattice : BottomJoinSemilattice`. A second focus is the interplay between `Nat` and `Fin n`, providing conversions `toFin`, `toFin'`, `mod_Fin` along with their characterization lemmas. The remainder of the file develops the basic theory of `div`/`mod`, divisibility (`LDiv`), and modular arithmetic congruences used elsewhere in the library.

#### Truncated Subtraction and Predecessor

- **`-'`**: Truncated subtraction on `Nat`: `0 -' m = 0`, `suc n -' suc m = n -' m`.
- **`pred`**: Predecessor: `pred 0 = 0`, `pred (suc x) = x`.
- **`suc_pred`**: `suc (pred n) = n` when `n /= 0`.
- **`-'0`**: `n -' 0 = n`.
- **`-'+`**: `(n + m) -' m = n`.
- **`-'id`**: `n -' n = 0`.
- **`-'-'`**: `a -' b -' c = a -' (b + c)`.
- **`suc/=0`**: `suc n = 0` is impossible.

#### Strict Order on Nat

- **`NatOrder.<`**: Inductive strict order: `0 < suc _` and `suc n < suc m` from `n < m`.
- **`NatOrder.unsuc<`**: Inverts `suc<suc`: `suc n < suc m -> n < m`.
- **`-'_<`**: `0 < n -' m` implies `m < n`.
- **`<_-'`**: `m < n` implies `0 < n -' m`.
- **`id<suc`**: `n < suc n`.
- **`id/=suc`**: `n /= suc n`.
- **`nonZero>0`**: `n /= 0` implies `0 < n`.

#### Conversion between Nat and Fin

- **`fin_<`**: A `Fin n` is bounded by `n`.
- **`toFin`**: Lifts `k : Nat` with `k < n` to `Fin n` by structural recursion.
- **`toFin=id`**: `toFin k p = k` as naturals.
- **`toFin=fin`**: `toFin` is a left inverse on `Fin n`.
- **`toFin'`**: Alternative lift via `mod`: `k mod suc n : Fin (suc n)`.
- **`mod_Fin`**: Convert `k : Nat` to `Fin n` using modular reduction, given `0 < n`.
- **`mod_Fin=mod`**: `mod_Fin k p = k mod n` as naturals.
- **`fin_nat-inj`**: Equality on `Nat` lifts to equality on `Fin n`.
- **`fin_nat-ineq`**: Disequality on `Fin n` lifts to disequality on `Nat`.
- **`toFin'=id`**: `toFin' p = k` as naturals when `k < n`.
- **`mod_Fin_<`**: `mod_Fin k p = k` as naturals when `k < n`.
- **`mod_Fin=id`**: `mod_Fin k p = k` for `k : Fin n`.
- **`fin_mod_id`**: `x mod suc n = x` for `x : Fin (suc n)`.

#### Algebraic and Order Instances

- **`NatSemiring`**: Decidable linearly ordered commutative semiring instance on `Nat` with the usual `+`, `*`, `<`, `0`, `1`.
- **`NatSemiring.triEquals`**, **`triGreater`**, **`triLess`**: Read off equality / strict comparison from the sign of `n - m` (in `Int`).
- **`NatSemiring.cancel-left`**, **`cancel-right`**: Additive cancellation.
- **`NatSemiring.cancel_*-left`**, **`cancel_*-right`**: Multiplicative cancellation by a nonzero factor.
- **`NatBSemilattice`**: Bottom join-semilattice on `Nat` with `bottom = 0`, joining the semiring's lattice structure.
- **`NatBSemilattice.<=_cancel-left`**, **`<=_cancel-right`**: Additive cancellation under `<=`.
- **`NatBSemilattice.ldistr0`**, **`rdistr0`**: `x ∧ 0 = 0` and `0 ∧ x = 0`.

#### Order Lemmas

- **`zero<=_`**: `0 <= x`.
- **`suc<=suc`**: Monotonicity of `suc` w.r.t. `<=`, with converse `suc<=suc.conv`.
- **`<=_exists`**: `n <= m` witnesses `n + (m -' n) = m`.
- **`-'<=id`**: `n -' m <= n`.
- **`-'+-comm`**: Distributes `-'` over `+` when subtrahend is bounded.
- **`-'-monotone-left`**, **`-'-monotone-right`**: Monotonicity of `-'` in each argument (right is anti-monotone).
- **`-'_<=`**: `n -' m = 0` implies `n <= m`.
- **`<=_*`**: Multiplication is monotone in both arguments.
- **`monotone-diagonal`**: A strictly increasing `f : Nat -> Nat` satisfies `n <= f n`.
- **`sequence-monotone`**, **`sequence-anti-monotone`**: Iterated step inequalities promote to general `<=`-comparisons; `sequence-monotone.induction` is the additive form.
- **`suc_<_<=`**, **`<_suc_<=`**, **`suc_<=_<`**, **`<=_<_suc`**: Conversions between `<` and `<=` shifted by `suc`.
- **`id<=suc`**: `n <= suc n`.

#### Division and Modulo

- **`n*_+_<n`**: `n * q + r < n` forces `q = 0`.
- **`mod-unique`**, **`div-unique`**: Uniqueness of remainder and quotient in the Euclidean decomposition.
- **`mod<=left`**: `n mod m <= n`.
- **`mod<right`**: `n mod m < m` when `m /= 0`.
- **`div_<`**, **`mod_<`**: When `n < m`, division yields `0` and `mod` yields `n`.
- **`natUnit`**: `n * m = 1` forces `m = 1` (units in `Nat` are trivial).

#### Divisibility

- **`natAssociates-areEqual`**: Mutually divisible naturals are equal.
- **`ldiv_<=`**: `n | m` with `m /= 0` implies `n <= m`.
- **`mod_div`**: `n mod m = 0` produces an `LDiv m n`.
- **`div_mod`**: `m | n` implies `n mod m = 0`.
- **`id_mod`**: `n mod n = 0`.
- **`div_*<=id`**: `(n div m) * m <= n`.

#### Boolean Decision Procedures

- **`nat_<=-dec`**: Reflects the boolean test `nat_<=_Bool n m := (n -' m == 0)` into `n <= m`.
- **`nat_<=-dec.nat_<=_Bool`**: The underlying boolean comparison.
- **`nat_<-dec`**: Boolean form of strict comparison `n < m`.

#### Powers and Modular Congruences

- **`id<pow2`**: `n < 2^n`.
- **`n*_+_mod_n`**: `(n * q + r) mod n = r` when `r < n`.
- **`n*_+_mod_n=mod`**: `(suc n * q + r) mod suc n = r mod suc n`; `.nat` is the variant for general `n`.
- **`mod_+-left`**, **`mod_+-right`**: Reducing one summand modulo `suc n` does not change the sum's residue.
- **`mod_*-left`**, **`mod_*-right`**: Same for multiplication.
- **`mod_+-cong-left`**, **`mod_+-cong-right`**: Congruence of `mod` under addition: equal residues remain equal after adding a common term.
