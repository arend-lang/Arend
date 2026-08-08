# Richman's spectrum in Arend — what remains

Working document for the choice-free formalisation of Richman's constructive fundamental theorem of
algebra, and its relationship to the existing `adchoice`-based `FTA`.

Reference: Fred Richman, *The fundamental theorem of algebra: a constructive development without
choice*, Pacific J. Math. **196** (2000) 213–230,
<https://msp.org/pjm/2000/196-1/pjm-v196-n1-p10-p.pdf>.
(The PDF is a compressed byte stream — run `pdftotext` on it; fetching it as text yields nothing.)

---

## 1. Why this exists

`arend-lib/src/Arith/Complex/FTA.ard` proves the FTA in full:

```arend
\lemma FTA {n : Nat} (n>0 : 0 < n) (f : Poly ComplexField)
           (deg : degree< f (suc n)) (monic : polyCoef f n = 1)
  : ∃ (z : Complex) (polyEval f z = 0)
```

Its single non-constructive ingredient is `adchoice` (dependent choice over `Nat`), which lives in
`arend-lib/src/Logic/Classical.ard` beside `achoice`. `adchoice` is *derivable* from `achoice`
(`adchoice.fromChoice`, proved), so it adds no strength; it is axiomatised separately because
`achoice` additionally yields excluded middle via `achoice.lemFromChoice`, and dependent choice does
not.

Two facts frame everything below.

**Choice cannot be removed from this statement.** Richman §2: *"Countable choice is required just to
construct a root of the polynomial X² − a for arbitrary complex a."* A complex square-root function
implies a weak countable-choice principle about antipodal pairs on the circle, and in sheaf models
where countable choice fails, a root of `X² − a` would be a continuous local square root, which does
not exist. So the goal is **not** to prove `FTA` without axioms — that is impossible.

**AC_ω cannot replace `adchoice` in the *existing* proof.** The Kneser iteration's state is a point of
`ℂ` and each step depends on the previous point, so pre-choosing a successor function needs choice over
a subset of `ℂ`, not over `Nat`. AC_ω ⇏ DC is independent in ZF (Jensen). This is a fact about that
*proof*, not about the theorem: §5 gives a **different** route to the same statement that needs only
AC_ω, and the reason it works is precisely that A3 makes coherence a theorem instead of a construction
obligation.

Phase A therefore aims at Richman's **reformulated** theorem: the spectrum of a monic degree-`n`
polynomial exists as a point of the completion of the space of `n`-multisets, with **no axiom at all**.
That is strictly weaker than "there is a root" — if the multiset space were complete you could read a
root off it, and the sheaf model shows you cannot — but it is a genuine theorem, and it is the most
that is available axiom-free.

It also has a second payoff, which is the practical reason to finish it, and which is **now delivered**:
Phase A's shrinking lemma turns the existing `FTA` statement into a consequence of **plain countable
choice** rather than dependent choice (§5, `FTA-choice`) — strictly weaker than what `FTA.ard` assumes
today, and weaker than `achoice`, which additionally gives excluded middle.

So the development now records three statements at three strengths, which is the point of the exercise:

| Statement | Axiom |
|---|---|
| `spectrum p : ComplexMultiset n`, and `mem (spectrum p) z ↔ polyEval p z = 0` | **none** |
| `FTA-choice` — `∃ (z : Complex) (polyEval p z = 0)` | `acomega` (AC_ω) |
| `FTA` — the same statement | `adchoice` (DC) |

The second line is the interesting one: it is the same theorem as the third from a strictly weaker
hypothesis, and by §1's first framing fact no line of this table can be pushed below the first.

---

## 2. Status

| Piece | File | Lines | Goals | Axioms |
|---|---|---|---|---|
| A1 matching pseudometric | `src/Topology/MetricSpace/Multiset.ard` | 312 | 0 | none |
| A2 approximate factorisation | `src/Arith/Complex/ApproxFactor.ard` | 352 | 0 | none |
| A3.1 root bound | `src/Arith/Complex/Spectrum.ard` | — | 0 | none |
| A3.2 product bound | `src/Arith/Complex/Spectrum.ard` | — | 0 | none |
| A3.3 matching | `src/Arith/Complex/Spectrum.ard` | — | 0 | none |
| A4 the spectrum | `src/Arith/Complex/Spectrum/Point.ard` | 396 | 0 | none |
| **Phase B — `FTA` from AC_ω** | `src/Arith/Complex/FTA/Choice.ard` | 256 | **0** | `acomega` only |

**Both phases are complete.** Phase A is 1764 lines (`Spectrum.ard` alone is 704):
`spectrum p deg monic : ComplexMultiset n` is constructed and `spectrum-char` proves
`mem (spectrum p) z ↔ polyEval p z = 0`, both **axiom-free**. Phase B is 256 lines: `FTA-choice`
proves `FTA`'s exact statement from `acomega` (countable choice) alone.

Axiom use is mechanically checked, not assumed. `arend -fu adchoice` gives exactly one usage
(`FTA.ard:204`, in `fta-seq`), `arend -fu fta-seq` exactly one (`FTA`), and `arend -fu FTA` reports
`No usages.` So the whole `adchoice` cone is a dead end reachable only from `FTA` itself — nothing in
A1–A4 or in Phase B can touch it, even though `Choice.ard` imports `FTA.ard` for its analytic
infrastructure. `arend -fu acomega` gives exactly two usages, both in `Choice.ard`: the two
applications. `achoice` occurs only inside `Logic/Classical.ard`.

Verified independently of the warm daemon and of the binary caches by `arend --daemon-stop &&
arend --no-daemon -r` (full from-source rebuild, 397 modules): **0 errors, 2m2s**, 1 module with goals
(the pre-existing `Topology.Locale.HausdorffLocale` one).

> **A stale-cache sighting, for the record.** Before that rebuild, every warm-daemon run reported a
> `DiscreteField.{u}` type mismatch at `Algebra.Field.Splitting:87` plus fallout at 88–93 — a module
> that imports nothing from `Arith.Complex` and that nothing here touches. It is the classic stale-`.arc`
> symptom (a bogus mismatch naming what looks like the same type twice, on a definition you never
> edited). The cold `-r` pass shows it does not exist in the sources, and re-persisting the caches
> cleared it from the warm runs too. `-r` now re-serialises by default, so plain `arend -r` is the fix;
> the skills' `-r --serialize` advice is stale in the flag but right in the remedy.

> **Toolchain note.** `--serialize` no longer exists: serialization is on by default in CLI 1.12 and
> the opt-out is `--no-serialize`. §7 below and the `arend-formalize` / `arend-prove` skills still
> describe the old opt-in flag.

### Already proved, by file

**A1** — matching (bottleneck) pseudometric on `Array X n` for `X : PseudoMetricSpace`, plus Sₙ as an
explicit array. The payoff:
`MetricCompletion (X : PseudoMetricSpace) : CompleteMetricSpace` takes a *pseudo*metric to a genuine
*metric* space, so it performs the Sₙ quotient **and** Richman's completion in one step — no quotient
type, no invariance obligations. It is filter-based (`RegularCauchyFilter`), i.e. exactly the
choice-free completion Richman had to build by hand as "locations".

**A2** — `approx-factor`: every monic `p` of degree `n` has, for every `eps > 0`, an `r : Array
Complex n` with `∏(X − rᵢ)` within `eps` of `p` coefficientwise. Choice-free: the only analytic input
is `fta-approx`, itself axiom-free. Deliberately *not* derived from `FTA`, which would be shorter but
would import `adchoice`.

**A3.1** — `nearFactor-bound`: a Cauchy-style a priori bound `B` on the roots of any
near-factorisation, with `1 <= B`.

**A3.2** — `nearFactor-proximity`: for every `eta > 0` some `delta > 0` puts every `r i` within `eta`
of the `s`-multiset, for any two `delta`-near factorisations. Stated as a bound on `minDist`, not as
`∃ j` — see the note in §3.

---

## 3. A3.3 — the shrinking lemma (**proved**)

```arend
\lemma nearFactor-match {n : Nat} (p : Poly ComplexField) (deg : degree< p (suc n))
                        (monic : polyCoef p n = 1) {eps : Real} (eps>0 : 0 < eps)
  : ∃ (delta : Real) (0 < delta) (\Pi (r s : Array Complex n)
      -> (\Pi (j : Nat) -> cabs (polyCoef (prodLin r) j - polyCoef p j) < delta)
      -> (\Pi (j : Nat) -> cabs (polyCoef (prodLin s) j - polyCoef p j) < delta)
      -> matchDist {ComplexNormed} r s < eps)
```

Two near-factorisations of the same `p` are close **in the matching metric**. It is what makes the
approximate-factorisation sets a *Cauchy* filter, and it is the precise sense in which multisets fix
what single roots cannot — the multiset of roots is unique, so its approximate-solution sets shrink,
whereas `{z : cabs (polyEval p z) < eps}` stays a union of one blob per root at every accuracy.

### The key move: drop `p` from the induction

The plan below (greedy deflation) is what was done, but the accuracy bookkeeping of item 3 never had
to be paid. Deflating replaces `p` by `deflate p (r 0)`, which depends on `r`, so a `p`-indexed
induction hypothesis cannot be instantiated before `r` is in hand — *that* is what forces an explicit
`n`-level epsilon chase. Keeping only what the argument actually uses gives

```arend
\lemma match-bounded {n : Nat} {B : Real} (B>=1 : 1 <= B) {eps : Real} (eps>0 : 0 < eps)
  : ∃ (delta : Real) (0 < delta) (\Pi (r s : Array Complex n)
      -> (\Pi (i : Fin n) -> cabs (r i) <= B)
      -> (\Pi (i : Fin n) -> cabs (s i) <= B)
      -> (\Pi (k : Nat) -> cabs (polyCoef (prodLin r) k - polyCoef (prodLin s) k) < delta)
      -> rawDist {ComplexNormed} r s < eps)
```

— no polynomial anywhere, only "the two products are close **to each other**, and both root arrays
are bounded". `delta` now depends on `(n, B, eps)` alone, all fixed before `r` and `s` are seen, so
the induction closes over itself and the `n`-fold root degradation of the modulus is *produced by
the existential* rather than computed by hand. Two simplifications fall out: `rawDist` rather than
`matchDist` (the hypotheses are symmetric, so `<_join-univ` plus a second application covers it),
and A3.2 applies after the same `p`-ectomy (`proximity-bounded`).

`nearFactor-match` is then ten lines: A3.1 gives `B` for both arrays and `coefDiff-bound` turns
two-sided `delta`-nearness to `p` into `2*delta`-nearness to each other.

### Dead route — do not re-attempt

**A3.3 does not follow from A3.2.** Two-sided proximity does not bound the matching distance:

> `r = {0,0,1}` and `s = {0,1,1}`. Every `r i` is at distance **0** from some `s j`, and every `s j` at
> distance **0** from some `r i`. Their matching distance is **1**.

The classical product argument (`∏_j |r_i − s_j| = |polyEval (prodLin s) (r i)|` is small, so the
minimum factor is small) delivers exactly proximity and nothing more. Multiplicities must be counted.
This counterexample is recorded in the lemma's doc comment.

### The route taken

A **greedy deflation induction** on `n`: match `r 0` to its nearest `s j`, remove both entries, and
recurse on the deflated factorisations. What it needed, as built (440 lines across four files):

1. **Array surgery — mostly already in the library.** `Data/Array.ard:566` already has the
   length-indexed `skip (l : Array A (suc n)) (k : Fin (suc n)) : Array A n`, with `skip.newIndex`,
   `skip-index`, `skip_0`, `skipExt`; `Set/Fin.ard` has the companion index maps `sface` / `skip`
   with `sface-inj`, `sface-skip`, `sface_skip`. Only the bridge `skip-sface : skip l k i =
   l (sface k i)` was new (4 lines). The `prodLin` side is `prodLin-skip`, by induction on the index:
   one `*-comm` at the head, one `*-assoc` per level.
2. **Deflation keeps things close** — `prodLin-deflate-close`, the substantive estimate.
   `prodLin-deflate-id` turns the deflated difference (multiplied back by `X - r 0`) into the
   original difference plus one *constant-multiple* correction `prodLin (skip s j) * padd pzero
   (s j - r 0)`, whose coefficients `polyCoef_*-right` reduces to `|r 0 - s j|` times a coefficient
   bound; `prodLin-coefBound` supplies that bound as `(1 + B)^m`; and `deflate-coefBound` divides
   `X - r 0` back out at a cost of `powSum |r 0| (suc m)`, which `powSum-mono` replaces by the
   array-independent `powSum B (suc m)`.
3. **Accuracy bookkeeping — not needed.** See *the key move* above: the existential in
   `match-bounded` does it. The step is four nested `split-eps`/`shrink` calls choosing, in order,
   the IH threshold, the IH's `delta'`, the loss budget `q`, and the matching accuracy `eta`.
4. **Assembly into `matchDist`.** `extendMatch j f` sends `0 ↦ j` and `suc i ↦ sface j (f i)`;
   `extendMatch-inj` is injectivity (three cases, `sface-skip` for the two mixed ones) and
   `maxDist-extend` bounds its cost by the matched pair's distance and the shortened pairing's cost.
   Then `rawDist-cond`, and `<_join-univ` for the symmetrised `matchDist`.

Two shared extraction lemmas underpin the whole thing, both instances of one induction:

```arend
\lemma bigMeet-lt {l : Array Real} {x c' c : Real} (h : Big (∧) x l <= c') (lt : c' < c)
  : (x < c) || ∃ (i : Fin l.len) (l i < c)
```

`rawDist-lt` (extract an injective pairing from a small `rawDist`) and `minDist-index` (extract the
nearest index from a small `minDist`) are both corollaries. The `<=`-hypothesis against a *strictly*
larger target is what lets the recursion reuse one gap; with a strict hypothesis each entry would
need its own threshold and the slack would have to be split `l.len` ways. **This is also §5's
step 4**, the step whose constructivity was flagged as non-obvious — it is now proved.

### What is already available for it

All in `Spectrum.ard` unless noted, all proved:

| Name | Statement |
|---|---|
| `linProd`, `polyEval_prodLin` | `polyEval (prodLin r) z = ∏_i (z − r i)` |
| `linProd-root`, `prodLin-root` | each `r i` is a root of `prodLin r` |
| `absProd`, `cabs_linProd`, `absProd>=0` | the product of distances |
| `minDist`, `minDist>=0`, `minDist-cond` | minimum distance to an array's entries |
| `pow<=absProd`, `pow_minDist<=absProd` | `c^n <= ∏` when `c` is below every factor |
| `minDist-small` | `∏ < eta^n ⟹ minDist < eta`, via `pow_<-cancel` |
| `cabs_polyEval-bound` | `\|polyEval q z\| <= Σ_k \|coef q k\| · \|z\|^k` |
| `cabs_polyEval-bound1`, `cabs_polyEval-coefMax` | the same with a uniform coefficient bound |
| `cabs_polyEval-monic-lower` | `\|p(z)\| >= \|z\|^n − \|q(z)\|` for `q = p − X^n` |
| `degree<-monic-diff` | two monic degree-`n` polys differ in degree `< n` |
| `cabs_polyEval-diff-at-root` | at a root of `a`, `\|polyEval b z\| = \|polyEval (a−b) z\|` |
| `powSum`, `powSum>=0`, `powSum-bound` | `Σ_{k<n} B^k`, and `<= n · B^{n−1}` for `B >= 1` |
| `coefMax`, `coefMax>=0`, `coefMax-cond` | uniform bound on the first `m` coefficients |
| `cabs-rev-triang`, `cabs_-comm`, `split3` | modulus arithmetic |
| `le-cancel-right` | cancel a positive right factor from `<=` |
| `nat<suc` | `k < suc m ⟹ k <= m` |
| `BigSum-scale` | `Σ_k c·a_k = c·Σ_k a_k` |
| `rawDist-cond`, `rawDist-univ` (A1) | `rawDist` is the greatest lower bound over permutations |

**Why `minDist` rather than `∃ j`.** A bound on a *product* does not constructively say which factor
is small — extracting `∃ j, |z − s j| < eta` would require deciding which `j`. Bounding the minimum is
extractable (`min^n <= ∏ < eta^n`, then `pow_<-cancel`), and the minimum is what `matchDist` is built
from anyway. Keep this shape in A3.3.

---

## 4. A4 — the spectrum (**proved**)

All in `src/Arith/Complex/Spectrum/Point.ard`, 402 lines, no axioms.

```arend
\func spectrum {n : Nat} (p : Poly ComplexField) (deg : degree< p (suc n))
               (monic : polyCoef p n = 1) : ComplexMultiset n
  => sregCF (specFilter p deg monic)

\lemma spectrum-char {m : Nat} (p : Poly ComplexField) (deg : degree< p (suc (suc m)))
    (monic : polyCoef p (suc m) = 1) (z : Complex)
  : mem (spectrum p deg monic) z <-> polyEval p z = 0
```

No `ComplexPseudoMetric` needed to be built: `PseudoNormedAbGroup` carries
`\default dist x y : Real => norm (x - y)` and `\extends PseudoMetricSpace`, so `ComplexNormed` already
*is* one, and the base distance is `cabs` of the difference **definitionally** — so
`ldist-triang {ComplexNormed}` *is* the complex triangle inequality with no bridging, and no
`ExUpperReal` detour appears anywhere in A1–A4. (The `ComplexMultiset` / `ldist_cabs` / `multisetOf`
probes this section used to cite were removed from `ApproxFactor.ard` long ago; don't look for them.
`ComplexMultiset` now lives in `Point.ard`.)

### The key move: membership is a predicate on filters, not a lifted function

The plan below said step 4 needed `completion-lift` / `dense-lift`. **It does not, and that was the
whole risk in this section.** Points of `Multiset X n = MetricCompletion (MultisetPseudoMetric X n)`
*are* `StronglyRegularCauchyFilter (Array X n)`, so any predicate on filters is automatically
well-defined on the completion — no lift, no invariance obligation, no 1-Lipschitz side condition to
discharge before the definition can even be made:

```arend
\func mem {m : Nat} (mu : ComplexMultiset (suc m)) (z : Complex) : \Prop
  => \Pi {eps : Real} -> 0 < eps -> mu (nearRootSet z eps)
```

This is equivalent to "the extension of `minDist _ z` to the completion vanishes at `mu`", but that
equivalence is never needed.

### Steps, as built

1. **The filter** — `specSetFilter p`, generated by `nearFactorSet p delta`. `filter-meet` is where
   nesting in `delta` is used (a meet of thresholds is a threshold); the rest is trivial. ~8 lines.
2. **Proper and Cauchy** — `specFilter`: `isProper` is A2 (`approx-factor`), `isCauchyFilter` is
   `cauchyFilter-metric-char.2` fed A3.3 (`nearFactor-match`) — the ball around any single
   `delta`-near factorisation. ~14 lines.
3. **The spectrum** — `sregCF (specFilter …)`, typed at `ComplexMultiset n`. One line, and the fact
   that it typechecks *is* the carrier identification: `MetricCompletion`'s own completeness is never
   invoked, so nothing analytic is spent here.
4. **`minDist-lip`** — `minDist _ z` is 1-Lipschitz for `rawDist`. For an injective pairing `f`,
   `bigMeet-cond` + `ldist-triang` + `maxDist-cond` bound `minDist r z` by `|z − s j| + maxDist s r f`
   for every `j` *including the base index*, so `bigMeet-univ` gives the bound by `minDist s z`, and
   `rawDist-univ` passes to the infimum. Two `linarith`s, ~15 lines. `minDist-lipM` symmetrises with
   `join-right`. Also `minDist-indep`: the base index of `minDist` is redundant, which is what licenses
   pinning it at `0` in `mem`.
5. **`absProd-factor`** — `prodLin-skip` splits `∏(z − sᵢ)` as `(z − s_j)` times the deflated product
   at `z`, bounded by `prodLin-coefBound` through `cabs_polyEval-boundD`. The upper bound complementing
   `pow<=absProd`. Needed again by Phase B step 8 (see §5). ~14 lines.
6. **The characterisation.** `root-nearRoot` (⟸ core): a root makes `∏(z − rᵢ) = |p(z) −
   ∏(X−rᵢ)(z)|` small, and `minDist-small` converts a small product to a small minimum — *no root
   bound needed*, only `powSum |z| n`. `nearRoot-root` (⟹): split `p = (p − ∏) + ∏`,
   `cabs_polyEval-boundD` on the first summand and `absProd-factor` on the second, both under `c/2`,
   with `c` arbitrary via `below-all-positive`. Then `root-mem` / `mem-witness` move these across the
   regularisation, and `spectrum-char` is the pair. `spectrum-prodLin` is the sanity check that each
   `r i` is a member of `spectrum (prodLin r)`.

### The one nontrivial obstacle, and how it was dodged

`spectrum p` is `sregCF (specFilter p)`, which is *smaller* than `specFilter p`. The ⟹ direction only
needs `sregCF_<=` and is free; the ⟸ direction has to prove membership in the **regularisation**, and
`nearRootSet` is not one of the filter's generators. Doing that through `s<=<` directly would require
`nearFactorSet delta' s<=* nearFactorSet delta`, i.e. continuity of `r ↦ ∏(X − rᵢ)` — a lemma we
otherwise do not need. The way around is a *ball* criterion:

```arend
\lemma sregCF-mem {X : PseudoMetricSpace} {F : WeaklyCauchyFilter X} {U : Set X} {eps : Rat}
                  (eps>0 : 0 < eps) (h : \Pi (x : X) -> F (OBall eps x) -> OBall eps x ⊆ U)
  : sregCF F U
  => \lam {G} G<=F => \case (cauchyFilter-metric-char {X} {G}).1 G.isCauchyFilter eps>0 \with {
       | inP (x, Gb) => filter-mono Gb (h x (G<=F Gb))
     }
```

Every `G ⊆ F` is still Cauchy, hence contains *some* `eps`-ball, and that ball is one of `F`'s own
sets — so it suffices that every `eps`-ball belonging to `F` already sits inside `U`. Four lines, no
`s<=<` reasoning, and it belongs beside `sregCF_<=` in
`Topology/CoverSpace/StronglyComplete.ard` (see §6). In `root-mem` the ball radius is a rational
`e < eps/3`, because `minDist-lipM` plus `matchDist-triang` costs two radii to move from the witness
`r1` to an arbitrary point of the ball.

---

## 5. Phase B — `FTA` from AC_ω instead of `adchoice` (**proved**)

All in `src/Arith/Complex/FTA/Choice.ard`, 256 lines, `acomega` the only axiom.

```arend
\lemma FTA-choice {n : Nat} (n>0 : 0 < n) (p : Poly ComplexField)
                  (deg : degree< p (suc n)) (monic : polyCoef p n = 1)
  : ∃ (z : Complex) (polyEval p z = 0)
```

— character for character `FTA`'s statement, from a strictly weaker hypothesis. This is the payoff
that makes Phase A worth finishing for its own sake, beyond Richman's theorem.

### AC_ω had to be formulated

The library had **no** countable choice. `achoice` is choice over an arbitrary `\Set`; `adchoice` is
*full dependent choice* — the `Nat` in its signature indexes the family, it does not weaken the
principle, and its doc comment's "dependent choice over `Nat`" invites exactly that misreading. So
`acomega` is new (`Logic/Classical.ard`), with `acomega.fromChoice` deriving it from `achoice` to show
it is a weakening rather than new strength:

```arend
\axiom acomega {A : Nat -> \Set} (h : \Pi (i : Nat) -> TruncP (A i)) : TruncP (\Pi (i : Nat) -> A i)
```

The three now sit in strict order `acomega < adchoice < achoice`, and each gap is load-bearing:
`achoice` yields excluded middle (`achoice.lemFromChoice`) and the other two do not; `adchoice` lets
each choice depend on the previous one and `acomega` does not (AC_ω ⇏ DC, Jensen).

### The chain, as built

| Step | | Choice? |
|---|---|---|
| 1 | `StepA p k` — at accuracy `2^{ -k}`, a near factorisation (**A2**) *together with* a threshold `K` past which any two near factorisations are `2^{ -k}`-close (**A3.3**) | free per `k` |
| 2 | `acomega (StepA-inh …)` → `s : \Pi k -> StepA p k`, giving both the sequence and the thresholds | **AC_ω** |
| 3 | `idx K` — `idx K 0 = K 0`, `idx K (suc k) = suc (idx K k) ∨ K (suc k)`: strictly increasing and past every threshold, so stage `k`'s coherence applies to `(idx K k, idx K (suc k))` | free (computed) |
| 4 | `StepB s k` — an injective pairing of `seqR s k` with `seqR s (suc k)` costing `< 2^{ -k}`, from `rawDist-lt` at `rawDist <= matchDist < 2^{ -k}` | free per `k` |
| 5 | `acomega (StepB-inh s)` → the alignment permutations `sigma k` | **AC_ω** |
| 6 | `tau sig (suc k) i = sig k (tau sig k i)`; `thread A k = seqR s k (tau … k 0)` has `\|thread (suc k) − thread k\| < 2^{ -k}` | free (computed) |
| 7 | `geom-increments-cauchy` → `IsConvergent (thread A)`, then `limit` | free |
| 8 | `thread-root` = **A4's `nearRoot-root`**, fed `tRoots A k` for `k` past both moduli | free |

**Step 3 is what keeps the count at two applications of `acomega`.** Bundling the threshold into
step 1's family — rather than choosing it separately after seeing the sequence — is the only reason a
third application is not needed. Both components of `StepA p k` are available choice-free at each `k`
(A2 gives the factorisation, A3.3 the threshold), so they can be chosen simultaneously.

**Step 6 was §5's top predicted risk and turned out to be free.** The worry was that composing the
permutations and re-indexing would make the epsilons drift. It does not, because `tau` is defined by
recursion on the *outside*: `tau sig (suc k) 0` **reduces** to `sig k (tau sig k 0)`, which is exactly
the index stage `k`'s pairing sends `tau sig k 0` to. So the increment bound is a single instance of
`maxDist-cond` at that index, with no index arithmetic at all. Had `tau` been defined the other way
round (`tau sig (suc k) i = tau sig k (sig … i)`) this would not reduce and the step would have cost
real work.

**Step 8 cost nothing.** A4's `nearRoot-root` takes precisely "for every pair of accuracies, some
factorisation that is `delta`-near `p` and has an entry within `eta` of `z`", and `tRoots A k` for `k`
past both thresholds is that, its near entry being `thread A k`. So Phase A's estimate is reused
verbatim and the continuity lemma §5 originally budgeted for is never needed.

### Why this works where the Kneser route does not

The Kneser route genuinely needs DC, and §1's claim about it stands. The difference is structural:

> In the Kneser route, coherence between consecutive approximations must be **built into the
> construction** — that is estimate (4) — which makes the choices dependent, hence DC. With multisets,
> coherence is a **theorem** (A3, from uniqueness of the root multiset), so the family in step 1 is
> non-dependent and AC_ω applies.

A3 converts an obligation on the construction into a fact about *any two* approximations. That is the
whole trick, and it is why the multiset reformulation buys something beyond Richman's own choice-free
statement.

Two corollaries worth recording:

- **AC_ω's role is precisely to turn the filter into a sequence.** The filter that A2 + A3 give is
  choice-free but cannot be aligned; a sequence can. That is the entire content of the two `acomega`
  applications.
- The obstructions recorded under *The choice-free variant* below both dissolve: the incoherence of an AC_ω choice function is
  *repaired* by step 5's alignment, and nothing in the chain ever decides whether a diameter is zero.

Also worth recording, now that both are formalised: **A4 is not needed for Phase B, but A4's estimates
are.** The spectrum as a point of the completion plays no role — `Choice.ard` never mentions
`spectrum` — yet `nearRoot-root` and `absProd-factor`, written for A4's characterisation, are exactly
what closes step 8. The two phases share the analysis and not the object.

**Never needed: ℂⁿ's completeness.** Step 7 of the original plan wanted the product space complete.
Only one root is wanted, so only the `0`-th thread has to converge, and that is a sequence in ℂ.

### Changes to `FTA.ard`

- **`geom-increments-cauchy` factored out** of `increments-uniform-cauchy`:
  `{r K : Real} (0 <= r) (r < 1) (0 <= K) (∀ i, cabs (z (suc i) − z i) < pow r i * K) : IsConvergent z`.
  The last ~15 lines of the existing proof with `r`, `K` abstracted; `increments-uniform-cauchy` is now
  a 3-line instantiation at `r = ⁿ√q`, `K = ⁿ√c`, and Phase B uses `r = 1/2`, `K = 1`.
- **`dist_U` un-privated**, and `dist_U-conv` added beside it (reading a `Complex` limit statement back
  as a bound on `cabs`).
- **`NatDirectedSet` un-privated** — needed wherever `IsConvergent` is used on a `Nat`-indexed sequence.

### Formalisation notes worth keeping

- **Bundle the two choices in a record.** `m` and `p` occur in `seq`'s type only *under* a `\Pi`, so
  nothing downstream can infer them from `seq`; every consumer failed with `Cannot infer parameter`.
  Making `Threading` a `\record` with `m`, `p` as fields turns them into projections of a variable and
  all inference becomes immediate. Cheaper than threading four explicit arguments everywhere.
- **`rawDist-lt` needs its implicits pinned.** Supplying `h` as a typed `\have` is not enough: the
  annotation gets normalised to `Big (∧) … <= …`, which no longer matches `rawDist {?X} {len {?r}} ?r ?s`.
  Pass `{ComplexNormed} {suc m} {r} {s}` explicitly.
- **`2^{ -k}` in a doc comment, always with the space.** `2^{-k}` opens a nested block comment
  (arend-quirks §19) and silently swallowed everything from `StepB` to `thread-conv`; the diagnostics
  were thirteen `Cannot resolve reference` errors ~40 lines below the cause.

**Two `acomega` applications suffice** if the first family is bundled: choose, for each `m`
simultaneously, a `2^{-m}`-near factorisation `r m` (A2) *and* a threshold index `K m` beyond which any
two near factorisations are `2^{-m}`-close (A3.3) — both choice-free per `m`, so one family. Then
compute a strictly increasing `idx` with `idx m >= K m`, and use a second `acomega` for the alignment
permutations, each inhabited by `rawDist-lt` at `h : rawDist <= matchDist`, `lt : matchDist < 2^{-m}`
— exactly the `<=`-against-strictly-larger shape `bigMeet-lt` was stated for.

Cost: a third route to `FTA` beside the existing `adchoice` one, reusing A2 + A3 + A4's
`absProd-factor`. Prerequisites ~25 lines, then steps 1–8 perhaps 250 lines.

### The choice-free variant — still open

Getting from `spectrum p` to `∃ (z : Complex) (polyEval p z = 0)` with **no** axiom at all is a
different and harder question, and I have no route I trust. Recorded so the obstructions are not
rediscovered.

The natural architecture is induction on `n` using the diameter of the spectrum: if `diam mu = 0` the
single point is `avg mu = −a_{n−1}/n` (canonical, choice-free); if `diam mu > 0` split into separated
clusters and recurse. `Real.LU-located` gives, at each scale `k`, the decision
`diam mu < 2^{-k}` **or** `diam mu > 2^{-k-1}`, monotone in `k`, so there is at most one transition.

Two things go wrong, and they are two faces of one obstruction:

- **Incoherence.** A choice function hands you `s k ∈ A k` with no coherence. Past the transition the
  `A k` are equal but `s` may hop between distinct members of `mu`, so it is not Cauchy and has no
  limit. Making the family nested turns `⋂ A k` into the goal itself (circular); asking for a coherent
  tail is unprovable below the transition; and chasing nested *clusters* instead of points reintroduces
  exactly the dependence that made this DC in the first place. (§5's route escapes this by aligning
  with a *second* choice, which is unavailable if no choice at all is assumed.)
- **Omniscience, not choice.** The clean argument wants `diam mu = 0 ∨ diam mu > 0` so it can branch
  once. That is an omniscience principle; **no amount of choice supplies it**. The binary scale exists
  to dodge it, and dodging it is what produces the incoherence above.

This is consistent with Richman: his antipodal principle shows FTA *implies* a weak countable-choice
principle, so a fully choice-free proof of the plain existential is impossible. Schuster's choice-free
theorem needs "at most one root, uniformly" — exactly the `n = 1` / `diam = 0` case that is already
easy here.

---

## 6. Migrations owed

All parked on leaf modules to keep the edit loop fast. None is blocking.

| Definitions | Current home | Belongs in |
|---|---|---|
| `allMaps`, `allMaps-complete`, `perms`, `perms-complete`, `perms-inj`, `IsInjFin`, `injDec`, `id-inj`, `comp-inj` | `Multiset.ard` | `Data/Array/` |
| `Dec_->` | `Multiset.ard` | `Set.ard`, beside `Dec_\|\|` |
| `bigMeet-cond`, `bigMeet-univ`, `bigJoin-base`, `bigJoin-cond`, `bigJoin-univ` | `Multiset.ard` | `Order/Lattice.ard` |
| `polyCoef_*padd1`, `degree<_monomial`, `polyEval_monomial`, `polyCoef-minus` | `ApproxFactor.ard`, `Spectrum.ard` | `Algebra/Ring/Poly.ard` |
| `degree<-suc`, `mul_padd1`, `degree<_*padd1` | `FTA.ard` | `Algebra/Ring/Poly.ard` |
| `pow_<1-decay`, `pow_<-cancel` | `FTA.ard` | `Arith/Real/Field.ard`, `Arith/Real/Root.ard` |
| `NatDirectedSet` | `FTA.ard` | `Order/Directed.ard` or `Arith/Nat.ard` |
| `nat<suc` | `Spectrum.ard` | `Arith/Nat.ard` |
| `zro-minus`, `add-sub-cancel`, `split3`, `add-sub-split` | `FTA.ard`, `ApproxFactor.ard`, `Spectrum.ard` | `Algebra/Group.ard` |
| `bigMeet-lt`, `bigJoin-lt` | `Multiset.ard` | `Order/Lattice.ard` or `Order/Biordered.ard` |
| `sregCF-mem` | `Spectrum/Point.ard` | `Topology/CoverSpace/StronglyComplete.ard`, beside `sregCF_<=` |
| `below-all-positive` | `Spectrum/Point.ard` | `Arith/Real/Field.ard` (or generic, on `LinearOrder` with a zero) |
| `polyEval_padd1` | `Spectrum/Point.ard` | `Algebra/Ring/Poly.ard` |
| `sub-add` (`x = (x − y) + y`) | `Spectrum/Point.ard` | `Algebra/Group.ard`, with `split3` etc. |
| `minDist-indep`, `minDist-lip`, `minDist-lipM` | `Spectrum/Point.ard` | fine where they are; `minDist` is next door |
| `geo`, `geo>0`, `geo-mono`, `geo<=1`, `geo-small` | `FTA/Choice.ard` | `Arith/Real/Field.ard` — a general `2^{-k}` schedule |
| `idx`, `idx>=self`, `idx>=K`, `idx>=K'` | `FTA/Choice.ard` | `Arith/Nat.ard` — nothing complex-specific about it |
| `geom-increments-cauchy`, `dist_U`, `dist_U-conv` | `FTA.ard` (now public) | `Analysis/Limit.ard` / `Topology/NormedAbGroup.ard` |
| `NatDirectedSet` | `FTA.ard` (now public) | `Order/Directed.ard` or `Arith/Nat.ard` — same row as above, now blocking less |
| `extendMatch`, `extendMatch-inj` | `Multiset.ard` | `Data/Array/` or `Set/Fin.ard` |
| `degree<_padd-tail`, `cabs_polyEval-boundD` | `Spectrum.ard` | `Algebra/Ring/Poly.ard` (the first) |
| `padd1-root`, `padd1-diff`, `deflate_*padd1` | `ApproxFactor.ard` | `Algebra/Ring/Poly.ard` |
| `powSum`, `powSum>=0`, `powSum-mono`, `powSum-bound` | `Spectrum.ard` | `Arith/Real/Field.ard` |

Several were un-privated during this work purely so a second module could use them; the `\private`
markers should be revisited when they move.

---

## 7. Practical notes

**Use the daemon.** `arend -d` in `arend-lib/` starts a long-lived JVM holding a warm `ArendServer`
(this is what "server" means — there is no separate mode). Measured on `ApproxFactor.ard`: **0.59 s
warm vs 20.5 s cold**. Two quirks: goals surface on the *next* request, never with `Expected type:` /
`Context:` lines, so the loop is edit → check errors → check again for goals; and never hand-delete a
`.arc` to force re-elaboration (it poisons unrelated importers — use `-r`).

**The daemon reports a resolution error only once — filed as
[arend-lang/Arend#138](https://github.com/arend-lang/Arend/issues/138).** Run 1 after a file's mtime
changes reports `Cannot resolve reference` / `Duplicate name`, marks the module `[✗]` and counts it.
Every run after that lists it as `[ ]` — the *healthy* marker — prints nothing, omits the
`Number of modules with errors:` line and **exits 0**, while the source is still broken. Typechecking
errors are re-reported every run, so the two classes disagree. `arend -r` inside the daemon does not
recover; `touch` re-arms it for exactly one run; `arend --no-daemon` is always right and exits 1.

**And never filter typecheck output by the `--- Typechecking <Module> ---` banner.** Resolution errors
are printed *before* it, so `arend M 2>&1 | grep -A20 'Typechecking M'` hides even the one report you
get. This is what actually happened while building A4: a new module used `skip`, `skip-sface` and
`sface` with neither `Data.Array` nor `Set.Fin` imported, and several rounds looked clean — first
because the banner-grep cut the errors off, then because the daemon had stopped emitting them. The
full-library run showed seven `Cannot resolve reference` errors and, behind them, two genuine
`Cannot infer parameter` errors in `absProd-factor`.

So: iterate with the daemon for speed, but never conclude "this module is finished" from a run that
followed an earlier run on the same content, and read the whole output rather than a slice of it. The
run that decides a milestone is still `arend --daemon-stop && arend --no-daemon -r`.

**Verify cold before believing a milestone.** `arend --daemon-stop` then `arend --no-daemon -r`
(~2 min, re-persists everything). A plain `--no-daemon` run with current caches just deserialises in
~4 s and checks nothing.

**Traps this development hit repeatedly**, all now recorded in the skills/memory but worth having here:

- Concrete `Complex` arithmetic computes on `re`/`im`, so `equation.cRing` and `rewrite` both fail on
  it, and `cabs_+` / `cabs_*` / `cabs_-` cannot infer their implicits. State algebra over an abstract
  `{R : CRing}` / `{G : AddGroup}` with all operands as *variables* and instantiate.
- `linarith` reasons over an ordered **field**: it cannot use `Nat` integrality (`n < k < suc n` looks
  satisfiable) and cannot expand products of non-constants.
- Over-specifying implicits is as dangerous as under-specifying — a miscounted `{_}` put an argument in
  an *interval* position. Pin them with typed `\have` steps instead.
- `Array` has definitional eta: `\new Array A (suc m) f` is convertible to `f 0 :: …`, so `BigSum`/`Big`
  reduce on non-constructor arrays, and `map f as` is interchangeable with the explicit `\new Array`
  form. (I wrongly assumed otherwise twice.)
- `peq` (the `Poly` path constructor) works in a `*>` chain but not under `rewrite`.
