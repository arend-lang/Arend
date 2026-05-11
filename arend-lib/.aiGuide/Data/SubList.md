### Data.SubList

A datatype representing the sublist relation, witnessing that one list embeds into another while preserving order.

`SubList l r` is an inductive predicate constructed from three cases: empty-empty (`sublist-nil`), matching heads (`sublist-match`), or skipping an element on the right (`sublist-skip`). The module develops a calculus around this relation — identity, composition, and various extension lemmas (left/right, single/both sides) — that mirror the categorical structure of order-preserving embeddings between lists. Substantial machinery handles transport coherence, since constructions like associativity of `++` produce equalities of lists that must be transported through the `SubList` family; the `Transports` submodule packages these naturality lemmas. Contractibility results (`trivial-sublist-contractible`, `identity-sublist-contractible`) show that sublists into `nil` and identity sublists are propositional.

#### Core Datatype

- **`SubList`**: Inductive family `SubList {A : \Type} (l r : List A)`. Constructors: `sublist-nil` (empty into empty), `sublist-match` (paired heads with `x = y` and a sublist of tails), `sublist-skip` (skip a head on the right).

#### Basic Constructors

- **`SubList.identity`**: Reflexivity — every list is a sublist of itself.
- **`SubList.sublist-nil-free`**: `SubList nil list` for any `list` — the empty list embeds into anything.

#### Extension Lemmas

- **`SubList.extend-left-both`**: Prepending the same list `add` to both sides preserves the sublist relation.
- **`SubList.extend-right-both`**: Appending the same list `add` to both sides preserves the sublist relation.
- **`SubList.extend-right-single`**: `SubList l r -> SubList l (r ++ add)` — extending only the larger list on the right.
- **`SubList.extend-left-single`**: `SubList l r -> SubList l (add ++ r)` — extending only the larger list on the left.
- **`SubList.id+right`**: `SubList i (i ++ r)` — a list embeds into itself extended on the right.
- **`SubList.id+left`**: `SubList i (l ++ i)` — a list embeds into itself extended on the left.

#### Structural Operations

- **`SubList.shrink`**: From `SubList (a :: list) list'` derive `SubList list list'` — drop the head of the smaller list.
- **`SubList.compose`**: Transitivity: `SubList a b -> SubList b c -> SubList a c`.
- **`SubList.compose.identity`**: Composition of identity sublists is identity.
- **`SubList.compose.over-right-both`**: Composition commutes with right-extension on both sides (for `\Set` types).
- **`SubList.compose.over-right-single`**: Coherence between `extend-right-single` of identity and `extend-right-both` under composition.

#### Contractibility / Uniqueness

- **`trivial-sublist-contractible`**: Any two proofs of `SubList nil a` are equal.
- **`identity-sublist-contractible`**: Any two proofs of `SubList a a` are equal (for `\Set` types).
- **`impossible-sublist`**: `SubList (x :: a) a` is empty — a list cannot be a sublist of its own tail.

#### Naturality of Extensions

- **`identity-invariant-over-right-extension`**: `extend-right-both` of `identity` equals `identity` on the extended list.
- **`skip-over-extend-right`**: `sublist-skip` commutes with `extend-right-both`.
- **`shrink-over-extend-right`**: `shrink` commutes with `extend-right-both`.

#### Transports Submodule

Lemmas describing how `sublist-skip` and `sublist-match` interact with `transport` along list equalities, used to handle coherence under associativity and unit laws of `++`.

- **`Transports.sublist-skip-over-transport-right`**: `sublist-skip` commutes with transport in the right argument of `SubList`.
- **`Transports.sublist-skip-over-transport-left`**: `sublist-skip` commutes with transport in the left argument.
- **`Transports.sublist-match-over-transport-right`**, **`sublist-match-over-transport-right-inv`**: `sublist-match idp` commutes with (inverse) transport in the right argument.
- **`Transports.sublist-match-over-transport-left`**: `sublist-match idp` commutes with transport in the left argument.
- **`Transports.extension-to-nil-right`**: `extend-right-single identity` equals `identity` transported along `inv ++_nil`.
- **`Transports.extension-to-nil-left`**: `sublist-nil-free` on `a ++ nil` equals `extend-left-single sublist-nil`.
- **`Transports.lb-ls-to-ls`**: Coherence — right-extending a left-extended identity equals a left-extended identity transported along `inv ++-assoc`. Includes companion `inv'` giving the reverse direction.
- **`Transports.rs-rs-to-rs`**: Iterated `extend-right-single` of identity coheres with associativity of `++` via transport. Includes `inv'` and `inv'2` reformulations using composition.
- **`Transports.nil-to-right-nil`**: `sublist-nil-free` on a triple concatenation is the right-extended `sublist-nil-free` transported along `++-assoc`.
- **`Transports.rb-rb-to-rb`**: Iterated `extend-right-both` coheres with `++-assoc` via transport on both arguments.
