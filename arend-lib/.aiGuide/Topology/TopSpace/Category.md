### Topology.TopSpace.Category

The category of topological spaces with continuous maps as morphisms.

#### Category Instance

- **`TopCat`**: The category `Cat TopSpace` whose objects are topological spaces and whose morphisms are continuous maps (`ContMap`). Identity is `ContMap.id`, composition is `ContMap.∘`, and the categorical laws together with univalence are established for this structure. Use this when working with topological spaces in a categorical setting (functors into/out of `Top`, limits, colimits, adjunctions).
