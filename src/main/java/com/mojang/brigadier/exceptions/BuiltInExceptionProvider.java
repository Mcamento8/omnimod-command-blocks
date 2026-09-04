package com.mojang.brigadier.exceptions;

/**
 * [Agent Note 2026-08-02] GENERAL: Brigadier built-in exception type provider shim.
 *
 * Mirrors {@code com.mojang.brigadier.exceptions.BuiltInExceptionProvider} from
 * Forge 1.20.1. It is a factory interface for exception builders used by
 * Brigadier's argument types. OmniMod only needs the type to exist for
 * bytecode-verification compatibility; no real parsing occurs.
 *
 * GENERAL — part of the standard Brigadier API surface, no mod hardcode.
 *
 * Doc-ID: BRIG-EXC-PROV-001
 */
public interface BuiltInExceptionProvider {
}
