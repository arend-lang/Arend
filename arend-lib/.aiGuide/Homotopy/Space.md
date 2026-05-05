### Homotopy.Space

Basic type classes for spaces (types) with optional inhabitation witnesses.

#### Classes

- **`BaseSpace`**: A class wrapping an underlying type `E : \Type`. Used as the base for spaces that need additional structure layered on top.
- **`InhSpace`**: Extends `BaseSpace` with an inhabitation witness `isInh : TruncP E`, representing a propositionally non-empty space.
