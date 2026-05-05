### Data.List

This module provides the `List` type and operations including concatenation, mapping, sorting, permutations, membership, and set operations.

#### List Type and Basic Operations

- **`List`**: Inductive list type with constructors `nil` and `:: (infixr 5)`.
- **`length`**: Returns the length of a list as a `Nat`.
- **`!!` (infixl 9)**: Indexing operator; `l !! i` returns the `i`-th element where `i : Fin (length l)`.
- **`headDef`**: Returns the head of a list or a default value if empty.
- **`tail`**: Returns the tail of a list; `nil` for empty.

#### Concatenation

- **`++` (infixr 5)**: List concatenation.
- **`++-assoc`**: `(xs ++ ys) ++ zs = xs ++ (ys ++ zs)`.
- **`++_nil`**: `l ++ nil = l`.
- **`length_++`**: `length (l ++ l') = length l + length l'`.

#### Replicate

- **`replicate`**: `replicate n a` produces a list of `n` copies of `a`.
- **`length_replicate`**: `length (replicate n a) = n`.

#### Mapping

- **`map`**: Applies `f : A -> B` to each element of a list.
- **`length_map`**: `length (map f l) = length l`.
- **`map_id`**: `map id l = l`.
- **`map_comp`**: `map (g ∘ f) l = map g (map f l)`.

#### ListMonoid Instance

- **`ListMonoid`**: `Monoid` instance for `List A` with `nil` as identity and `++` as multiplication.

#### Splitting, Taking, and Dropping

- **`splitAt`**: Splits a list at position `n` into a pair `(take, drop)`.
  - **`appendLem`**: `take n l ++ drop n l = l`.
- **`take`**: Returns the first `n` elements of a list.
- **`drop`**: Returns the list after dropping the first `n` elements.

#### Replace and Slice

- **`replace`**: `replace l i s r` replaces a segment of `l` starting at index `i`, skipping `s` elements, with `r`.
- **`slice`**: `slice l i s` extracts `s` elements starting at index `i`.
  - **`appendLem`**: `take i l ++ slice l i s ++ drop s (drop i l) = l`.

#### List Predicates

- **`All`**: Inductive predicate asserting `P` holds for all elements; constructors `all-nil` and `all-cons`.
  - **`all-map`**: Transfers `All P (map f l)` to `All (P ∘ f) l`.
  - **`all-implies`**: If `All P l` and `All (P → Q) l`, then `All Q l`.
  - **`all-forall`**: If `P` holds universally, then `All P l`.
- **`All2`**: Pairwise predicate on all pairs `(x, y)` where `x` precedes `y`; constructors `all2-nil` and `all2-cons`.
- **`AllC`**: Consecutive-pair predicate; constructors `allC-nil`, `allC-single`, and `allC-cons`.
  - **`allC-tail`**: `AllC P (a :: l)` implies `AllC P l`.

#### Count

- **`count`**: Counts occurrences of `a` in a list (requires `DecSet`).
  - **`all-diff`**: If all elements differ from `a`, then `count l a = 0`.
- **`count_perm`**: Permutations preserve counts.
- **`count_++`**: `count (l ++ l') a = count l a + count l' a`.

#### Group

- **`group`**: Groups consecutive equal elements into `(element, count)` pairs (requires `DecSet`).
- **`group-sorted`**: If the list is sorted, so is `map fst (group l)`.
  - **`head-lem`**: Helper relating `headDef` to the first group entry.
- **`group-diff`**: If the list is sorted (in a `Poset`), consecutive groups have distinct keys.
  - **`diff-lem`**: Helper for proving elements differ from the head.
- **`group_count-lem`**: In a sorted list, each group's count equals the global count.
  - **`group_nil-lem`**: `group l = nil` implies `l = nil`.

#### Sort Module

##### Perm (Permutations)

- **`Perm`**: Inductive type for list permutations with constructors:
  - `perm-nil`: `Perm nil nil`.
  - `perm-::`: Head equality + tail permutation.
  - `perm-swap`: Swap of the first two elements.
  - `perm-trans`: Transitivity.
- **`perm-refl`**: Reflexivity of `Perm`.
- **`perm-sym`**: Symmetry of `Perm`.
- **`perm-head`**: `Perm (a :: xs ++ ys) (xs ++ a :: ys)`.
- **`perm_length`**: Permutations preserve length.

##### Sorted

- **`Sorted`**: Inductive predicate for sorted lists (requires `Preorder`); constructors `sorted-nil` and `sorted-cons`.
  - **`allSorted`**: In a sorted list `a1 :: l1 ++ a2 :: l2`, we have `a1 <= a2`.
    - **`aux`**: Helper for `allSorted`.
  - **`headSorted`**: A prefix of a sorted list is sorted.
  - **`tailSorted`**: A suffix of a sorted list is sorted.

##### Insertion Sort

- **`Insertion.sort`**: Insertion sort for lists (requires `Dec`).
  - **`insert`**: Inserts an element into a sorted list.
- **`sort-sorted`**: `Insertion.sort` produces a sorted list.
  - **`insert-sorted`**: Inserting into a sorted list preserves sortedness.
- **`sort-perm`**: `Insertion.sort` produces a permutation of the input.
  - **`insert-perm`**: `Perm (a :: xs) (insert a xs)`.
  - **`insert-comm`**: `insert a (insert a' l) = insert a' (insert a l)`.
    - **`aux`**: Helper for the `a < a'` case.
- **`perm_sort`**: Permuted lists have equal sorts.
- **`sorted_sort`**: Sorting an already-sorted list is the identity.

##### Red-Black Tree Sort

- **`RedBlack.sort`**: Red-black tree sort for lists (requires `Dec`).
  - **`Color`**: Data type with constructors `red` and `black`.
  - **`RBTree`**: Red-black tree type with constructors `rbLeaf` and `rbBranch`.
  - **`rbTreeToList`**: Converts an `RBTree` to a list (with accumulator).
  - **`aux`**: Builds an `RBTree` by folding insertions over the list.
  - **`repaint`**: Repaints the root of a tree to black.
  - **`insert`**: Inserts an element into an `RBTree`.
  - **`balanceLeft`** / **`balanceRight`**: Rebalancing operations after insertion.
- **`toList`**: Converts an `RBTree` to a list (without accumulator).
  - **`=rbTreeToList`**: `rbTreeToList t nil = toList t`.
    - **`aux`**: `rbTreeToList t l = toList t ++ l`.
- **`sort=insert`**: `RedBlack.sort l = Insertion.sort l`.
  - **`makeTree`**: Builds an `RBTree` from a list.
  - **`toList_repaint`**: `toList (repaint t) = toList t`.
  - **`toList_balanceLeft`** / **`toList_balanceRight`**: Balance operations preserve `toList`.
  - **`insert_++-left`** / **`insert_++-right`**: Insertion into a concatenation splits left or right.
  - **`toList_insert'`**: `toList (insert a t) = Insertion.sort.insert a (toList t)` when `t` is sorted.
  - **`toList_mkTree`**: `toList (makeTree l) = Insertion.sort l`.
  - **`makeTree-sorted`**: `toList (makeTree l)` is sorted.
  - **`toList_insert`**: `toList (insert a (makeTree l)) = Insertion.sort.insert a (toList (makeTree l))`.
  - **`makeTree_insert`**: Insertion into `makeTree` commutes with list insertion.
  - **`aux=makeTree`**: `toList (aux l (makeTree l')) = toList (makeTree (l ++ l'))`.

#### Membership and Contains

- **`contains`**: Boolean membership test for `DecSet`.
- **`InList`**: Propositional membership predicate with constructors `here` and `there`.
  - **`~` (infix 4)**: List equivalence: `l ~ l'` iff they have the same members.
  - **`~_::`**: `l ~ l'` implies `a :: l ~ a :: l'`.
    - **`InList_::`**: Helper transferring `InList` across `::`.
- **`InList_++`**: `InList a (l ++ l')` implies `InList a l || InList a l'`.
- **`InList_++-left`**: `InList a l` implies `InList a (l ++ l')`.
- **`InList_++-right`**: `InList a l'` implies `InList a (l ++ l')`.
- **`contains_InList`**: `contains l a = true` iff `InList a l`.

#### Set Operations

- **`union`**: Computes the union of two lists, skipping duplicates already in `l'`.
- **`union~++`**: `union l l' ~ l ++ l'` (same membership).
