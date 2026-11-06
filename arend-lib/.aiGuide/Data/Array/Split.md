### Data.Array.Split

Splitting an array of pairs into buckets indexed by the distinct second components.

This module provides a single permutation lemma showing that any array of `(A, B)` pairs (with `B` decidable) can be reorganized as a concatenation of "buckets," one per distinct `B`-value occurring in the array. The construction uses `nub` to enumerate the distinct keys and `keep` to filter the array by each key, with the result expressed as an `EPerm` (extensional permutation) so the original multiplicity of elements is preserved. The proof proceeds by induction on the deduplicated key list, peeling off one bucket at a time via `keep_remove-split` and reusing injectivity of `nub` to maintain the invariant that all remaining elements carry one of the remaining keys.

#### Bucket Splitting

- **`nub-split`**: For `l : Array (\Sigma A B)` with `B : DecSet`, produces an `EPerm` between `l` and the concatenation `Big ++ nil (map (\lam b => keep (\lam s => decideEq b s.2) l) (nub (map __.2 l)))`, i.e., `l` is permutation-equivalent to the concatenation of its key-buckets enumerated by the distinct second projections.
- **`nub-split.aux`**: The general inductive engine, parametrized by an arbitrary array `bs : Array B` of keys assumed injective (`IsInj bs`) and surjective onto the second projections of `l` (every `(l j).2` is hit by some `bs k`); produces the same bucketed permutation. Recurses on `bs`: the empty case forces `l` to be empty, and the cons case splits off the first bucket via `keep_remove-split`, then applies the induction hypothesis to the remainder using `remove.preimage` and `unfsuc` to transport the injectivity/surjectivity hypotheses.
