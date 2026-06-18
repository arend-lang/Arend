### Homotopy.HLevel

Homotopy levels (n-types) and their closure properties.

This module defines two parallel indexings of homotopy levels: `ofHLevel_-1+` (starting at propositions, indexed from -1) and `ofHLevel_-2+` (starting at contractibility, indexed from -2). The two indexings are interconvertible via `HLevel_-2+1=>HLevel_-1` and `HLevel_-1=>HLevel_-2+1`. The bulk of the module establishes that h-levels are preserved under standard type-forming operations (Π, Σ, retracts, embeddings) and are upward-closed under the level ordering, providing the basic infrastructure for working with truncation levels throughout the library.

#### H-Level Predicates

- **`ofHLevel_-1+`**: `A ofHLevel_-1+ n` asserts `A` is an `(n-1)`-type. `n = 0` means `isProp A`; `suc n` recurses on path types. Has a `\level` instance proving the predicate itself is a proposition.
- **`ofHLevel_-2+`**: `A ofHLevel_-2+ n` asserts `A` is an `(n-2)`-type, valued in `\Prop`. `n = 0` means `Contr A`; `suc n` recurses on path types.
- **`hLevel-inh`**: If `A`'s h-level can be shown assuming an inhabitant of `A`, then `A` has that h-level (for `n >= 1`).
- **`I-isContr`**: The interval `I` is contractible, with `left` as center.

#### Conversion Between Indexings

- **`HLevel_-2+1=>HLevel_-1`**: Converts `A ofHLevel_-2+ suc n` to `A ofHLevel_-1+ n` by extracting centers of contraction on path types.
- **`HLevel_-1=>HLevel_-2+1`**: Converts `A ofHLevel_-1+ n` to `A ofHLevel_-2+ suc n` (the reverse direction).

#### Upward Closure

- **`HLevel_-1_suc`**: An `n`-type is also a `(n+1)`-type. Base case uses `isProp.=>isSet`.
- **`HLevel_-1_+`**: Iterated upward closure: `A ofHLevel_-1+ k` implies `A ofHLevel_-1+ (k + n)`.
- **`HLevel_-1_<=`**: Upward closure along an inequality `k <= n`.

#### Closure Under Type Constructors (h-level -1+)

- **`HLevel-retracts`**: H-levels transfer along sections (retracts): if `B` has h-level `n` and `A` retracts onto `B`, then `A` has h-level `n`.
- **`HLevels-pi`**: Π-types preserve h-levels: if each `B a` has h-level `n`, so does `\Pi (a : A) -> B a`.
- **`HLevels-sigma`**: Σ-types preserve h-levels given both components do.
- **`HLevels-embeddings`**: H-levels transfer along embeddings: if `B` has h-level `n` and `e : A -> B` is an embedding, then `A` has h-level `n`.

#### Closure Under Type Constructors (h-level -2+)

- **`HLevel_-2-retracts`**: Retract-closure for the `-2+` indexing.
- **`HLevels_-2-pi`**: Π-closure for the `-2+` indexing.
- **`HLevels_-2-sigma`**: Σ-closure for the `-2+` indexing.
