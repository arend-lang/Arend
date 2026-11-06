### Set.Fin.Noetherian

Noetherian sets in the sense of Spiwack and Coquand, defined through bars.

A `NoetherianSet` is a set for which the property "some two entries at different positions are equal" is a bar (`Bar` of `Logic.Bar`) on the arrays over the set, as in Spiwack and Coquand, Constructively Finite?, 2010. Every such set satisfies the pigeonhole principle, which is derived from `bar-stream`, so the class extends `PigeonholeSet`.

#### Noetherian sets

- **`NoetherianSet`**: A class extending `PigeonholeSet` with the field `isNoetherianSet`, that `Bar` holds from `nil` for the predicate that an array has a repetition (`i /= j` with `l i = l j`). The pigeonhole field is derived from it.
