### Topology.Locale.LocaleProduct

- **`ProductLocale.proj1`**, **`ProductLocale.proj2`**: Projections from the product locale to its factors.
- **`LocaleHasProduct`**: Instance making `ProductLocale` the binary product in the category of locales.
- **`ProductLocale`**: Product of two locales `X` and `Y`, defined as `SiteLocale` of a preorder site on `\Sigma X Y` (ordered componentwise) whose basic covers of `(x,y)` are the families obtained by covering `x` in `X` or covering `y` in `Y`.
- **`localeDiagonal`**: The diagonal `LocaleHom L (ProductLocale L L)`, the pairing of two identities.
