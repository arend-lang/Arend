### Data.SubList

This module defines the `SubList` relation on `List` and provides composition, extension, contraction, transport, and contractibility lemmas.

#### SubList Type

- **`SubList`**: Inductive relation `SubList l r` witnessing that `l` is a sublist of `r`, with constructors:
  - `sublist-nil`: `SubList nil nil`
  - `sublist-match`: from `x = y` and `SubList xs ys`, gives `SubList (x :: xs) (y :: ys)`
  - `sublist-skip`: from `SubList l ys`, gives `SubList l (y :: ys)`

#### SubList Utilities (in `\where` block)

- **`identity`**: `SubList list list` (reflexivity).
- **`sublist-nil-free`**: `SubList nil list` for any `list`.
- **`extend-left-both`**: `SubList l r` implies `SubList (add ++ l) (add ++ r)`.
- **`extend-right-both`**: `SubList l r` implies `SubList (l ++ add) (r ++ add)`.
- **`extend-right-single`**: `SubList l r` implies `SubList l (r ++ add)`.
- **`extend-left-single`**: `SubList l r` implies `SubList l (add ++ r)`.
- **`shrink`**: `SubList (a :: list) list'` implies `SubList list list'`.
- **`id+right`**: `SubList i (i ++ r)`.
- **`id+left`**: `SubList i (l ++ i)`.

#### Composition

- **`compose`**: Transitivity: `SubList a b` and `SubList b c` imply `SubList a c`.
  - **`compose.identity`**: `compose identity identity = identity`.
  - **`compose.over-right-both`**: Composition distributes over `extend-right-both`.
  - **`compose.over-right-single`**: Composition with `extend-right-single` and `extend-right-both`.

#### Contractibility and Impossibility

- **`trivial-sublist-contractible`**: Any two `SubList nil a` proofs are equal.
- **`identity-sublist-contractible`**: Any two `SubList a a` proofs are equal (for `\Set`).
- **`impossible-sublist`**: `SubList (x :: a) a` is impossible (for `\Set`).

#### Invariance Lemmas

- **`identity-invariant-over-right-extension`**: `extend-right-both identity = identity` for `a ++ b`.
- **`skip-over-extend-right`**: `sublist-skip` commutes with `extend-right-both`.
- **`shrink-over-extend-right`**: `shrink` commutes with `extend-right-both`.

#### Transports Module

Lemmas for transporting `SubList` proofs along list equalities:

- **`sublist-skip-over-transport-right`**: `sublist-skip` commutes with `transport` on the right list.
- **`sublist-skip-over-transport-left`**: `sublist-skip` commutes with `transport` on the left list.
- **`sublist-match-over-transport-right-inv`**: `sublist-match` commutes with `transport` (inverse direction).
- **`sublist-match-over-transport-right`**: `sublist-match` commutes with `transport` on the right list.
- **`sublist-match-over-transport-left`**: `sublist-match` commutes with `transport` on the left list.
- **`extension-to-nil-right`**: `extend-right-single identity` equals `transport` of `identity` along `inv ++_nil`.
- **`extension-to-nil-left`**: `sublist-nil-free` for `a ++ nil` equals `extend-left-single sublist-nil`.
- **`lb-ls-to-ls`**: Relates `extend-right-both (extend-left-single identity)` to `transport` of `extend-left-single identity` along `++-assoc`.
  - **`lb-ls-to-ls.inv'`**: Inverse direction.
- **`rs-rs-to-rs`**: Relates nested `extend-right-single` to `transport` along `++-assoc`.
  - **`rs-rs-to-rs.extension-lemma`**: `extend-right-single sublist-nil-free = sublist-nil-free`.
  - **`rs-rs-to-rs.inv'`**: Inverse direction.
  - **`rs-rs-to-rs.inv'2`**: Variant using `compose`.
- **`nil-to-right-nil`**: Relates `sublist-nil-free` for `a ++ b ++ c` to `transport` along `++-assoc`.
- **`rb-rb-to-rb`**: Relates nested `extend-right-both` to `transport` along `++-assoc`.
