### Data.Array.Sort

This module provides sorting for arrays with a decidable linear order, along with sorted-ness predicates and uniqueness results.

#### Sort Function

- **`sort`**: Sorts an `Array A` (for `LinearOrder.Dec A`) into an `Array A l.len`.

#### IsSorted Predicate

- **`IsSorted`**: A predicate on arrays: `l i <= l j` whenever `i < j`.
- **`sorted_transport`**: `IsSorted` is preserved under transport of array length.

#### Conversions with List Sorted

- **`list_sorted`**: `Sort.Sorted` on a list implies `IsSorted` on the corresponding `fromList` array.
  - **`headDef_fromList`**: Helper: head of a sorted list is `<=` all elements.
- **`sorted_list`**: `IsSorted` on an array implies `Sort.Sorted` on `toList`.

#### Sort Correctness

- **`sort-sorted`**: `sort l` is `IsSorted`.
  - **`list_perm`**: List `Perm` implies array `EPerm` (via `fromList`).
  - **`sort-eperm`**: `EPerm l (fromList (Insertion.sort (toList l)))`.

#### Perm and Uniqueness

- **`perm_list`**: Fixed-length `Perm` implies list `Sort.Perm` (via `toList`).
- **`perm_=`**: If two same-length arrays are both `IsSorted` and related by `Perm`, they are equal.
