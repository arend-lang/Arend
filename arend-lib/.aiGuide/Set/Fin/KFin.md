### Set.Fin.KFin

Kuratowski-finite sets: types admitting a surjection from a standard finite type, generalizing finiteness without requiring decidable equality.

#### Main Class

- **`KFinSet`**: Extends `BoundedPigeonholeSet`. A set `E` together with a cardinal bound `finCard` and a proof `finSurj : ∃ (f : Fin finCard -> E) (IsSurj f)` exhibiting surjective enumeration. The pigeonhole property is derived automatically by transporting the standard pigeonhole on `Fin n` along the surjection.

#### Constructions

- **`fromArray`**: Builds a `KFinSet A l.len` from any array `l : Array A` whose entries cover all of `A`, i.e. for every `a : A` some index `i` satisfies `l i = a`.
- **`toArray`**: Inverse extraction — every `KFinSet A` yields an array `l : Array A` together with surjectivity onto `A`.

#### Relating to Finite Sets

- **`KFin+Dec=>Fin`**: A Kuratowski-finite set with decidable equality is genuinely finite (`FinSet`); decidability lets one prune the surjective enumeration to a bijection.

#### Closure Properties

- **`QuotientKFin`**: Quotients of Kuratowski-finite sets are Kuratowski-finite, with the same cardinality bound `A.finCard`; the surjection lifts through the quotient projection.
