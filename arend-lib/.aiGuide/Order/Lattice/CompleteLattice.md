### Order.Lattice.CompleteLattice

- **`CompleteLattice`**: A `BoundedLattice` with arbitrary set-indexed joins (`Join`) and meets (`Meet`) and their universal properties (`Join-cond`, `Join-univ`, `Meet-cond`, `Meet-univ`). Extends `CompleteCat`, with limits built from `Meet` and pullbacks from binary meets. Binary `join`/`meet`, `top`, `bottom` come with default implementations derived from `Join`/`Meet`.
- **`CompleteLattice.SJoin`**: Join of the values `f a` over those `a : A` satisfying `P a`, for `f : A -> E` and `P : A -> \Type`.
- **`CompleteLattice.SJoin-cond`**, **`CompleteLattice.SJoin-univ`**: Universal property of `SJoin`: `f a <= SJoin f P` whenever `P a`, and `SJoin f P <= e` if `f a <= e` for all `a` with `P a`.
- **`CompleteLattice.SJoin-conde`**: `SJoin-cond` with the element `a` and the proof `P a` as explicit arguments.
- **`CompleteLattice.IJoin`**: Join of the values of a family `f : J -> E`, i.e. the `Join` of its image.
- **`CompleteLattice.IJoin-cond`**, **`CompleteLattice.IJoin-univ`**: Universal property of `IJoin`: `f j <= IJoin f` for every `j`, and `IJoin f <= e` if `f a <= e` for all `a`.
- **`CompleteLattice.Join_SJoin`**, **`CompleteLattice.Join_IJoin`**: `Join C` of a predicate `C` equals `SJoin (\lam x => x) C`, and equals the `IJoin` of the family of elements satisfying `C`.
- **`CompleteLattice.join_Join`**: Binary join expressed as a `Join` of the predicate `\lam z => (z = x) || (z = y)`.
- **`CompleteLattice.op`**: The dual complete lattice obtained by swapping `Join` ↔ `Meet`.
- **`CompleteLattice.SMeet`**: Meet of the values `f a` over those `a : A` satisfying `P a`; the dual of `SJoin`.
- **`CompleteLattice.SMeet-cond`**, **`CompleteLattice.SMeet-conde`**: `SMeet f P <= f a` whenever `P a` (the second takes `a` and the proof explicitly).
- **`CompleteLattice.SMeet-univ`**: `e <= SMeet f P` if `e <= f a` for all `a` with `P a`.
