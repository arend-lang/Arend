### Algebra.Group.Solver

Reflective decision procedure for proving group and commutative-group equalities by normalizing syntactic `GroupTerm` expressions.

The module defines a free syntax `GroupTerm V` over variables `V` together with an `interpret` function into a target group `G`. Solvers are organized as classes (`GroupData`, `NatData`, `CGroupData`) that bundle the carrier group with a variable assignment and provide a `simplify` operation paired with a `simplify-correct` lemma showing `interpret t = interpret (simplify t)`. For general groups (`NatData`), simplification proceeds by repeatedly locating adjacent inverse pairs of leaves and removing them, preserving normal form (no nested non-variable inverses). For commutative groups (`CGroupData`), terms are flattened to a list of (variable, sign) leaves, sorted, grouped, and cancelled bulk via `removeVar`/`removeVars`, exploiting commutativity to collect like terms.

#### Term Syntax and Interpretation

- **`GroupTerm`**: Inductive syntax of group expressions over variables `V`, with constructors `var`, `:ide`, `:inv`, and `:*`.
- **`GroupData`**: Class bundling a `Group G`, a variable set `V : \Set`, and an assignment `f : V -> G`. Provides:
  - **`interpret`**: Evaluates a `GroupTerm V` in `G` by mapping `var` via `f`, `:ide` to `ide`, `:inv` to `inverse`, and `:*` to `*`.

#### Non-commutative Solver (NatData)

- **`NatData`**: Extends `GroupData` with `V => Nat`. Implements the inverse-pair cancellation strategy.

##### Normal Form

- **`isInNF`**: Predicate stating that a term is in normal form — no `:inv` is applied to a non-variable subterm.
- **`isInNF-dec`**: Decision procedure returning `Maybe (isInNF t)`.

##### Leaves

- **`Leaf`**: Inductive type with `var-leaf v` and `inv-var-leaf v`, representing positive/negative variable occurrences.
  - **`leaf-to-term`**, **`isInv`**, **`val`**: Conversion to a term, sign indicator, and underlying variable.
  - **`toLeaf`**: Recognizes a term as a leaf if it is `var v` or `:inv (var v)`.
  - **`toLeafToTerm-isInv`**: Round-trip soundness lemma for `toLeaf`.
- **`count-leaves`**: Counts leaf occurrences in a term (variables and inv-of-variables count 1; non-variable inverses count 0).
- **`get-leaf`**: Retrieves the `n`-th leaf of a term, or `:ide` if out of range.
- **`get-rightmost-leaf`**: The last leaf of a term.
- **`get-leaf-less-lemma`**: When `ind < count-leaves l + 1`, the `ind`-th leaf of `l :* r` lives in `l`.
- **`get-leaf-zro-leaves-lemma`**: A normal-form term with zero leaves interprets to `ide`.

##### Pair Removal

- **`remove-pair`**: Given a term and an index, removes the leaf at that index together with its right neighbour; returns the rewritten term and a flag indicating whether the removal collapsed to `:ide`.
  - **`processLeaf`**: Helper deciding leaf-level removal at indices 1 or 2.
- **`rp-preserves-nf`**: Pair removal preserves normal form.
- **`rp-count-leaves-lemma`**: Pair removal does not increase leaf count.
- **`rp-correctness-both-inside`**: Soundness when both removed leaves are in the interior — interpretation is preserved given the inverse-pair hypothesis.
- **`rp-correctness-one-inside-left`**, **`rp-correctness-one-inside-right`**: Soundness for boundary cases (factoring out the first/last leaf).
- **`rp-returns-true-lemma`**, **`rp-returns-true-lemma-left`**, **`rp-returns-true-lemma-right`**, **`rp-returns-true-lemma'`**, **`rp-returns-true-lemma''`**, **`rp-returns-true-lemma'''`**: Structural lemmas characterizing when `remove-pair` reports success — the term must be a product of two leaves at the appropriate index.

##### Search and Top-level Simplification

- **`find-fstLeafToRemoveInd`**: Searches the term for an index of a leaf whose interpretation is the inverse of the next leaf's, returning the index together with the required side conditions and bounds.
  - **`find-fstLeafToRemoveInd-rec`**: Bounded recursion driving the search.
  - **`leaf-isInv-lemma`**: Two leaves with opposite signs and equal variable interpret as inverses of each other.
- **`find-pair-to-remove`**: Alternative left-to-right scan threading the last visited leaf, returning the cancellation index.
- **`simplify`**: Applies one round of pair removal at a found cancellation site, when the term is in normal form.
- **`simplify-correct`**: `interpret t = interpret (simplify t)`.

#### Commutative Solver (CGroupData)

- **`CGroupData`**: Extends `NatData` with `G : CGroup`. Overrides `simplify` with a multiset-based normalization exploiting commutativity.

##### Bulk Variable Removal

- **`removeVar`** (in `\where`): Removes up to `n` occurrences of variable `v` (with chosen sign `withInv`) from a term, returning the rewritten term and the residual count not yet consumed.
- **`removeVar-lem`**: Core correctness — for any `n`, `interpret (removeVar n t v b).1 * pow (±f v) (n ∧ countVar t v b) = interpret t`, with the residual count equal to `n -' countVar t v b`.
  - **`pow-lem`**: Powers of `f v` versus `inverse (f v)` are inverses of each other.
  - **`sum-lem`**: Distribution lemma `a ∧ (b + c) = a ∧ b + (a -' b) ∧ c` used to split counts across `:*`.
- **`removeVar_<=-lem`**: When `n <= countVar t v b`, the meet simplifies and we get the cleaner equation `interpret (removeVar ...).1 * pow ... n = interpret t`.
- **`removeVar-correct`**: Removing `n` occurrences of `v` with both signs successively preserves the interpretation, so `n` cancellations are valid.

##### Listing, Sorting, Grouping

- **`countVar`** (in `\where`): Counts signed occurrences of variable `v` in a term.
- **`toList`** (in `\where`): Flattens a term into a list of `(variable, sign)` pairs, with the outer `withInv` toggling signs through `:inv`.
- **`countVars`** (in `\where`): Walks a sorted list of `((var, sign), count)` triples and pairs adjacent entries with equal variable but opposite signs into a single `(var, min count)` cancellation entry.
- **`removeVars`** (in `\where`): Iterates `removeVar` over a list of `(var, count)` pairs, each removing both positive and negative occurrences.
- **`simplify`** (in `\where`): The CGroup pipeline — `toList` ⟶ red-black `sort` ⟶ `group` ⟶ `countVars` ⟶ `removeVars`.

##### Correctness Pipeline

- **`removeVars-correct`**: `interpret (removeVars t l) = interpret t` when each entry's count is bounded by both signed counts and the variables are pairwise distinct.
  - **`count-removeVar-lem`**: Removing variable `v` does not change counts of any other variable `u`.
  - **`count-removeVars-lem`**: Removing a list of variables disjoint from `v` does not change `v`'s count.
- **`toList-correct`**: `count (toList t b) p` equals `countVar t p.1` with the appropriate sign flip.
- **`group-correct`**: Each grouped count is bounded by the corresponding signed variable count in `t`.
- **`countVars-correct`**: After `countVars`, each entry's count is bounded by both the positive and negative counts of its variable.
- **`countVars-ineq`**, **`countVars-diff`**: `countVars` preserves variable distinctness conditions.
- **`sort-correct-lem`**, **`sort-correct`**: Sorting yields adjacent entries with the same variable in `(false, true)` sign order, ensuring `countVars` finds genuine cancellation pairs.
- **`count-remove-lem`**: Removing `(v', not withInv)` does not change `(v, withInv)`-counts.
- **`simplify-correct`**: `interpret t = interpret (simplify t)` for the commutative pipeline.
