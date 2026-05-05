### Algebra.Group.Solver

A reflection-based solver for equalities in groups, providing a syntactic term representation and normalization procedure that simplifies group expressions by canceling matching variables and their inverses.

#### Term Representation

- **`GroupTerm`**: Inductive type of syntactic group expressions over a variable set `V`, with constructors `var` (variable), `:ide` (identity), `:inv` (inverse), and `:*` (multiplication).

#### Solver Data Classes

- **`GroupData`**: Bundles a group `G : Group`, a variable set `V : \Set`, and an interpretation `f : V -> G` mapping syntactic variables to group elements.
- **`NatData`**: Specialization of `GroupData` fixing `V` to `Nat`, suitable for de Bruijn-style variable indexing.
- **`CGroupData`**: Specialization of `NatData` requiring the underlying group to be commutative (`CGroup`).

#### Variable Manipulation

- **`removeVar`**: Removes up to `n` occurrences of variable `v` (with given inversion flag `withInv`) from a term, returning the simplified term and the remaining count budget.
- **`countVars`**: Given a sorted list of `((var, sign), count)` triples, pairs up matching `(v, false)` / `(v, true)` entries and returns `(var, min count)` pairs indicating how many cancellations are possible per variable.
- **`removeVars`**: Iteratively removes pairs of matched positive/negative occurrences of each variable in the cancellation list from the term.

#### Term Flattening

- **`toList`**: Flattens a `GroupTerm Nat` into a list of `(variable, sign)` pairs by recursing through `:*` and toggling signs through `:inv`.
- **`countVar`**: Counts occurrences of variable `v` with sign `withInv` directly on a `GroupTerm Nat`.

#### Normalization

- **`simplify`**: Main entry point — converts a term to a list, sorts it (red-black), groups equal entries, computes cancellation counts, and removes the cancelable variable pairs from the original term to produce a normalized `GroupTerm Nat`.

#### Correctness Lemmas

- **`count-remove-lem`**: Removing occurrences of `v'` with one sign does not change the count of `v` with the opposite-tagged sign.
- **`toList-correct`**: The multiplicity of `(v, s)` in `toList t b` matches `countVar t v` with sign appropriately flipped by `b`.
- **`group-correct`**: After sorting and grouping, each `((v, s), n)` entry has `n <= countVar t v s`.
- **`countVars-correct`**: After pairing positive/negative groups, each resulting `(v, n)` satisfies `n <= countVar t v false` and `n <= countVar t v true`, justifying that `n` cancellations are sound.
- **`countVars-ineq`**: If `x` differs from every variable in the input list, it differs from every variable in `countVars` of that list.
- **`countVars-diff`**: Pairwise distinctness of variables is preserved by `countVars` under the sign-ordering invariant.
- **`sort-correct-lem`**, **`sort-correct`**: After sorting, adjacent equal-variable entries always appear in `(false, true)` order — the key invariant making `countVars` sound.
