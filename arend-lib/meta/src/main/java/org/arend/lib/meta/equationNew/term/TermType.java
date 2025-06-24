package org.arend.lib.meta.equationNew.term;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public sealed interface TermType permits TermType.OpType, TermType.NatType {
  record NatType() implements TermType {}
  record OpType(@Nullable List<TermOperation> operations) implements TermType {}
}