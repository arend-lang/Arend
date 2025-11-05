package org.arend.typechecking;

import org.arend.core.context.binding.Binding;
import org.arend.ext.ArendExtension;
import org.arend.extImpl.userData.UserDataHolderImpl;
import org.arend.naming.reference.Referable;
import org.arend.server.ArendServerResolveListener;
import org.arend.term.prettyprint.LocalExpressionPrettifier;
import org.arend.typechecking.instance.pool.GlobalInstancePool;

import java.util.Map;

public record TypecheckingContext(Map<Referable, Binding> localContext, LocalExpressionPrettifier localPrettifier,
                                  GlobalInstancePool instancePool, ArendExtension arendExtension,
                                  ArendServerResolveListener resolveListener, UserDataHolderImpl userDataHolder,
                                  LevelContext levelContext) {}
