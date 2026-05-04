### Topology.UniformSpace.StronglyComplete

Strongly complete uniform spaces and the strong completion construction, providing a universal lift for uniform maps from weakly dense uniform embeddings.

#### Main Class

- **`StronglyCompleteUniformSpace`**: A uniform space that is simultaneously strongly regular, complete, and strongly complete as a cover space. Extends `StronglyRegularUniformSpace`, `CompleteUniformSpace`, and `StronglyCompleteCoverSpace`.

#### Universal Property

- **`weaklyDense-uniform-lift`**: Given a weakly dense uniform embedding `f : X -> Y` and a uniform map `g : X -> Z` into a strongly complete uniform space, produces the unique uniform extension `Y -> Z`. This is the universal property characterizing strongly complete uniform spaces.

#### Strong Completion

- **`UniformStrongCompletion`**: The strong completion of a strongly regular uniform space `X`, equipped with the canonical strongly complete uniform space structure. A cover `D` is uniform iff there exists a uniform cover `C` of `X` such that every `U : C` is contained in some `V : D` via `mkSet`.
  - **`properUniform`**: If `X` is proper uniform, then so is its strong completion.
  - **`makeUniform`**: Constructs a uniform cover on the completion from a uniform cover `C` of `X` by taking `{mkSet U | U : C}`.
- **`uniform-strongCompletion`**: The canonical uniform map `X -> UniformStrongCompletion X` embedding a strongly regular uniform space into its strong completion.
  - **`isDenseEmbedding`**: The canonical map is a weakly dense uniform embedding, witnessing the universal property.

#### Filter Characterizations

- **`cauchyFilter-uniform-char`**: A set filter meets every Cauchy cover iff it meets every uniform cover, characterizing Cauchy filters in terms of the uniform structure.
- **`regularFilter-uniform-char`**: For a Cauchy filter on a regular preuniform space, the rather-below regularity condition (`V <=< U`) is equivalent to the strong rather-below condition (`V <=* U`).
