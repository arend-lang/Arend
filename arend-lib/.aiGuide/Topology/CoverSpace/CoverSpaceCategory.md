### Topology.CoverSpace.CoverSpaceCategory

The categories of precover spaces and of cover spaces.

- **`PrecoverSpaceCat`**: The category `Cat PrecoverSpace` whose objects are precover spaces and whose morphisms are `PrecoverMap`s. Identity is `PrecoverMap.id`, composition is `PrecoverMap.∘`, and the instance witnesses the category laws (`id-left`, `id-right`, `o-assoc`) along with `univalence`.
- **`CoverSpaceCat`**: The category `Cat CoverSpace` whose objects are cover spaces and whose morphisms are `CoverMap`s. Identity is `CoverMap.id`, composition is `CoverMap.∘`, with the category laws and univalence supplied.
