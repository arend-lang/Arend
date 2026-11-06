### AG.Scheme

Schemes as locally ringed locales that are covered by affine opens.

A `Scheme` is a locally ringed locale (see `AG.RingedLocale`) whose top element is covered by the join of its affine opens. The notions of ringed locale, its morphisms and category, and the spectrum of a ring live in `AG.RingedLocale` and `AG.AffineScheme`.

#### Schemes

- **`Scheme`**: Extends `LocallyRingedLocale` with `isLocallyAffine : top <= Join IsAffineOpen`: the top element of the locale is covered by affine opens.
