package org.arend.frontend.cli.ai;

import org.arend.ext.module.LongName;
import org.arend.ext.module.ModulePath;
import org.arend.ext.util.Pair;

import java.util.Set;

/**
 * Granularity of an {@code -ai} invocation, derived from the positional argument:
 * <ul>
 *   <li>{@link #LIBRARY}: no module argument; the whole library is in scope.</li>
 *   <li>{@link #MODULE}: a {@code Module.Path} argument; one module is in scope.</li>
 *   <li>{@link #DEFINITION}: a {@code Module.Path:Def.Long.Name} argument; one
 *       definition is in scope.</li>
 * </ul>
 *
 * <p>The router uses this together with the {@link #targetModule} / {@link #targetDefinition}
 * fields to decide whether a given diagnostic gets a full body on stdout or only
 * a count toward the closing summary.
 */
public final class Granularity {
  public enum Kind { LIBRARY, MODULE, DEFINITION }

  public final Kind kind;
  public final ModulePath targetModule;
  public final LongName targetDefinition;

  private Granularity(Kind kind, ModulePath targetModule, LongName targetDefinition) {
    this.kind = kind;
    this.targetModule = targetModule;
    this.targetDefinition = targetDefinition;
  }

  public static Granularity library() {
    return new Granularity(Kind.LIBRARY, null, null);
  }

  public static Granularity module(ModulePath modulePath) {
    return new Granularity(Kind.MODULE, modulePath, null);
  }

  public static Granularity definition(ModulePath modulePath, LongName longName) {
    return new Granularity(Kind.DEFINITION, modulePath, longName);
  }

  /**
   * Derive granularity from {@code CommandContext.requestedModules}. A multi-module
   * argument list collapses to {@link Kind#LIBRARY} — the router can only filter by
   * a single target, so the wide net is the safe interpretation.
   */
  public static Granularity from(Set<Pair<ModulePath, LongName>> requestedModules) {
    if (requestedModules.isEmpty()) return library();
    if (requestedModules.size() > 1) return library();
    Pair<ModulePath, LongName> only = requestedModules.iterator().next();
    return only.proj2 == null ? module(only.proj1) : definition(only.proj1, only.proj2);
  }
}
