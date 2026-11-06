# Changelog

The changes in each release of the Arend language, the CLI, the IntelliJ Arend plugin and arend-lib. A release's section is the plugin's change notes on JetBrains Marketplace and the body of its GitHub release; see [RELEASING.md](RELEASING.md). Earlier releases are announced on [the Arend site](https://arend-lang.github.io/).

## Unreleased

### Language

### Plugin

### CLI

### arend-lib

## 1.12.0 - 2026-07-29

This release includes a major internal overhaul of the universe/level system, an opt-in binary caching mode for the CLI, and substantial additions to arend-lib, most notably in algebraic geometry and analysis.

### Language

- Introduce types not bounded by universe levels. The old explicit level-bound machinery (`\lp`, `\plevels`, default level parameters) has been removed; level polymorphism is now inferred automatically by the level solver.
- Introduce classes not bounded by universe levels, with a local override available when a definition needs to be pinned to a finite level.
- Prefix application of infix operators is now forbidden. Applying an infix operator to a single argument is instead interpreted as a right section.

### Plugin

- Updated IntelliJ platform support to 2025.3, then to 2026.2.
- Added basic Arend support for the JetBrains Educational plugin (course builder, project generator, and task checker).
- Fixed a UI freeze in Find Usages, formatter performance issues, and the Arend Messages panel.

### CLI

- Added an opt-in binary cache (`.arc`) serialization mode for faster incremental rechecking: pass `--serialize` to persist typechecked results, and `-r`/`--recompile` to ignore existing caches.
- Added console query tooling: `-ss` (symbol search), `-ps` (proof search, previously IDE-only), `-fu` (find usages), `-ch` (class hierarchy), and `-sc` (scope), all with `--json` output support.
- REPL improvements: Ctrl+J (multi-line input), Ctrl+C (clear line), Ctrl+D (quit), `-L` support, and module preloading.
- Added support for typechecking individual definitions from the CLI.

### arend-lib

- Introduce scheme sites, affine opens, affine schemes, and general schemes built from a scheme site.
- Construct Spec and Proj; prove Spec is a reflective embedding of CRing^op into scheme sites and check the sheaf condition for the structure sheaf on Spec R.
- Define locally ringed locales and construct a fully faithful functor and equivalence of morphisms between scheme sites and locally ringed locales; prove every scheme can be reconstructed from a scheme site.
- Refactor locales and sites, including dense sites and preorder sites; prove the category of ringed locales is univalent.
- Cauchy product of power series; make exp and log(1+x) into functions with full analytic properties; define sin and cos and prove cos π = -1.
- Make complex numbers a Banach algebra.
- Define Noetherian rings and prove products of Noetherian rings are Noetherian; define Bar-Noetherian rings, bar induction, and prove the fan theorem.
- Prove Hilbert's basis theorem for Bar-Noetherian rings.
- Define tensor products of modules and of algebras.
- Refactor Heyting algebras, complete lattices, and the localization of rings and monoids.
- Replace Equiv with QEquiv and IsEquiv throughout the library.
- Refactor the hierarchy of substructures and group representation theory.
- Add natarith/intarith tactics extending the linarith-style solver, including Nat div/mod support.
