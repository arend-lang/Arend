### Order.PartialOrder.Coproduct

The coproduct (disjoint sum) of a family of preorders.

For a family `P : J -> Preorder` the module builds the set `CoproductPreorder.Elem P` of pairs `elem j a` with `a : P j`, and orders it by comparing elements only within the same index: `elem i a <= elem j b` means `i = j` and the transported `a` is below `b`. The resulting preorder instance is `CoproductPreorder`, and the `\where` block contains the supporting lemmas.

#### The coproduct preorder

- **`CoproductPreorder`**: The `Preorder` on `CoproductPreorder.Elem P`, the disjoint union of the preorders `P j`, where elements of different summands are incomparable.
- **`CoproductPreorder.Elem`**: The truncated (set) data type of elements `elem j a` of the coproduct, with `j : J` and `a : P j`.
- **`CoproductPreorder.<=`**: The order on `Elem P`: `elem i a <= elem j b` holds if there merely is a path `p : i = j` with the transport of `a` along `p` below `b`.
- **`CoproductPreorder.<=_transport`**: Transport along a path `p : i = j` preserves `<=` in the fibers.
- **`CoproductPreorder.Elem_=`**: Two elements `elem i a` and `elem j b` are equal if `i = j` via `p` and `transport (P __) p a = b`.
- **`CoproductPreorder.<=-elem-left`**: If `x <= y` and `y = elem j a`, then `x` is merely of the form `elem j b` with `b <= a`.
