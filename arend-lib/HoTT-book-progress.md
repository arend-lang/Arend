# Formalizing the HoTT book in arend-lib — progress and handover

Status as of commit `8c2a2f3c8` ("HB8.6: Hopf fiber sequence").

This file is meant to be sufficient on its own to pick the work up: it records what exists, the
conventions every new file must follow, what is deliberately *not* done and why, and the concrete
next steps with their known blockers.

Commits in this line of work are tagged `HB<chapter>.<section>` in their subject
(`HB7.1`, `HB8.1`, `HB8.2`, `HB8.3`, `HB8.4`, `HB8.6`).

---

## 1. Scope

Chapters 7 (n-types) and 8 (homotopy theory) of the HoTT book, built as a *thin numeric layer over
machinery arend-lib already had* — reflective subuniverses and modalities in
`Homotopy/Localization/`, `Omega^`/`SphereLoopEquiv` in `Homotopy/Loop.ard`, `Aut` and the algebra
class hierarchy in `Algebra/`.

The governing design constraint, and the one to keep enforcing in review:

> **No parallel theory.** Numeric connectedness must be a specialization of the modal notion, not a
> second implementation. π₁'s multiplication must be `Aut`'s path concatenation. Higher
> commutativity must come from `Omega^2-Commutative`. Morphisms must be `GroupHom`, products must be
> `ProductGroup`.

---

## 2. Conventions (follow these in every new file)

### 2.1 Index shift

arend-lib's h-level predicate is shifted one step from the book:

```text
A ofHLevel_-1+ n   is the book's (n-1)-type
Trunc_-1+ n A      is the book's (n-1)-truncation
isNConnectedType n is the book's (n-1)-connectedness
```

So `n = 0` means "proposition" / "merely inhabited", `n = 1` means "set" / "path-connected".
Consequences that have already bitten and will again:

- An `n`-type is **`Sphere n`**-null, not `Sphere (suc n)`-null — the book's `+1` cancels the `-1`.
- Join: `nconnected_join` gives `suc (m + n)` from `m` and `n`. This coincides with the book's
  `i + j + 2` only after the shift; do not transcribe book arithmetic, re-derive it.
- Always sanity-check a degenerate case by hand (`m = n = 0`) before believing an index.

The shift is documented in the header comment of `Homotopy/Connectivity.ard`. Keep it there.

### 2.2 Algebraic structure of homotopy groups

**π_{n+1} is a `Group` (multiplicative); π_{n+2} is a `CGroup`, not an `AbGroup`.**

Eckmann–Hilton does not give higher homotopy groups a different operation — it says the *same*
concatenation is commutative. `CGroup \extends Group` adds exactly `*-comm` while keeping `*`, so
`PiCGroup n X` is `| Group => PiGroup (suc n) X` plus commutativity. `AbGroup \extends AddGroup` is
a separate record whose operation is `+`, and using it would force a re-notated copy. Multiplicative
also makes `PiGroup n X => Aut (base {Omega^ n X})` a definition with no conversion.

There is no `CGroupCat` in arend-lib, but `AbGroup.fromCGroup` and `AddGroupHom.fromGroupHom` are
`\use \coerce`, so the additive/abelian view costs nothing at the use site.

### 2.3 File layout: one main theorem, machinery in its `\where`

Computation files state a single top-level theorem as an **equality of groups** (arend-lib's
`GroupCat` is univalent, so `Cat.isotoid` converts an `Iso` into `=`), with the homomorphism, the
equivalence and the `Iso` demoted into the `\where`:

```
\func PiGroup_S1.{u} : PiGroup 0 (Sphere.pointed 1) = {Group.{u}} AddGroup.toGroup IntRing
  => inv (Cat.isotoid iso)
  \where { windHom, windEquiv, circleMap, circleEquiv, hom, iso }
```

`\where` members take their indices explicitly (`equiv (n : Nat) …`) so the general-`n` set-level
statements stay usable even though the enclosing theorem is about `PiGroup`.

Naming for these follows the library's `f_g` underscore convention and deliberately parallels the
existing `Loop_S1`: `PiGroup_S1`, `PiGroup_Torus`, `PiGroup_Product`, `PiGroup_LoopShift`,
`PiGroup_basepoint`.

### 2.4 Comments

Match the surrounding tree, which is sparing. `{- | … -}` is a **file header** carrying a paper
citation or an essential convention; `-- |` is one line of mathematical content or a bare reference
(`-- | Corollary 7.5.8.`). Most definitions carry no comment. Do not write comments that restate a
signature, and do not leave proof sketches in the tree.

### 2.5 Universe levels

Add `.{u}` **iff** the statement mentions something that itself demands `\Type u` / `Class.{u}`
arguments. In practice: anything mentioning `Omega^` or `pi` needs it; anything mentioning only
`Loop`, `->*`, `PointedProduct` does not. Strip-and-recheck rather than adding speculatively. See
the `arend-levels` skill for the full rule and the error-message table.

---

## 3. What is done

### 3.1 Chapter 7 substrate

| File | Contents |
|---|---|
| `Homotopy/Localization/Truncation.ard` | `truncTypes` (universe of n-types), `trunc-unique`, **`truncUniverse n : Modality`** — `Trunc_-1+` as a localization; `sphereNull_iff_truncated` (book 7.2.9 / RSS Example 2.3(v)) |
| `Homotopy/Localization/Connected.ard` | `connectedType-local-cong`, `isConnected-equiv`, `contr=>isConnectedType`, `equiv=>isConnectedMap`, **`lEta-connected`** (the unit of any modality is connected) |
| `Homotopy/Localization/Equiv.ard` | `Extension.dext`, `dext-equiv`, `dcontr-equiv`, **`connected_depEquiv`** (book Lemma 7.5.7 — the dependent strengthening of `connected_isLocalEquiv`) |

`LType {truncUniverse n} A` reduces **definitionally** to `Trunc_-1+ n A`, and `lEta` to `inT`. All
downstream statements are therefore phrased in `Trunc_-1+`, with no transport.

### 3.2 Chapter 7 numeric layer — `Homotopy/Connectivity.ard`

`isNConnectedType`, `isNConnectedMap`, `isNTruncatedMap`, `TruncMap` (+`.equiv`, `.=lmap`),
`nconnected-iff-trunc-contr`, `nconnected-map-fibers`, `nconnected-equiv`, `nconnected-contr`,
`nconnected-map-equiv`, `nconnected-map-mono`, `nconnected-map=>trunc-equiv` (one direction of 7.5.14),
`trunc-in-nconnected` (7.5.8), `nconnected_join`, `inhabited=>nconnected0`, `nconnected-mono`,
`loop-nconnected`, `sphere0-nconnected`, `susp-nconnected`, `sphere-nconnected`.

### 3.3 Chapter 7 images — `Homotopy/Image/Truncated.ard`

`NImage`, `nimage-in`, `nimage-out`, `nimage-out-fib`, `nimage-right-truncated`,
`nimage-left-connected`, `nimage0-equiv` (n = 0 is the ordinary image), `DiagonalFillers`,
**`nconnected_ntruncated-orthogonal`** (7.6.6), `FiberProduct`, `pullbackMap`,
`ntruncated-pullback`, `nimage-pullback` (7.6.9).

### 3.4 Chapter 8 — `Homotopy/HomotopyGroup.ard` and children

Core: `pi`, `piMap`, `piMap-isEquiv`, `piMap-id`, `piMap-comp`, `piSphereEquiv`, `PiGroup`,
`PiCGroup`, `piGroupMap`.

Theorems (each main-theorem + `\where`): `PiGroup_Product`, `PiGroup_LoopShift`,
`PiGroup_basepoint` (with `act`, `act-id`, `act-comp`, and `act-1` — basepoint change in dimension
one is conjugation).

| File | Theorem                                                                                                      |
|---|--------------------------------------------------------------------------------------------------------------|
| `Homotopy/HomotopyGroup/Circle.ard` | `PiGroup_S1 : PiGroup 0 (Sphere.pointed 1) = AddGroup.toGroup IntRing`. Also done for higher homotopy groups |
| `Homotopy/HomotopyGroup/Torus.ard` | `PiGroup_Torus : PiGroup 0 (Pointed.make point) = ProductGroup ℤ ℤ`, also done for higher groups             |
| `Homotopy/HomotopyGroup/Connectivity.ard` | `pi-vanishes-below` (8.3.2), `sphere-pi-vanishes` — π_k(Sⁿ) = 0 for k < n                                    |

### 3.5 Chapter 8 — fibre sequences, `Homotopy/FiberSequence.ard`

`PointedFib`, `fibProj`, `fibBoundary`, `PointedShortSeq` (+`gapMap`, `gap`), `FiberSequence`,
`fiberGapEquiv`, `gapInverse`, `gap-incl`, `gapInverse-incl`, `boundary`; the constructors
`canonicalFiberSequence`, `fiberSequenceOfGap`, `fiberSequence-fiber`, `fiberSequence-total`,
`familyFiberSequence` (+`totalPointed`, `totalProj`); `loopInv`, `fibProjFib`, `fiberFibProjEquiv`
(8.4.4 (1)), `fibEquivPreMap`/`fibEquivPre`, `fibCongrMap`/`fibCongr`, `fibBoundary-gap`,
`fibBoundary-proj`, `fiberBoundaryMap`, `fiberBoundaryEquiv` (8.4.4 (2)), `fiber-third-map`
(8.4.4 (3)), `fibBoundary-third`; `loopFibMap`, `omegaFibMap`, `omegaFib-proj`; the tower
`fiberTower`, `fiberTowerObj`, `fiberTowerArrow`, `towerShift` and the three-periodicity
equivalences `towerBaseEquiv`, `towerTotalEquiv`, `towerFiberEquiv`.

The notion is the *invariant* one: a short sequence plus a nullhomotopy whose gap map into the
homotopy fibre is an equivalence, deliberately not `F = PointedFib p`. Two conventions no signature
reveals:

- `fibBoundary f r = (base, f.2 *> r)` — appended on the right, not inverted. Every later sign
  statement depends on it.
- 8.4.4 (2) is stated as `Loop X ≃ PointedFib (fibBoundary f)`, i.e. in the direction that needs no
  inverse; `PointedEquiv.inverse` is a mere quasi-inverse and does not reduce. Where an inverse is
  genuinely needed it is taken half-adjoint (`PointedEquiv.toHAEquiv`), which is what makes
  `PointedEquiv.inverse-sec` — and hence `gapInverse-incl` — hold.

### 3.6 Chapter 8 — the long exact sequence

| File | Contents |
|---|---|
| `Algebra/Pointed/Exact.ard` | `ExactAt` (8.4.5), `ExactAt-transport`, `exact-middle-surjective`, `PointedExactSequence` |
| `Algebra/Group/Exact.ard` | `exact-middle-injective`, `exact-middle-iso` — Lemma 8.4.7 without commutativity |
| `Homotopy/FiberSequence/Exact.ard` | `trunc0-fiber-exact`; `piHom`, `piBoundary`, `pi-exact-total/fiber/base` (8.4.6); `piBoundarySeq`, `seq-exact-total/fiber/base` for an arbitrary fibre sequence; `piGroup-exact`; `fiberLongExactSet` |
| `Homotopy/FiberSequence/Connectivity.ard` | `nconnected-fiber`, `nconnected-piGroup-iso`, `nconnected-pi-surj`, `nconnected-pi0-isEquiv` — Corollary 8.4.8 |

`trunc0-fiber-exact` is the whole geometric input; everything else is that one theorem moved by
`ExactAt-transport`. Its kernel-to-image direction goes through the new `Trunc0.equality` in
`Set.ard`. Three choices worth keeping:

- **`Omega^` is pushed through the fibre rather than iterating the tower.** The three exactness
  statements are `trunc0-fiber-exact` at `Omega^-Func k` of `f`, of `fibProj f` and of
  `fibBoundary f`, bridged by `omegaFibMap`/`omegaFib-proj`. This is sign-free: the book's
  odd-degree inversion appears only when the fibre construction is iterated. 8.4.4 (3) is still
  used, in `pi-exact-base`, with the inversion cancelled inside the comparison map.
- The sequence is proved at loop degree `k` and then re-indexed to the book's `pi (suc k)` along
  `omegaShift`, whose naturality is the induction in `Homotopy/Loop.ard`.
- Every map appearing inside a later *statement* has its own name with an explicit result type.
  `PointedEquiv.map {e}` inline in a type does not elaborate, and the implicits of `PointedFib` /
  `PointedTrunc0-Func` cannot be inferred from a `->*` type; a named wrapper pins both. See
  `arend-quirks`.

### 3.7 Chapter 8 — Blakers–Massey, Freudenthal, stabilization

| File | Contents |
|---|---|
| `Homotopy/Connectivity.ard` | `nBlakersMassey` — {genBlakersMassey} in the numeric indexing, sitting next to `nconnected_join`, which it uses; plus `nconnected-path`, `nconnected-map-comp-left/-right` |
| `Homotopy/Freudenthal.ard` | `freudenthal-connected` (8.6.4), with the normalized-pushout comparison and `merid-connected` in its `\where` |
| `Homotopy/HomotopyGroup/Stabilization.ard` | `piStabilization` (+`.hom`, `.isEquiv`, `.isSurj`, `.iso`, `.natural`, `.square`), `sphereStabilization` (+`.isEquiv`, `.isSurj`, `.iso`, `.diagonalIso`) |
| `Homotopy/Wedge.ard` | `wedgeToProduct-fiberEquiv` (fibre = join of path spaces, no connectivity hypotheses), `wedgeToProduct-connected`, `WedgeData`, `restrictWedge` (+`.coh`), `wedge-extension` (8.6.2, as an `IsEquiv`, with `.extend` / `.extend-restrict`) |

`SuspLoopEquiv.unit` and `unit.natural` are in `Homotopy/Loop.ard` — the unit belongs with the
adjunction, and `Homotopy/Suspension.ard` cannot mention `Loop` (Loop.ard imports it).

Freudenthal is connectedness of the **specified** unit, not a truncated equivalence; stabilization
naturality is an equality of pointed maps (`piStabilization.square`), not a claim that four corners
are equivalent.

### 3.8 Chapter 8 — the Hopf fibration and the sphere groups

| File | Contents |
|---|---|
| `Homotopy/Hopf.ard` | `HSpaceConn.transport-base`, `sphereHSpace-base-north` |
| `Homotopy/FiberSequence/Hopf.ard` | `hopfTotal`, `hopfTotalEquivS3` (pointed, basepoint **computed**), `hopfFiberSequence`, `hopfMap` |
| `Homotopy/HomotopyGroup/Hopf.ard` | `PiGroup2_S2` (π₂(S²) ≅ ℤ via the connecting map), `hopfPi3Iso` |
| `Homotopy/HomotopyGroup/Sphere.ard` | `PiGroup_Sn_diagonal`, `PiGroup3_S2` |

Two decisions worth keeping:

- **The H-space's unit and the total space's basepoint are separate.** The public objects use
  `(north, north)` with the *sphere's* north, so `familyFiberSequence hopfS2 north` already has
  fibre `Sphere.pointed 1` definitionally. `sphereHSpace-base-north` is the comparison, not a
  dependency of the calculation.
- **π₂(S²) is not obtained by stabilizing π₁(S¹).** `d <= 2d-2` fails at `d = 1`, so `s_1` is only
  surjective; the Hopf sequence is a genuine dependency of the diagonal induction, whose base case
  it is. `HomotopyGroup/Hopf.ard` must not import `HomotopyGroup/Sphere.ard`.

### 3.9 Supporting additions elsewhere

- `Function/Iterate.ard` — `iterr_+`, `iterr-step`, `iterl=iterr`.
- `Equiv/Sigma.ard` — `fib-precomp`, `proj-fib`, `totalFib`.
- `Homotopy/Join.ard` — `Join_Sphere_Sphere : Join (Sphere m) (Sphere n) = Sphere (suc (m + n))`.
- `Homotopy/Fibration.ard` — `Fib.ext-proj` (the first projection of a `Fib.ext` path is its input
  path); note its parameters are ordered `x' x` like `Fib.ext.retraction`, not like `Fib.ext`.
- `Homotopy/Pointed.ard` — `->*.id`, `->*.compose` (`∘*`), `->*.compose-assoc`, `->*.compose-id`,
  `->*.zero`, `->*.happly`, `->*.happly-base`, `PointedProduct` with `proj1`/`proj2`, and the
  `PointedEquiv` record (`map`, `map-isEquiv`) with `id`, `toHAEquiv`, `inverse`, **`inverse-sec`**
  and `compose` (`∘~`).
- `Homotopy/Loop.ard` — **`loopMap`** and its `\where` (`idp-case`, `isEquiv`, `comp`, `comp-coh`),
  `Loop-Func` refactored on top of it, `loopEquiv`, `omegaShift` (+`.isEquiv`, **`.natural`**),
  `omegaShiftEquiv`, `omegaShiftInv` (+`.left`, `.right`), `Loop-Func-id`, `Loop-Func-comp`, `Loop-Func_*>`,
  `Omega^-Func.{isEquiv,id,comp}`, `LoopProductMap` (+`.equiv`, `.proj1`, `.proj2`),
  `omegaProductMap` (+`.isEquiv`, `.proj1`, `.proj2`), `omegaLoopShift`, `omegaSphere`.
- `Homotopy/Sphere/Circle.ard` — `Loop_S1-equiv`, `wind_loop-inv`, `wind-+`.
- `Set.ard` — `Trunc0` API mirroring `TruncP`'s in `Logic.ard`: `remove`, `remove'`, `rec`,
  `rec-eval`, `map`, `map-id`, `map-comp`, `map-isEquiv`, `mapEquiv`, `setEquiv`, `productEquiv`,
  `equality`.
  (`rec`/`remove` must be `\sfunc`, hence `rec-eval`; see §5.)
- `Algebra/Group/GroupCat.ard` — `GroupCat.isoOfEquiv`.
- `Algebra/Group/GroupHom.ard` — `GroupHom.injective`, the multiplicative twin of the existing
  `AddGroupHom.injective` (trivial kernel implies injective).
- `Paths.ard` — `transport_pmap`; `Equiv.ard` — `precomp=>isEquiv` (a map is an equivalence once
  precomposition with it is, for every codomain); `Equiv/Path.ard` — `pathConcatEquiv-right`;
  `Equiv/Sigma.ard` — `pi-left` (dependent Π-reindexing along an equivalence), `pi-right`;
  `Equiv/Univalence.ard` — `transport-isEquiv`.
- `Homotopy/Pushout.ard` — `Cocone` (+`nest`, `vertexEquiv`, `equiv`, `dep`), `drec` (+`.equiv`,
  the *dependent* universal property), `congr` (+`.isEquiv`, congruence of pushouts along an
  equivalence of spans), and the flattening computations `tinl-coe`, `tinr-coe`, `pinl-comp`,
  `pinr-comp`.
- `Homotopy/Suspension.ard` — `Susp.map`, `Susp-Func`; `Homotopy/Join.ard` — `Join_Sphere.north-comp`;
  `Homotopy/Loop.ard` — `SuspLoopEquiv.unit` (+`.natural`), `omegaShiftInv.concat`;
  `Homotopy/HomotopyGroup.ard` — `PiGroup_LoopShift.hom`/`.iso`;
  `Homotopy/FiberSequence/Exact.ard` — `piBoundaryGroup`, `piGroup-exact-boundary`,
  `piGroup-exact-fiber`; `Homotopy/FiberSequence/Connectivity.ard` — `nconnected-piMap-isEquiv`.

---

## 4. What is not done

Ordered by how much is blocking downstream work.

### 4.1 Converse of Lemma 7.5.14

`IsEquiv (TruncMap n f) -> isNConnectedMap n f`. The forward direction is done. The converse is the
modality-specific "L-equivalence ⟹ L-connected" (Rijke–Shulman–Spitters Thm 1.32) and is a genuinely
separate development; it does not follow from what is present.

### 4.2 Uniqueness half of Theorem 7.6.6

Orthogonality (contractibility of `DiagonalFillers`) is proved, which is the engine. Packaging it as
"the type of (connected, truncated) factorizations of `f` is contractible" needs an `NFactorization`
record and its path characterization.

### 4.3 Abelian-range categorical isos

`AddGroupHom.isoOfEquiv` / `AbGroupHom.isoOfEquiv` and the `AbGroupCat` isos for π_{n+2}. Pure
plumbing through the existing coercions; the inverse's additivity follows from injectivity of the
forward map.

### 4.4 Naturality of `boundary`

`fiberSequence-fiber`/`fiberSequence-total` move a fibre sequence along pointed equivalences and
`ExactAt-transport` makes the exactness half of naturality free, but `boundary` itself is not known
to be natural: a `FiberSequenceEquiv` record with `boundary-natural`, and compatibility with
basepoint change (`(x,p) ↦ (x, p *> q)`) and `PiGroup_basepoint`. Nothing needs it yet.

### 4.5 Not started

- Whitehead's theorem.
- Minor omitted conveniences with no consumer yet: `nconnected-comp`, `nconnected-pullback`,
  `NConnected`/`NConnectedMap` classes, `Connected0` ↔ numeric-1-connectedness bridges.

Pre-existing in the library and *not* part of this work: `Homotopy/Localization/BlakersMassey.ard`,
`Homotopy/Hopf.ard`, `Homotopy/K1.ard`, `Homotopy/Image.ard` (the join-based image).

---

## 5. Working notes that will save you time

These are the failures that recurred. The `arend-skills` repo has them in full
(`arend-levels`, `arend-hott`, plus additions to `arend-quirks` and `arend-formalize`); the
highlights:

- **Test `idp` before proving.** Specializations of abstract constructions are often definitionally
  equal to the concrete ones. `LType {truncUniverse n} A = Trunc_-1+ n A => idp` typechecks; several
  planned comparison theorems evaporated this way.
- **Path induction on a structure field.** You cannot `\elim` on `f.2 : f.1 base = base {Y}` because
  `base {Y}` is a projection, not a variable. Restate the lemma over raw components with the
  endpoint as its own parameter — `(f1 : X -> E) {y : E} (f2 : f1 base = y)` — then
  `\elim y, f2 | _, idp` works. This is why `loopMap` exists: once the definition sits below the
  `Pointed` layer, `Loop-Func-comp`'s 2-dimensional coherence reduces to `idp`.
- **`\truncated \data` elimination.** Matching is allowed only into universes ≤ the data's. A
  `\level` annotation does *not* unlock a plain `\func` (`allowed only in \sfunc and \scase`); a
  `\Set`-valued eliminator must be `\sfunc` and therefore needs a `\peval`-based `-eval` lemma.
  Paths in a truncated data type are props, so `\lemma … \elim t` is fine for equations.
- **`\instance … : C (someFunc args)` is rejected** (`Classifying field must be either a universe, a
  sigma type, a record, or a partially applied data or constructor`). State the carrier unfolded:
  `Group (Trunc0 (Omega^ (suc n) X))`, not `Group (pi (suc n) X)`.
- **Class-valued definitions cannot be passed unapplied**: `pmap2 ProductGroup p q` fails; write
  `pmap2 (\lam (G H : Group.{u}) => ProductGroup G H) p q`.
- **A universal property of a HIT is cheap if the glue datum is a dependent path.** `Cocone.dep`
  states it as `Path (\lam i => P (pglue c i)) …` rather than `transport P (ppglue c) … = …`; both
  round-trips of `drec.equiv` are then `idp`. With the transport formulation the section half needs
  the pushout-uniqueness 2-path. The same choice made `WedgeData`'s coherence field a plain
  `lm base = rm base`, because `wedgeToProduct` is constant on the gluing path and so the family is
  definitionally constant.

- **Computing a transport along a chain of univalence paths.** `transport (\lam T => T) (QEquiv_= e) x`
  reduces to `e.f x`, but a `p *> q` of such paths does not reduce at all — peel it with
  `transport_*>` first. `coe` over an interval-indexed `\data` (e.g. `flattening`'s `TotalPushout`)
  also does not reduce on a constructor that ignores the interval; supply the dependent path by hand,
  `pathOver.conv (path (\lam i => tinl {…} {i} y t))`.

- **Finish with one cold `arend -r --serialize`, and only one.** Warm-cache runs can give a false
  green on instance inference — a qualifier deleted as redundant can pass every incremental run and
  fail from source. But `-r` re-typechecks the whole library, so keep it for the end of a piece of
  work, not the edit loop.

Baseline for a clean build: zero errors, and exactly one `[GOAL]`, in
`Topology/Locale/HausdorffLocale.ard` — that is pre-existing and unrelated. `arend -t` also reports
6 pre-existing failures in `test/Meta/MCasesTest.ard`.
