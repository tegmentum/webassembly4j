package ai.tegmentum.webassembly4j.provider.graalwasm;

import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.ValueType;
import ai.tegmentum.webassembly4j.api.exception.ExecutionException;
import ai.tegmentum.webassembly4j.api.exception.TrapException;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

final class GraalWasmFunctionAdapter implements Function {

    private final Value nativeFunction;

    GraalWasmFunctionAdapter(Value nativeFunction) {
        this.nativeFunction = nativeFunction;
    }

    @Override
    public ValueType[] parameterTypes() {
        // GraalVM Polyglot API doesn't expose WASM function type metadata directly
        return new ValueType[0];
    }

    @Override
    public ValueType[] resultTypes() {
        return new ValueType[0];
    }

    @Override
    public Object invoke(Object... args) {
        try {
            Value result = nativeFunction.execute(args);
            if (result == null || result.isNull()) {
                return null;
            }
            if (result.fitsInInt()) {
                return result.asInt();
            }
            if (result.fitsInLong()) {
                return result.asLong();
            }
            if (result.fitsInFloat()) {
                return result.asFloat();
            }
            if (result.fitsInDouble()) {
                return result.asDouble();
            }
            return null;
        } catch (PolyglotException e) {
            if (e.isGuestException()) {
                throw new TrapException(e.getMessage(), e);
            }
            throw new ExecutionException(e.getMessage(), e);
        } catch (Exception e) {
            throw new ExecutionException(e.getMessage(), e);
        }
    }
}
