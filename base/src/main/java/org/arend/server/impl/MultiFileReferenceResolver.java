package org.arend.server.impl;

import org.arend.error.DummyErrorReporter;
import org.arend.ext.module.ModuleLocation;
import org.arend.server.ArendServer;
import org.arend.server.modifier.RawModifier;
import org.arend.server.modifier.RawSequenceModifier;

import java.util.HashMap;

public class MultiFileReferenceResolver {
    public HashMap<ModuleLocation, SingleFileReferenceResolver> multiResolverMap = new HashMap<>();
    protected ArendServer myServer;

    public MultiFileReferenceResolver(ArendServer server) {
        myServer = server;
    }

    public SingleFileReferenceResolver getFileResolver(ModuleLocation anchorLocation) {
        return multiResolverMap.computeIfAbsent(anchorLocation, k -> new SingleFileReferenceResolver(myServer, DummyErrorReporter.INSTANCE, k));
    }

    public void addSingleResolver(SingleFileReferenceResolver resolver) {
        multiResolverMap.put(resolver.getModuleLocation(), resolver);
    }
}
