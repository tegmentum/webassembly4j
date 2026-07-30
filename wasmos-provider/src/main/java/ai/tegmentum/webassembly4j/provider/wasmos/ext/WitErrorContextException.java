/*
 * Copyright 2026 Tegmentum AI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package ai.tegmentum.webassembly4j.provider.wasmos.ext;

import ai.tegmentum.webassembly4j.api.exception.ExecutionException;

/**
 * Java-side exception wrapper for a wasmos-provider WIT
 * {@code error-context} handle. Carries the opaque handle (as an
 * {@link Object} — the underlying carrier type is package-private in
 * {@code wasmos-provider}) so consumers can inspect it via the
 * exceptional {@link java.util.concurrent.CompletableFuture} chain.
 *
 * <p>Wasmtime 47's {@code ErrorContextAny} is a placeholder type (see
 * wasmtime's {@code FIXME(#11161)}) — the only publicly-accessible
 * information is a numeric rep. That's carried on the wrapped
 * {@code WitErrorContext}. Downstream code that wants to introspect can
 * do so via the pass-in path (route the handle back into a guest import
 * that accepts an error-context argument) or via
 * {@link WasmosAsyncExtension#closeErrorContext} for disposal.
 *
 * <p>Extends {@link ExecutionException} so
 * {@code CompletableFuture<Object>.get()} rethrows it wrapped in
 * {@link java.util.concurrent.ExecutionException} with a consistent
 * exception-hierarchy story shared with other invoke-time failures.
 * Callers doing {@code .handle((v, t) -> …)} can pattern-match on
 * {@code WitErrorContextException} to detect the specific case and
 * pull the underlying handle out via {@link #errorContext()}.
 *
 * @since 2.5.2
 */
public final class WitErrorContextException extends ExecutionException {

    private static final long serialVersionUID = 1L;

    /**
     * The opaque {@code WitErrorContext} carrier. Passed through as
     * {@link Object} because the concrete
     * {@code WasmosMarshalling.WitErrorContext} type is package-private
     * on purpose (the wasmos-provider marshalling layer keeps its
     * carrier types out of the public API surface). Callers who
     * absolutely need the numeric rep can call {@code toString()} — the
     * carrier's toString includes {@code rep=<N>} — or route the
     * handle back into a guest via
     * {@link WasmosAsyncExtension#invokeAsync}.
     */
    private final Object errorContext;

    public WitErrorContextException(String message, Object errorContext) {
        super(message);
        this.errorContext = errorContext;
    }

    public WitErrorContextException(String message, Object errorContext, Throwable cause) {
        super(message, cause);
        this.errorContext = errorContext;
    }

    /**
     * The wrapped {@code WitErrorContext} — an opaque
     * {@code WasmosMarshalling.WitErrorContext} carrier. Passed as
     * {@link Object} to avoid leaking the package-private carrier type;
     * downstream code that wants to introspect can call {@code toString()}
     * on it (which includes the numeric rep) or pass it back into a guest
     * import via {@link WasmosAsyncExtension#invokeAsync}.
     */
    public Object errorContext() {
        return errorContext;
    }
}
