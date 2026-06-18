### Logic.Rewriting.TRS.Utils.FinFunctions

Utilities for working with finite-domain functions `Fin n -> C i` and indices into list concatenations, geared toward inductive arguments over rewriting systems.

The module provides two complementary toolkits. First, it handles indices into appended lists (`a ++ b`) by giving canonical embeddings of `Index a` and `Index b` together with a decision procedure that lets one do case analysis on which side an index lands. Second, it formalizes pointwise "hybrid" functions: `modular-function f g delim` agrees with `f` before `delim` and with `g` from `delim` onward, while `pointed-function f index point` overrides `f` at a single index. These hybrids enable a stepwise induction principle (`modular-induction`) that bridges from `f` to `g` one position at a time, which is the standard pattern for proving properties that are stable under single-coordinate updates.

#### List Index Embeddings

- **`Index`**: Abbreviation `Fin (length list)` for indices into a `List A`.
- **`expand-fin-left`**: Embeds `Index a` into `Index (a ++ b)` via the prefix.
- **`expand-fin-left.correct`**: `(a ++ b) !! expand-fin-left i = a !! i`; the embedding preserves lookup.
- **`expand-fin-right`**: Embeds `Index b` into `Index (a ++ b)` via the suffix (offset by `length a`).
- **`expand-fin-right.correct`**: `(a ++ b) !! expand-fin-right i = b !! i`; suffix embedding preserves lookup.

#### Case Analysis on Concatenated Indices

- **`partial-fin-induction`**: Eliminator for `Index (a ++ b)`: given proofs on the prefix and suffix images, derives a proof for any index.
- **`partial-fin-induction.fin-list-decide`**: Decides whether a given `Index (a ++ b)` factors through `expand-fin-left` or `expand-fin-right`, returning the corresponding witness equation.

#### Modular (Hybrid) Functions

- **`modular-function`**: `modular-function f g delim i` returns `f i` when `i < delim` and `g i` otherwise; the boundary `delim : Fin (suc n)` ranges over all split points including the extremes.
- **`modular-function.pure-left-modular`**: At `delim = 0`, the hybrid equals `g`'s "complement" — actually `modular-function f g 0 = f` (the left-pure case).
- **`modular-function.pure-left-modular.unext`**: Pointwise version of `pure-left-modular`.
- **`modular-function.modular-bridge`**: If `f delim = g delim`, then advancing the boundary by one is invisible: `modular-function f g delim = modular-function f g (suc delim)`.
- **`modular-function.modular-bridge.unext`**: Pointwise version of `modular-bridge`.
- **`modular-function.pure-right-modular`**: At `delim = finLast n`, the hybrid equals `g`.
- **`modular-function.pure-right-modular.unext`**: Pointwise version of `pure-right-modular`.

#### Modular Induction

- **`modular-induction`**: Bridge induction principle: to transfer a predicate `Q` from `f` to `g`, supply `Q f` and a step that promotes `Q (modular-function f g delim)` to `Q (modular-function f g (suc delim))`.
- **`modular-induction.progressive-induction-lemma`**: The recursion that walks the boundary from `0` up to `finLast n`, producing `Q (modular-function f g delim)` for every `delim`.

#### Pointed Functions

- **`pointed-function`**: `pointed-function f index point` overrides `f` at a single coordinate `index`, returning `point` there and `f j` elsewhere.
- **`pointed-function.at-index`**: The override holds at the chosen coordinate: `pointed-function f index point index = point`.
- **`pointed-function.not-at-index`**: Off the chosen coordinate, the function is unchanged: `pointed-function f index point j = f j` whenever `j ≠ index`.

#### Pointed Induction

- **`pointed-induction`**: Eliminator for predicates over `pointed-function`: given the predicate at the override site (`Q index point`) and at every other site (`Q j (f j)` for `j ≠ index`), derive `Q i (pointed-function f index point i)` for all `i`.

#### Bridges Between Modular and Pointed

- **`modular-to-pointed`**: Re-expresses `modular-function f g delim` as a `pointed-function` whose distinguished value at `delim` is `f delim`.
- **`modular-to-pointed.unext`**: Pointwise version of `modular-to-pointed`.
- **`modular-to-pointed-forward`**: Re-expresses `modular-function f g (suc delim)` as a `pointed-function` over `modular-function f g delim` with override value `g delim` at `delim`; this is the natural "single-coordinate update" view of the bridge step.
- **`modular-to-pointed-forward.unext`**: Pointwise version of `modular-to-pointed-forward`.
