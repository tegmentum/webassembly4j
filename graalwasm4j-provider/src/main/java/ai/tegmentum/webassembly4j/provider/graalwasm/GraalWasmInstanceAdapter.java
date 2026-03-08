package ai.tegmentum.webassembly4j.provider.graalwasm;

import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Global;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.Memory;
import ai.tegmentum.webassembly4j.api.Table;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.util.Optional;

final class GraalWasmInstanceAdapter implements Instance {

    private final Context context;
    private final Value bindings;

    GraalWasmInstanceAdapter(Context context, Value bindings) {
        this.context = context;
        this.bindings = bindings;
    }

    @Override
    public Optional<Function> function(String name) {
        Value member = bindings.getMember(name);
        if (member == null || !member.canExecute()) {
            return Optional.empty();
        }
        return Optional.of(new GraalWasmFunctionAdapter(member));
    }

    @Override
    public Optional<Memory> memory(String name) {
        Value member = bindings.getMember(name);
        if (member == null || !member.hasBufferElements()) {
            return Optional.empty();
        }
        return Optional.of(new GraalWasmMemoryAdapter(member));
    }

    @Override
    public Optional<Table> table(String name) {
        Value member = bindings.getMember(name);
        if (member == null || !member.hasArrayElements()) {
            return Optional.empty();
        }
        return Optional.of(new GraalWasmTableAdapter(member));
    }

    @Override
    public Optional<Global> global(String name) {
        Value member = bindings.getMember(name);
        if (member == null || member.canExecute() || member.hasBufferElements()
                || member.hasArrayElements()) {
            return Optional.empty();
        }
        if (member.isNull()) {
            return Optional.empty();
        }
        return Optional.of(new GraalWasmGlobalAdapter(member));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        if (nativeType.isInstance(context)) {
            return Optional.of((T) context);
        }
        if (nativeType.isInstance(bindings)) {
            return Optional.of((T) bindings);
        }
        return Optional.empty();
    }
}
