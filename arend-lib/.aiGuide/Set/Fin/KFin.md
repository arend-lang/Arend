### Set.Fin.KFin

Kuratowski-finite sets: types admitting a surjection from a standard finite set.

A `KFinSet` is a set equipped with a cardinality bound and a (truncated) surjection from `Fin finCard`, making it finitely enumerable up to repetition. Unlike `FinSet`, no decidable equality is required, so quotients of finite sets remain Kuratowski-finite even when equality is not decidable. The class extends `BoundedPigeonholeSet` because any surjection from `Fin n` automatically witnesses the pigeonhole principle, and the module shows that adding decidable equality recovers the stronger `FinSet` structure.

#### Main Class

- **`KFinSet`**: Extends `BoundedPigeonholeSet`. A set `E` with a bound `finCard` and a truncated surjection `Fin finCard -> E`, automatically yielding the bounded pigeonhole property.

#### Decidability and Search

- **`search`**: For a decidable predicate `A : E -> \Prop`, decides `∃ (e : E) (A e)` by exhausting the surjection's domain.
- **`||-search`**: Constructive choice: from `\Pi (x : E) -> A || B x` derive `A || (\Pi (x : E) -> B x)`.
- **`search_yes_reduce`**: Reduction lemma — `search` returns `yes` when a witness exists.
- **`search_no_reduce`**: Reduction lemma — `search` returns `no` when no witness exists.
- **`dec`**: Decidability of inhabitation: `Dec (TruncP E)`.

#### Construction from Arrays

- **`fromArray`**: Builds `KFinSet A l.len` from an array `l : Array A` whose elements cover all of `A`.
- **`toArray`**: Extracts a covering array from a `KFinSet`, witnessing surjectivity onto its elements.

#### Relation to FinSet

- **`KFin+Dec=>Fin`**: A Kuratowski-finite set with decidable equality is a `FinSet` — decidable equality lets one prune the surjection to a bijection.

#### Quotients

- **`QuotientKFin`**: The quotient `Quotient R` of a `KFinSet A` by any relation `R` is itself Kuratowski-finite with the same cardinality bound `A.finCard`, since the canonical projection composes with the surjection.
