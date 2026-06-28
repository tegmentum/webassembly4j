package ai.tegmentum.webassembly4j.provider.wasm3;

import ai.tegmentum.wasm34j.WebAssemblyFunction;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.ValueType;
import ai.tegmentum.webassembly4j.api.exception.ExecutionException;

/** {@link Function} backed by a wasm34j {@link WebAssemblyFunction}. */
final class Wasm3FunctionAdapter implements Function {

    private final WebAssemblyFunction nativeFunction;

    Wasm3FunctionAdapter(final WebAssemblyFunction nativeFunction) {
        this.nativeFunction = nativeFunction;
    }

    @Override
    public ValueType[] parameterTypes() {
        final ValueType[] types = new ValueType[nativeFunction.parameterCount()];
        for (int i = 0; i < types.length; i++) {
            types[i] = Wasm3Types.toApi(nativeFunction.parameterType(i));
        }
        return types;
    }

    @Override
    public ValueType[] resultTypes() {
        final ValueType[] types = new ValueType[nativeFunction.resultCount()];
        for (int i = 0; i < types.length; i++) {
            types[i] = Wasm3Types.toApi(nativeFunction.resultType(i));
        }
        return types;
    }

    @Override
    public Object invoke(final Object... args) {
        try {
            return nativeFunction.invoke(args);
        } catch (final ai.tegmentum.wasm34j.exception.WasmException e) {
            throw new ExecutionException(e.getMessage(), e);
        }
    }
}
