### Category.Limit.WidePullback

- **`WidePullback`**: Record witnessing a wide pullback of the family of morphisms `maps j : objs j -> z` (`j : J`) in `D`: its field `wpbLim` is a limit of the diagram `diagram maps` over the shape `Shape J`.
- **`WidePullback.Shape`**: The graph shape of a wide pullback over index set `J`: vertices are `Or (\Sigma) J` (one apex plus one node per index), with a unique edge from each `inr i` to the apex `inl ()`.
- **`WidePullback.diagram`**: Builds the `Diagram (Shape J) D` whose apex is `z`, leaves are `objs i`, and edges are the given `maps i`.
