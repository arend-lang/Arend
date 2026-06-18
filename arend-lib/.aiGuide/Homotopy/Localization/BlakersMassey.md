### Homotopy.Localization.BlakersMassey

Formalizes the generalized Blakers–Massey connectivity theorem for pushouts in the setting of higher modalities (reflective subuniverses).

The Blakers–Massey theorem describes the connectivity of the pullback of inclusions into a pushout. This module establishes a generalized form: given a pushout square built from a span `Q : X -> Y -> hType` and connectivity assumptions on appropriate joins (the data class `Data`), the diagonal map `pbMap` from `Q x y` into the path space `pinl x = pinr y` of the pushout is a connected map relative to a fixed reflective universe. The proof follows the Favonia–Finster–Licata–Lumsdaine encode-decode approach: a type family `code` over the pushout is defined by gluing an "equivalence data" structure (`EquivData`) at each generator, the equivalence is built explicitly via maps `LR`, `RL` and homotopies `LRL`, `RLR`, and contractibility of the total space gives the connectivity result.

#### Main Theorems

- **`genBlakersMassey`**: The generalized Blakers–Massey theorem: for any `d : Data`, `x0 : X`, `y0 : Y`, the map `pbMap` is a connected map (with respect to the ambient reflective universe).
- **`surjective`**: Auxiliary form of the theorem assuming truncated existence of a witness `Q x0 y` for some `y`.

#### Reflective-Universe Equivalence Data

- **`EquivData`**: Class extending `ReflUniverse`, packaging the data needed to prove an equivalence of localizations between `LType A` and `LType B` from a span of connected types over `A` and `B`. Contains families `M : A -> Connected`, `N : B -> Connected`, mutual maps `f`, `g` between total spaces, and pointwise compatibility `p`, `q` on first projections.
- **`EquivData.eval`**: Computes the value of the lifted map on `lEta (a, m)`, expressing it via the localization of `f` on the first component.
- **`EquivData.equiv-lemma`**: The lifted map `LType A -> LType B` is an equivalence — the core lemma used to identify `code` fibers across the pushout glue.

#### Pushout Span Data

- **`POData`**: Class bundling a span `X`, `Y`, `Q : X -> Y -> hType` together with derived constructions on its pushout.
- **`POData.PO`**: The pushout of the span, with vertices `pinl x` / `pinr y` and gluing along `Q x y`.
- **`POData.swap`**: Symmetry map exchanging the two sides of the pushout.
- **`POData.pbMap`**: The "path-builder" map sending a witness `q : Q x y` to the glue path `pinl x = pinr y` in `PO`.
- **`POData.pullback_pushout-surjective`**: From a path `pinl x = pinr y`, extracts truncated existence of some `x'` with `Q x' y` — the surjectivity step underlying `surjective` above.

#### Connectivity Hypothesis

- **`Data`**: Class extending `ReflUniverse` and `POData` with the Blakers–Massey connectivity assumption `ch`: for any compatible `q0`, `q1`, `q2`, the join of the two relevant path-fiber types is connected.

#### Encode-Decode Setup

- **`DataExt`**: Extension of `Data` with chosen basepoints `x0 : X`, `y0 : Y`, `q0 : Q x0 y0`, providing the setting for the encode-decode argument.
- **`DataExt.code-left`**: The fiber type used on the `pinl` side: a localization of pairs `(q1, witness)` where the witness equates a constructed concatenation of `pbMap`s with the given path.
- **`DataExt.code-glue-gen`**: Generic transport-along-pglue lemma turning a fiberwise equivalence into a transport equality of fiber families.
- **`DataExt.code-glue`**: Specialization of `code-glue-gen` showing that transport along `pbMap q` carries `code-left idp` to the localized fiber `LType (Fib pbMap p)`.
- **`DataExt.code`**: The main type family on the pushout: `code-left idp p` on `pinl`, `LType (Fib pbMap p)` on `pinr`, glued via `code-glue`.

#### The Equivalence Underlying `code-glue`

- **`code.equivData`**: The `EquivData` instance used at each glue generator; sets `A` to witness pairs `(q1, ...)` and `B` to `Fib pbMap p`, with `M`/`N` as joins of path-transport pairs, justified by the Blakers–Massey hypothesis `ch`.
- **`code.equiv`**: The fiberwise equivalence between `code-left (pbMap q)` and `LType (Fib pbMap p)` derived from `equivData`.
- **`code.LR`**, **`code.RL`**: Explicit forward and backward maps between the two sides of the equivalence, defined by case analysis on the `Join` constructors.
- **`code.LRL`**, **`code.RLR`**: Round-trip homotopies `g ∘ f ~ id` and `f ∘ g ~ id` on first components, completing the equivalence.
- **`code.pathLem1`**, **`code.pathLem2`**, **`code.pathLem2-gen`**, **`code.pathLem3`**: Path-algebra lemmas (e.g. `p *> inv q *> q = p`, `p *> inv p *> q = q`, and their compatibility) used to massage the witnesses appearing in `LR`/`RL`.

#### Centers and Contractibility

- **`coerce-path-gen`**, **`coerce-path`**: Transport `code-left idp idp` to `code p` along an arbitrary path `p` in the pushout.
- **`Left`**: Abbreviation for the witness type appearing in `code-left`.
- **`code-left-diag`**: Functorial action on `code-left` along a path `p`, applied to the `Left idp` summand via `pathLem2-gen`.
- **`coerce-path-glue-gen`**: Computes the transport in `coerce-path` for the `pglue` case, reducing it to the equivalence `t`.
- **`code-center`**: The canonical center element of `code p`, obtained by transporting `lEta point` along `coerce-path`.
- **`code-center.point`**: The base witness `(q0, *>_inv (pbMap q0))` in `Left idp`.
- **`code-path`**: For any `c : Fib pbMap p` over a path to `pinr y0`, identifies `code-center p` with `lEta c` — the decode step exhibiting `code-center` as a center of contraction.
- **`code-contr`**: Contractibility of `code p` for every `p`, the encode-decode payload yielding `genBlakersMassey`.
