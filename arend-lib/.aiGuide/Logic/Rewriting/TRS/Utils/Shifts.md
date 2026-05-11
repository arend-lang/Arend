### Logic.Rewriting.TRS.Utils.Shifts

Index-shifting machinery for sublists, used to relocate positions when a list is embedded into a larger one.

This module supports the bookkeeping needed when rewriting rules or terms reference positions in a list `a` that is a sublist of a bigger list `b`. The core operation `shift-index` translates an index into `a` to the corresponding index in `b` along a `SubList` witness, and the accompanying `proof` shows that the indexed element is preserved. The remaining lemmas describe how `shift-index` interacts with the standard `SubList` constructions (identity, skip, shrink, left/right extensions) and with the `expand-fin-left`/`expand-fin-right` embeddings induced by list concatenation, providing the rewriting equalities needed to transport indexed data between contexts.

#### Core Shift Operation

- **`shift-index`**: Given a `SubList a b` witness and an index `ind : Index a`, produces the corresponding index `Fin (length b)` by walking the sublist witness and inserting `suc` for each skipped element.
- **`Shifts.proof`**: Transports an equation `point = a !! ind` along a sublist embedding, yielding `point = b !! shift-index sublist ind`. Confirms that `shift-index` preserves the indexed element.

#### Behavior on SubList Constructors

- **`Shifts.over-identity`**: `shift-index SubList.identity ind = ind`; the identity sublist leaves indices fixed.
- **`Shifts.over-skip`**: Skipping a fresh head element `point'` shifts every index up by one: `shift-index (sublist-skip sublist) ind = suc (shift-index sublist ind)`.
- **`Shifts.over-shrink`**: Relates shrinking the source list to advancing the index: `shift-index (SubList.shrink sublist) ind = shift-index sublist (fsuc ind)`.

#### Interaction with List Concatenation

- **`Shifts.over-right-single`**: For the canonical embedding `a ↪ a ++ b`, `shift-index` agrees with `expand-fin-left`.
- **`Shifts.over-left-single`**: For the canonical embedding `b ↪ a ++ b`, `shift-index` agrees with `expand-fin-right`.
- **`Shifts.over-right-both`**: Extending a sublist on the right by `c` leaves indices coming from `c` (via `expand-fin-right`) unchanged.

#### Packaged Extension Lemmas

- **`Shifts.right-extension`**: For the inclusion `a ↪ a ++ b`, packages the equality `a !! ind = (a ++ b) !! expand-fin-left ind` together with the fact that `(shift-index, proof)` agrees with `(expand-fin-left, _)` as an equality of dependent pairs. Useful for transporting indexed data along right-concatenation.
- **`Shifts.left-extension-generic`**: Variant of `right-extension` for left-concatenation with an arbitrary witness `point = b !! ind`, packaging the corresponding `expand-fin-right` equality.
- **`Shifts.left-extension`**: Specialization of `left-extension-generic` to `idp`, giving the canonical form `(shift-index SubList.id+left ind, proof …) = (expand-fin-right ind, inv (expand-fin-right.correct …))`.

#### Compatibility of Right-Both Extension with Fin Expansions

- **`Shifts.right-both-after-expand-left`**: Shifting an `expand-fin-left index` through `extend-right-both sublist` equals first shifting through `sublist` and then through `extend-right-single SubList.identity`. Expresses commutativity of the right-both extension with the left half of `a ++ c`.
- **`Shifts.right-both-after-expand-right`**: Shifting an `expand-fin-right index` through `extend-right-both sublist` equals shifting through `extend-left-single SubList.identity`. The dual statement on the right half.
