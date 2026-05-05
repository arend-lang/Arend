### Homotopy.HLevel

H-levels (homotopy truncation levels) for types, with two indexing conventions and closure properties under standard type formers.

#### H-Level Predicates

- **`ofHLevel_-1+`**: `A ofHLevel_-1+ n` asserts `A` has h-level `n-1` (0 = prop, 1 = set, etc.); defined recursively as iterated path-space proposition-ness.
- **`ofHLevel_-2+`**: `A ofHLevel_-2+ n` asserts `A` has h-level `n-2` (0 = contractible, 1 = prop, etc.); a `\Prop`-valued version with contractibility at the base.
- **`ofHLevel_-1+.levelProp`**: Being of h-level `n-1` is itself a proposition (used as a `\use \level` instance).

#### Conversions Between Indexings

- **`HLevel_-2+1=>HLevel_-1`**: `A ofHLevel_-2+ suc n -> A ofHLevel_-1+ n`.
- **`HLevel_-1=>HLevel_-2+1`**: `A ofHLevel_-1+ n -> A ofHLevel_-2+ suc n` (converse direction).
- **`I-isContr`**: The interval `I` is contractible, with center `left`.

#### Cumulativity

- **`hLevel-inh`**: From a function `A -> A ofHLevel_-1+ n` (h-level conditional on inhabitation), conclude `A ofHLevel_-1+ n`.
- **`HLevel_-1_suc`**: H-levels are upward-closed by one: `A ofHLevel_-1+ n -> A ofHLevel_-1+ suc n`.
- **`HLevel_-1_+`**: Upward closure by an arbitrary offset: `A ofHLevel_-1+ k -> A ofHLevel_-1+ (k + n)`.
- **`HLevel_-1_<=`**: Upward closure under `<=` on naturals.

#### Closure Under Type Formers

- **`HLevel-retracts`**: H-levels transfer along sections (retracts inherit h-level from their codomain).
- **`HLevels-pi`**: Dependent function types preserve h-level: pointwise `B a ofHLevel_-1+ n` implies `(\Pi (a : A) -> B a) ofHLevel_-1+ n`.
- **`HLevels-sigma`**: Sigma types preserve h-level when both base and fiber do.
- **`HLevels-embeddings`**: H-levels transfer along embeddings (the domain inherits from the codomain).
- **`HLevel_-2-retracts`**: Retract closure for the `-2+` indexing.
- **`HLevels_-2-pi`**: Pi closure for the `-2+` indexing.
- **`HLevels_-2-sigma`**: Sigma closure for the `-2+` indexing.
