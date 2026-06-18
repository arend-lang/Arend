### Data.Array.Sort

Sorting for arrays over decidable linear orders, with permutation and sortedness specifications.

This module lifts list-based insertion sort (from `Data.List`) to length-indexed arrays by routing through `toList`/`fromList` conversions. The main `sort` function preserves length via transport, and the surrounding lemmas establish the two characteristic properties of any sorting routine: the output is sorted (`sort-sorted`) and a permutation of the input (`sort-perm`). A uniqueness lemma (`perm_=`) shows that any two sorted permutations are equal, making sorted-permutation a complete specification.

#### Main Function

- **`sort`**: Sorts an `Array A l.len` over a decidable linear order, returning an array of the same length. Implemented by converting to a list, applying `Sort.Insertion.sort`, converting back to an array, and transporting along the length-preservation proof.

#### Sortedness Predicate

- **`IsSorted`**: Predicate on an array stating that `l i <= l j` whenever `i < j` as natural numbers. Defined for any `Preorder`.
- **`sorted_transport`**: Transporting an array along an equality of lengths preserves `IsSorted`.

#### Conversion Between List and Array Sortedness

- **`list_sorted`**: If a `List` is `Sort.Sorted`, then `fromList l` satisfies `IsSorted`.
- **`list_sorted.headDef_fromList`**: Helper lemma: when `l` is sorted, `headDef a l <= fromList l j` for any index `j`.
- **`sorted_list`**: Conversely, if an array satisfies `IsSorted`, then `toList l` is `Sort.Sorted`.

#### Correctness of `sort`

- **`sort-sorted`**: The result of `sort l` is `IsSorted`.
- **`sort-perm`**: The result of `sort l` is a `Perm`utation of `l` (defined via `EPerm`-to-`Perm` transport).

#### Permutation Conversion

- **`sort-perm.list_perm`**: Lifts a `Sort.Perm` between lists to an `EPerm` between their `fromList` arrays, by case analysis on the list permutation constructors (`perm-nil`, `perm-::`, `perm-swap`, `perm-trans`).
- **`sort-perm.sort-eperm`**: The original array `l` is `EPerm`-equivalent to `fromList (Sort.Insertion.sort (toList l))`.
- **`perm_list`**: Lifts a `Perm` between equal-length arrays to a `Sort.Perm` between their `toList` representations, by case analysis on array permutation constructors.

#### Uniqueness

- **`perm_=`**: Two sorted arrays of the same length that are permutations of each other are equal. This makes `IsSorted` + `Perm` a complete specification of `sort`'s output.
