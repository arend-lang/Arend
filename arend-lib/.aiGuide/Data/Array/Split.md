### Data.Array.Split

This module provides a splitting lemma for arrays of pairs by their second component.

#### Nub Split

- **`nub-split`**: For `l : Array (\Sigma A B)` with `B : DecSet`, `EPerm l (Big ++ nil (map (\lam b => keep (\lam s => decideEq b s.2) l) (nub (map __.2 l))))`. That is, `l` is a permutation of the concatenation of sub-arrays grouped by their second component (using `nub` to deduplicate keys).
  - **`aux`**: Helper taking an explicit array of keys `bs` with injectivity and coverage proofs.
