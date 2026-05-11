### Topology.TopSpace.Category

The category of topological spaces and continuous maps.

This module packages topological spaces into a `Cat` instance, with continuous maps (`ContMap`) as morphisms. Identity and composition are inherited from `ContMap.id` and `ContMap.∘`, and the categorical laws together with univalence are discharged from the structural equality of topological spaces. This makes `TopCat` available as the standard ambient category whenever topological constructions need to be expressed categorically (functors, limits, adjunctions, etc.).

#### Category Instance

- **`TopCat`**: The category `Cat TopSpace` whose morphisms are continuous maps. Sets `Hom => ContMap`, `id => ContMap.id`, and `o => ContMap.∘`, and supplies the unit, associativity, and univalence laws so that topological spaces form a univalent category.
