### Program Arguments Supported by `ConsoleMain`

The Arend console app uses Apache Commons CLI for argument parsing. Below is the complete list of supported arguments:

---

#### Named Options

| Short    | Long                            | Has Arg | Description                                                                          |
|----------|---------------------------------|---------|--------------------------------------------------------------------------------------|
| `-h`     | `--help`                        | No | Print help message and exit                                                          |
| `-v`     | `--version`                     | No | Print language version and exit                                                      |
| `-L`     | `--libdir`                      | Yes (`dir`) | Directory containing libraries (can be specified multiple times)                     |
| `-s`     | `--sources`                     | Yes (`dir`) | Project source directory                                                             |
| `-e`     | `--extensions`                  | Yes (`dir`) | Language extensions directory                                                        |
| `-m`     | `--extension-main`              | Yes (`class`) | Main extension class name                                                            |
| `-c`     | `--double-check`                | No | Double-check correctness of the typechecking result                                  |
| `-i`     | `--interactive`                 | Optional (`type`) | Start an interactive REPL; `type` can be `plain` or `jline` (default is `jline`)     |
| `-p`     | `--print`                       | Yes (`target`) | Print a definition or a module; format: `Module.Path` or `Module.Path:DefinitionName` |
| `-ps`    | `--proof-search`                | Yes (`pattern`) | Run proof searcher                                                                   |
| `-r`     | `--recompile`                   | No | Recompile all modules from source, ignoring binary caches (`.arc` files)             |
| *(none)* | `--serialize`                   | No | After typechecking, persist typechecked modules as `.arc` binary caches              |
| `-t`     | `--test`                        | No | Run tests                                                                            |
| `-d`     | `--daemon`                      | No | Start a daemon for the library and leave it serving future calls                     |
| *(none)* | `--daemon-stop`                 | No | Stop the daemon serving the library                                                  |
| *(none)* | `--daemon-ping`                 | No | Report whether the daemon is IDLE or BUSY                                            |
| *(none)* | `--daemon-status`               | No | Dump the daemon's queue depth, uptime, current task, protocol version, locked flags  |
| *(none)* | `--daemon-refresh`              | No | Re-run the bootstrap pipeline on the daemon's warm context to pick up source edits   |
| *(none)* | `--no-daemon`                   | No | Run in this process even if a daemon serves the library                              |
| *(none)* | `--show-times`                  | No | Show typechecking times per definition                                               |
| *(none)* | `--show-sizes`                  | No | Show sizes (expression node counts) of typechecked definitions, sorted descending    |
| *(none)* | `--show-modules`                | No | Show module dependency cycles                                                        |
| *(none)* | `--show-modules-with-instances` | No | Show module dependency cycles, filtered to modules that contain instances            |

---

#### Positional Arguments (FILES)

Usage: `arend [FILES]`

Each positional argument is interpreted as one of the following:
- **A directory path** — treated as a library root (looks for `arend.yaml` inside)
- **A path ending in `arend.yaml`** — loaded directly as a library config file
- **A `.zip` file** — loaded as a zipped library
- **A library name** (without path) — searched in the library directories specified by `-L` (or the default libraries root)
- **A module path** (e.g., `Category.Functor`) — a specific module to typecheck within the loaded libraries
- **A path to a module and a definition within it, separated by colon, e.g. `Category.Functor:FullyFaithfulFunctor.inverse` - a specific definition to typecheck within the loaded libraries**

---

#### Notes
- If `-L` is not specified, the default libraries root directory is used automatically.
- If no files/modules are specified and no `-s` is given, the tool looks for `arend.yaml` in the current directory.
- `-i` (`--interactive`) launches a REPL and skips typechecking entirely.
- `-h` and `-v` cause the program to exit immediately after printing output.
- Persistence of typechecked definitions to `.arc` files (in the library's `binariesDir` from `arend.yaml`) is **opt-in**: pass `--serialize` to enable it. Without the flag, typechecking runs but no `.arc` files are written.
- Deserialization of existing `.arc` files is **on by default**: on subsequent runs, any `.arc` caches already present are loaded automatically (regardless of whether `--serialize` is set). Pass `-r` / `--recompile` to ignore them and re-typecheck from source.

## The daemon

A daemon is a long-lived JVM holding a warm `ArendServer` for one library, so that a
second `arend` call over the same sources does not repeat the load, the resolve and the
typecheck it already did.

- `arend LIBRARY -d` starts one: the normal load + typecheck + persist once, then it idles.
- After that an ordinary `arend LIBRARY ...` is **routed to the daemon automatically**; the
  output and exit code are the same as running in this process, and a relative path in the
  command means the directory you typed it in, not the daemon's. `--no-daemon` opts out.
- Two commands are never routed, because a daemon cannot serve them: `-i` (the REPL is
  interactive) and `-s <dir>` (it names the sources to check, which a served command would
  ignore and then typecheck the daemon's own library instead). Both run in this process.
- `--daemon-ping`, `--daemon-status`, `--daemon-refresh` and `--daemon-stop` control it.
  All of them take the same single positional library reference, defaulting to `./arend.yaml`.
- Per-daemon state lives in `<library>/.arend/`: `daemon.lock`, `daemon.sock`, `daemon.log`
  (and `daemon.starting` while a start is in flight, which is what stops two `-d` calls from
  both spawning). The directory and socket are created private to the owner, which is the
  whole of the access control — anything that can reach the socket can run commands against
  the library. Where the library's own path is too long for a socket the daemon uses a short
  private directory instead and says so; `daemon.lock` records whichever address was bound.
- Ctrl-C on a routed command cancels the work on the daemon and exits 130, printing
  `[CANCELLED]`. A cancelled run writes no `.arc` caches: the modules it never reached were
  not checked, and caching that would leave the gap on disk for later runs to load as if
  they were.

The flags that shape the server itself are fixed when the daemon starts, so a daemon-served
command cannot change them: `-L`, `-s`, `-e`, `-m`, `-c`, `-r`, `--serialize`.
Passing one is warned about and ignored; use `--no-daemon` to get a process
that honours it. `-i` and the `--daemon-*` flags are rejected outright inside a served command.
