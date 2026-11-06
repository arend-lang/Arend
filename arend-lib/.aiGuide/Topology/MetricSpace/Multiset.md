### Topology.MetricSpace.Multiset

The matching pseudometric on `n`-element arrays over a pseudometric space and the metric completion giving completed `n`-multisets.

For a pseudometric space `X`, the module equips `Array X n` with the distance `min over permutations σ of max over i of d(r_i, s_{σ i})`, built choice-free from `Big` joins and meets in `Real`. Its kernel identifies arrays that are permutations of each other, so the metric completion `MultisetCompletion X n` is the space of completed `n`-multisets over `X`. This is the carrier for Richman's spectrum of a monic polynomial (Pacific J. Math. 196, 2000). The module has three parts: the matching cost of a pairing (`maxDist`) and the one-sided and symmetrised distances (`rawDist`, `matchDist`); the lemma `extendMatch` for growing a pairing by one matched entry; and the distance `minDist` from a point to an array, which is 1-Lipschitz in the array.

#### Costs of pairings

- **`pairCosts`**: The array of the `n` distances `ldist (r i) (s (f i))` induced by a map `f : Fin n -> Fin n` between arrays `r` and `s`.
- **`maxDist`**: The cost of a map `f` as a pairing of `r` with `s`: the largest of the distances in `pairCosts`, or `0` when `n = 0`.
- **`maxDist>=0`**: `maxDist` is nonnegative.
- **`maxDist-cond`**: Each induced distance `ldist (r i) (s (f i))` is at most `maxDist r s f`.
- **`maxDist-univ`**: If `c >= 0` bounds every induced distance, then `maxDist r s f <= c`.
- **`maxDist-triang`**: Triangle inequality for pairings: the cost of the composite pairing `g ∘ f` is at most `maxDist r s f + maxDist s t g`.
- **`permCosts`**: The array of costs of all the `fac n` permutations (via `SymFin.fin_equiv`).

#### The one-sided distance

- **`rawDist`**: The least cost of a permutation (the meet of `permCosts`, together with the identity pairing).
- **`rawDist>=0`**: `rawDist` is nonnegative.
- **`rawDist-cond`**: `rawDist` is below the cost of any injective pairing.
- **`rawDist-univ`**: `rawDist` is the greatest lower bound of the costs of injective pairings.
- **`rawDist-lt`**: If `rawDist r s < c`, then there merely exists an injective pairing of cost below `c`. The slack is needed because the optimal permutation does not exist constructively.
- **`rawDist-triang`**: Triangle inequality for `rawDist`.

#### Extending a pairing

- **`extendMatch`**: A pairing of the arrays shortened at `0` and at `j`, extended by matching position `0` with `j`.
- **`extendMatch-inj`**: The extension of an injective pairing is injective.
- **`maxDist-extend`**: The cost of the extended pairing is below `c` when the matched pair's distance and the cost of the shortened pairing are both below `c`.

#### The matching pseudometric

- **`matchDist`**: The symmetrised distance `rawDist r s ∨ rawDist s r`.
- **`matchDist-refl`**: `matchDist r r = 0`.
- **`matchDist-symm`**: `matchDist r s = matchDist s r`.
- **`matchDist-triang`**: Triangle inequality for `matchDist`.
- **`MultisetPseudoMetric`**: The `PseudoMetricSpace` instance on `Array X n` with distance `matchDist`.
- **`MultisetCompletion`**: The metric completion of `MultisetPseudoMetric`, a `CompleteMetricSpace` of completed `n`-multisets over `X`; filter-based and choice-free.

#### Distance from a point to an array

- **`pointCosts`**: The array of distances from a point `z` to the entries of `s`.
- **`minDist`**: The distance from `z` to the nearest entry of a nonempty array `s`.
- **`minDist>=0`**: `minDist` is nonnegative.
- **`minDist-cond`**: `minDist s z` is at most the distance to any entry `s j`.
- **`minDist-index`**: If `minDist s z < c` then some entry `s j` is at distance below `c` from `z`.
- **`minDist-lip`**: `minDist` is 1-Lipschitz in the array with respect to `rawDist`: `minDist r z <= minDist s z + rawDist s r`.
- **`minDist-lipM`**: The same estimate with `matchDist r s`.
