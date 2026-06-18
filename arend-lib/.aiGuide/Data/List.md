### Data.List

Inductive lists with concatenation, indexing, splitting, predicates, sorting, and membership.

The module defines the standard `List A` data type and builds up its algebra: concatenation `++` is associative with `nil` as identity (witnessed by a `Monoid` instance), `length` and `map` satisfy the usual functoriality lemmas, and positional access via `!!` uses `Fin (length l)` for safe indexing. Predicate types `All`, `All2`, `AllC`, and `InList` express universal/pairwise/consecutive properties and membership as `\Prop`. The `Sort` submodule provides permutation `Perm` and `Sorted` predicates, two implementations (insertion sort and red-black tree sort), and proves their equivalence on decidable orders; `count` and `group` give multiset-style operations on `DecSet`s, with key lemmas that connect `count` to `Perm` and `group` to `Sorted` lists.

#### Core Type and Basic Operations

- **`List`**: Inductive list type with constructors `nil` and `:: : A -> List A -> List A`.
- **`length`**: Number of elements in a list.
- **`!!`**: Safe indexing `List A -> Fin (length l) -> A`.
- **`++`**: Right-recursive list concatenation.
- **`headDef`**: Head of a list with a default for the empty case.
- **`tail`**: Drop the first element (returns `nil` on `nil`).
- **`replicate`**: Build a list of `n` copies of an element.

#### Concatenation Lemmas

- **`++-assoc`**: Associativity of `++`.
- **`++_nil`**: Right identity: `l ++ nil = l`.
- **`length_++`**: `length (l ++ l') = length l + length l'`.
- **`length_replicate`**: `length (replicate n a) = n`.
- **`ListMonoid`**: `Monoid` instance on `List A` with `nil`/`++`.

#### Map

- **`map`**: Apply `f : A -> B` pointwise.
- **`length_map`**: `map` preserves length.
- **`map_id`**: `map id = id`.
- **`map_comp`**: `map` distributes over composition.

#### Splitting and Slicing

- **`splitAt`**: Split at position `n` into a pair `(prefix, suffix)`.
- **`splitAt.appendLem`**: `take n l ++ drop n l = l`.
- **`take`**, **`drop`**: First/second component of `splitAt`.
- **`replace`**: Replace a slice of length `s` starting at `i` with another list.
- **`slice`**: Sublist of length `s` starting at offset `i`.
- **`slice.appendLem`**: Decomposition `take i l ++ slice l i s ++ drop s (drop i l) = l`.

#### Universal Predicates on Lists

- **`All`**: `\Prop`-valued predicate that `P` holds at every element; constructors `all-nil`, `all-cons`.
- **`all-map`**: `All P (map f l) -> All (P ∘ f) l`.
- **`all-implies`**: Pointwise modus ponens: combine `All P l` with `All (P → Q) l`.
- **`all-forall`**: From `(∀ a, P a)` derive `All P l`.
- **`All2`**: `P` holds for every ordered pair of distinct positions.
- **`AllC`**: `P` holds for every consecutive pair (used for sortedness).
- **`allC-tail`**: Tail of an `AllC` is still `AllC`.

#### Counting and Grouping

- **`count`**: Number of occurrences of `a` in a list over a `DecSet`.
- **`count.all-diff`**: `All (a /=) l -> count l a = 0`.
- **`count_perm`**: Counts are permutation-invariant.
- **`count_++`**: `count` is additive over `++`.
- **`group`**: Run-length encoding into `List (\Sigma A Nat)` using decidable equality.
- **`group-sorted`**: For sorted input, the keys of `group l` are sorted.
- **`group-diff`**: For sorted input on a `Poset`, adjacent group keys are distinct (`All2 (/=)`).
- **`group_count-lem`**: For sorted input, each group's tally equals `count l a`.

#### Permutations (`Sort.Perm`)

- **`Perm`**: Inductive permutation relation with constructors `perm-nil`, `perm-::`, `perm-swap`, `perm-trans`.
- **`Perm.perm-refl`**, **`Perm.perm-sym`**: Reflexivity and symmetry.
- **`Perm.perm-head`**: Move an element across a prefix: `Perm (a :: xs ++ ys) (xs ++ a :: ys)`.
- **`Perm.perm_length`**: Permutations preserve length.

#### Sortedness (`Sort.Sorted`)

- **`Sorted`**: Inductive sortedness on a `Preorder`; `sorted-cons` requires the head dominates the next head.
- **`Sorted.allSorted`**: In a sorted list, the head is below any element appearing later (with `aux` helper for prefixes).
- **`Sorted.headSorted`**, **`Sorted.tailSorted`**: Sortedness is preserved by taking prefixes/suffixes.

#### Insertion Sort (`Sort.Insertion`)

- **`sort`**: Insertion sort over a decidable linear order.
- **`sort.insert`**: Insert an element at the correct position via `dec<_<=`.
- **`sort-sorted`** (with **`insert-sorted`**): Output of `sort` is `Sorted`.
- **`sort-perm`** (with **`insert-perm`**, **`insert-comm`**): `xs` is a `Perm` of `sort xs`.
- **`perm_sort`**: `Perm xs ys -> sort xs = sort ys`.
- **`sorted_sort`**: `sort` is the identity on sorted lists.

#### Red-Black Tree Sort (`Sort.RedBlack`)

- **`sort`**: Sort by inserting all elements into a red-black tree, then traversing.
- **`Color`**, **`RBTree`**: Internal red-black tree representation with `red`/`black` and `rbLeaf`/`rbBranch`.
- **`rbTreeToList`**, **`toList`**: Accumulator-based and direct in-order traversals.
- **`aux`**, **`repaint`**, **`insert`**, **`balanceLeft`**, **`balanceRight`**: The standard insertion + rebalancing combinators.
- **`sort=insert`**: Red-black tree sort agrees with insertion sort (with helper lemmas `toList_repaint`, `toList_balance{Left,Right}`, `toList_insert`, `makeTree_insert`, `aux=makeTree` linking the tree representation to the list-level `Insertion.sort.insert`).

#### Membership and Set-like Operations

- **`contains`**: Boolean membership on a `DecSet`.
- **`union`**: Concatenation with duplicate suppression by `contains`.
- **`InList`**: Propositional membership; constructors `here`, `there`.
- **`InList.~`**: Setwise equivalence of lists `\Pi a, InList a l <-> InList a l'`.
- **`InList.~_::`** (with **`InList_::`**): `~` is preserved by consing the same head.
- **`InList_++`**, **`InList_++-left`**, **`InList_++-right`**: Membership distributes over `++`.
- **`contains_InList`**: `contains l a = true` iff `InList a l`.
- **`union~++`**: `union l l'` is set-equivalent to `l ++ l'`.
