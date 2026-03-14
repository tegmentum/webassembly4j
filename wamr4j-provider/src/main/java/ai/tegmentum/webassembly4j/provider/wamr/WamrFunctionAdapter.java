package ai.tegmentum.webassembly4j.provider.wamr;

import ai.tegmentum.wamr4j.FunctionSignature;
import ai.tegmentum.wamr4j.WebAssemblyFunction;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.ValueType;
import ai.tegmentum.webassembly4j.api.exception.ExecutionException;

final class WamrFunctionAdapter implements Function {

    private final WebAssemblyFunction nativeFunction;

    WamrFunctionAdapter(WebAssemblyFunction nativeFunction) {
        this.nativeFunction = nativeFunction;
    }

    @Override
    public ValueType[] parameterTypes() {
        FunctionSignature sig = nativeFunction.getSignature();
        return convertTypes(sig.getParameterTypes());
    }

    @Override
    public ValueType[] resultTypes() {
        FunctionSignature sig = nativeFunction.getSignature();
        return convertTypes(sig.getReturnTypes());
    }

    @Override
    public Object invoke(Object... args) {
        try {
            return nativeFunction.invoke(args);
        } catch (ai.tegmentum.wamr4j.exception.WasmRuntimeException e) {
            throw new ExecutionException(e.getMessage(), e);
        }
    }

    @Override
    public Object[] invokeMultiple(final Object[]... argSets) {
        if (argSets == null) {
            throw new IllegalArgumentException("Argument sets array cannot be null");
        }
        try {
            return nativeFunction.invokeMultiple(argSets);
        } catch (ai.tegmentum.wamr4j.exception.WasmRuntimeException e) {
            throw new ExecutionException(e.getMessage(), e);
        }
    }

    private static ValueType[] convertTypes(ai.tegmentum.wamr4j.ValueType[] nativeTypes) {
        ValueType[] types = new ValueType[nativeTypes.length];
        for (int i = 0; i < nativeTypes.length; i++) {
            types[i] = convertType(nativeTypes[i]);
        }
        return types;
    }

    private static ValueType convertType(ai.tegmentum.wamr4j.ValueType nativeType) {
        if (nativeType == ai.tegmentum.wamr4j.ValueType.I32) return ValueType.I32;
        if (nativeType == ai.tegmentum.wamr4j.ValueType.I64) return ValueType.I64;
        if (nativeType == ai.tegmentum.wamr4j.ValueType.F32) return ValueType.F32;
        if (nativeType == ai.tegmentum.wamr4j.ValueType.F64) return ValueType.F64;
        throw new IllegalArgumentException("Unknown type: " + nativeType);
    }
}
