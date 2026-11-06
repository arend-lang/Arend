### Set.SubSet

- **`SubSet`**: A predicate `contains : S -> \Prop` on a `BaseSet S`; provides `ISet`, the induced sigma `BaseSet`.
- **`DecSubSet`**: A `SubSet` whose membership is decidable (`isDec`).
- **`DecSubSet.max`**: The total subset (always `\Sigma`), with trivial decidability.
- **`SubSet.Total`**: The type of elements of `S` together with a proof that they satisfy `contains`, i.e. `\Sigma (x : S) (contains x)`.
- **`SubSet.ISet`**: The `BaseSet` whose carrier is `Total`, the subset viewed as a set in its own right.
- **`SubsetPoset`**: The inclusion order on subsets of a `BaseSet X`: `S <= S'` iff every `x` in `S` is in `S'`; antisymmetry uses `propExt`.
