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

It also has a second payoff, which is the practical reason to finish it. Phase A's shrinking lemma
turns the existing `FTA` statement into a consequence of **plain countable choice** rather than
dependent choice (§5) — strictly weaker than what `FTA.ard` assumes today, and weaker than `achoice`,
which additionally gives excluded middle.

---

## 2. Status

| Piece | File | Lines | Goals | Axioms |
|---|---|---|---|---|
| A1 matching pseudometric | `src/Topology/MetricSpace/Multiset.ard` | 237 | 0 | none |
| A2 approximate factorisation | `src/Arith/Complex/ApproxFactor.ard` | 268 | 0 | none |
| A3.1 root bound | `src/Arith/Complex/Spectrum.ard` | — | 0 | none |
| A3.2 product bound | `src/Arith/Complex/Spectrum.ard` | — | 0 | none |
| **A3.3 matching** | `src/Arith/Complex/Spectrum.ard` | — | **1** | none |
| **A4 the spectrum** | not started | — | — | — |
| Phase B — `FTA` from AC_ω | not started; route in §5 | — | — | — |

`Spectrum.ard` totals 433 lines. Phase A is 938 lines so far. The library typechecks with 0 errors;
the only goals anywhere are `nearFactor-match` and the pre-existing `Topology.Locale.HausdorffLocale`
one.

Verified independently of the warm daemon and of the binary caches by `arend --no-daemon -r`
(full from-source rebuild, 394 modules): **0 errors, 1m46s**, 2 modules with goals.

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

## 3. A3.3 — the remaining lemma

```arend
\lemma nearFactor-match {n : Nat} (p : Poly ComplexField) (deg : degree< p (suc n))
                        (monic : polyCoef p n = 1) {eps : Real} (eps>0 : 0 < eps)
  : ∃ (delta : Real) (0 < delta) (\Pi (r s : Array Complex n)
      -> (\Pi (j : Nat) -> cabs (polyCoef (prodLin r) j - polyCoef p j) < delta)
      -> (\Pi (j : Nat) -> cabs (polyCoef (prodLin s) j - polyCoef p j) < delta)
      -> matchDist {ComplexNormed} r s < eps) => {?}
```

This is the shrinking lemma: two near-factorisations of the same `p` are close **in the matching
metric**. It is what makes the approximate-factorisation sets a *Cauchy* filter, and it is the precise
sense in which multisets fix what single roots cannot — the multiset of roots is unique, so its
approximate-solution sets shrink, whereas `{z : cabs (polyEval p z) < eps}` stays a union of one blob
per root at every accuracy.

### Dead route — do not re-attempt

**A3.3 does not follow from A3.2.** Two-sided proximity does not bound the matching distance:

> `r = {0,0,1}` and `s = {0,1,1}`. Every `r i` is at distance **0** from some `s j`, and every `s j` at
> distance **0** from some `r i`. Their matching distance is **1**.

The classical product argument (`∏_j |r_i − s_j| = |polyEval (prodLin s) (r i)|` is small, so the
minimum factor is small) delivers exactly proximity and nothing more. Multiplicities must be counted.
This counterexample is recorded in the lemma's doc comment.

### The viable route

A **greedy deflation induction** on `n`: match `r 0` to its nearest `s j`, remove both entries, and
recurse on the deflated factorisations. Concretely it needs:

1. **Array surgery.** Remove an entry at a given index from `Array Complex (suc m)`, yielding
   `Array Complex m`, and relate `prodLin` of the result to `prodLin` of the original. `Data/Array.ard`
   has `remove`/`keep`/`filter` but they are predicate-driven and length-unindexed; a
   remove-at-`Fin`-index with a length index is probably new.
2. **Deflation keeps things close.** If `|r 0 − s j|` is small, then `prodLin (r minus r 0)` and
   `prodLin (s minus s j)` are still near-factorisations of a common polynomial, with a controlled
   loss of accuracy. This is the substantive estimate.
3. **Accuracy bookkeeping.** The loss compounds over `n` levels and each level's proximity bound comes
   from A3.2 with a root extraction, so the modulus degrades like a repeated `n`-th root. `delta` must
   be chosen at the top for the whole induction.
4. **Assembly into `matchDist`.** The greedy matching is a permutation; feed it to
   `rawDist-cond` (already proved: `rawDist r s <= maxDist r s f` for any injective `f`), then
   `join-univ` for the symmetrised `matchDist`.

Estimate: 150–250 lines. This is the hardest single piece in Phase A.

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

## 4. A4 — the spectrum

Not started. The interface is already verified to elaborate (probes in `ApproxFactor.ard`):

```arend
\func ComplexMultiset (n : Nat) : CompleteMetricSpace => Multiset ComplexNormed n
\lemma ldist_cabs (x y : Complex) : ldist {ComplexNormed} x y = cabs (x - y) => idp
\func multisetOf {n : Nat} (r : Array Complex n) : ComplexMultiset n => pointSCF r
```

No `ComplexPseudoMetric` needs building: `PseudoNormedAbGroup` carries
`\default dist x y : Real => norm (x - y)` and `\extends PseudoMetricSpace`, so `ComplexNormed` already
*is* one, and the base distance is `cabs` of the difference **definitionally** (hence no
`norm`↔`cabs` bridging and no `ExUpperReal` detour anywhere in A1–A3).

### Steps

1. **The filter.** Generate a `SetFilter (Array Complex n)` from
   `S_delta := {r | ∀ j, cabs (polyCoef (prodLin r) j − polyCoef p j) < delta}`. Inhabited by A2
   (`approx-factor`), nested in `delta`. Filter, not sequence — no choice.
2. **Cauchy.** `cauchyFilter-metric-char` (`Topology/MetricSpace.ard:334`) reduces `IsCauchyFilter` to:
   for every `eps > 0` some element of the filter sits inside an `eps`-ball. That is exactly A3.3.
3. **The spectrum.** `MetricCompletion`'s completeness turns the Cauchy filter into a point
   `spectrum p : ComplexMultiset n`. `MetricCompletion.dist-char` says `pointSCF` is isometric, so the
   embedding of each near-factorisation is within `eps` of the spectrum.
4. **Membership.** Define `z ∈ mu` by extending `dist_min r z := minDist r z` (1-Lipschitz in `r` w.r.t.
   `matchDist`) to the completion via `completion-lift` / `dense-lift`, and setting
   `z ∈ mu := (dist_min mu z = 0)`. Similarly extend `diam` if Phase B is ever attempted.
5. **The characterisation.** `z ∈ spectrum p ↔ polyEval p z = 0`. Both directions are quantitative
   consequences of `cabs_polyEval-bound` plus A3.1's root bound; neither should need new machinery.

Estimate: 150–250 lines, mostly steps 4 and 5. Lower risk than A3.3 — no new mathematical content,
but the `completion-lift` plumbing is unfamiliar territory in this development.

---

## 5. Phase B — `FTA` from AC_ω instead of `adchoice`

This is the payoff that makes Phase A worth finishing for its own sake, beyond Richman's theorem.

`FTA.ard` currently gets its sequence from `adchoice` (dependent choice over `Nat`). **A2 + A3 plus
plain countable choice look sufficient instead** — a strictly weaker axiom, and the one every school of
constructive mathematics already assumes. Note that A4 (the spectrum as a point of the completion) is
*not* needed for this; A2 and A3 are.

Assume only

```arend
\axiom acomega {A : Nat -> \Set} (h : \Pi (i : Nat) -> TruncP (A i)) : TruncP (\Pi (i : Nat) -> A i)
```

### The chain

| Step | | Choice? |
|---|---|---|
| 1 | for each `k`, `∃ (r : Array Complex n), ‖∏(X − rᵢ) − p‖ < 2^{-k}` | free — **A2, proved** |
| 2 | `acomega` on that family gives a *sequence* `r : Nat -> ℂⁿ` of approximating multisets | **AC_ω** |
| 3 | `r` is Cauchy in `matchDist`: for `k, l >= K` with `2^{-K} < delta eps`, both are `delta`-near, so `matchDist (r k) (r l) < eps` | free — **A3** |
| 4 | for each `k`, the set of permutations aligning `r k` to `r (suc k)` within `eps` is inhabited | free (see below) |
| 5 | `acomega` on *that* family gives alignment permutations `sigma k` in `S_n` | **AC_ω** |
| 6 | compose `tau k := sigma 0 ∘ … ∘ sigma (k−1)`; the relabelled sequence `k ↦ r k ∘ tau k` is **componentwise** Cauchy in ℂⁿ | free (computed, not chosen) |
| 7 | ℂⁿ is complete (tail filter + `ComplexComplete`), so the relabelled sequence has a limit `rInf` | free |
| 8 | continuity of `r ↦ ∏(X − rᵢ)` gives `∏(X − rInf i) = p`, hence `polyEval p (rInf 0) = 0` | free |

**Step 4 in detail**, since it is the step whose constructivity is not obvious. From
`Big (∧) l < eps'` with `eps' < eps` one *can* extract an index with `l j < eps`. Two-element case:
`<-comparison a {eps'} {eps}` gives `a < eps || eps' < a`, and likewise for `b`; if `eps' < a` **and**
`eps' < b` then `<_meet-univ` gives `eps' < a ∧ b`, contradicting the hypothesis. So the disjunction is
genuine, and induction lifts it to a finite `Big (∧)`. The slack `eps' < eps` is what pays for this —
you never get "the minimum is attained", only "something is below a slightly larger bound", which is
all that is needed. (`rawDist` is `Big (∧) (maxDist … id) (permCosts …)`, so both the base value and
the array entries yield an injective permutation.)

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

### Status and risks

Worked out on paper in one pass, **not formalised** — treat as a strong conjecture. In decreasing
order of risk:

1. **Step 3 depends on A3.3, still open.** Everything here is contingent on §3.
2. **Step 6's bookkeeping.** Composing permutations and showing the relabelled sequence is
   componentwise Cauchy is where the epsilons could fail to line up. `matchDist` controls `max_i`, so
   it should transfer, but it is not free.
3. **Step 8's continuity.** `ℂⁿ -> Poly` is polynomial in the coordinates, so continuity is
   unsurprising, but nothing in Phase A proves it yet.

Cost: a third route to `FTA` beside the existing `adchoice` one, reusing A2 + A3. Finish A3.3
(150–250 lines), then steps 4–8 (perhaps 200–300 lines).

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

Several were un-privated during this work purely so a second module could use them; the `\private`
markers should be revisited when they move.

---

## 7. Practical notes

**Use the daemon.** `arend -d` in `arend-lib/` starts a long-lived JVM holding a warm `ArendServer`
(this is what "server" means — there is no separate mode). Measured on `ApproxFactor.ard`: **0.59 s
warm vs 20.5 s cold**. Two quirks: goals surface on the *next* request, never with `Expected type:` /
`Context:` lines, so the loop is edit → check errors → check again for goals; and never hand-delete a
`.arc` to force re-elaboration (it poisons unrelated importers — use `-r`).

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
