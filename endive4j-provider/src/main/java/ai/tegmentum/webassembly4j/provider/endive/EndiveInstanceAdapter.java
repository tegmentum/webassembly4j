package ai.tegmentum.webassembly4j.provider.endive;

import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Global;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.Memory;
import ai.tegmentum.webassembly4j.api.Table;
import ai.tegmentum.webassembly4j.api.gc.GcExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

final class EndiveInstanceAdapter implements Instance {

    private final run.endive.runtime.Instance nativeInstance;
    private final Map<String, Optional<Function>> functionCache = new HashMap<>();
    private final Map<String, Optional<Memory>> memoryCache = new HashMap<>();

    EndiveInstanceAdapter(run.endive.runtime.Instance nativeInstance) {
        this.nativeInstance = nativeInstance;
    }

    @Override
    public Optional<Function> function(String name) {
        Optional<Function> cached = functionCache.get(name);
        if (cached != null) {
            return cached;
        }
        Optional<Function> result;
        try {
            run.endive.runtime.ExportFunction exportFunc =
                    nativeInstance.exports().function(name);
            run.endive.wasm.types.FunctionType funcType =
                    nativeInstance.exportType(name);
            result = Optional.of(new EndiveFunctionAdapter(exportFunc, funcType));
        } catch (Exception e) {
            result = Optional.empty();
        }
        functionCache.put(name, result);
        return result;
    }

    @Override
    public Optional<Memory> memory(String name) {
        Optional<Memory> cached = memoryCache.get(name);
        if (cached != null) {
            return cached;
        }
        Optional<Memory> result;
        try {
            run.endive.runtime.Memory nativeMemory =
                    nativeInstance.exports().memory(name);
            result = Optional.of(new EndiveMemoryAdapter(nativeMemory));
        } catch (Exception e) {
            result = Optional.empty();
        }
        memoryCache.put(name, result);
        return result;
    }

    @Override
    public Optional<Table> table(String name) {
        try {
            run.endive.runtime.TableInstance tableInstance =
                    nativeInstance.exports().table(name);
            return Optional.of(new EndiveTableAdapter(tableInstance));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Global> global(String name) {
        try {
            run.endive.runtime.GlobalInstance globalInstance =
                    nativeInstance.exports().global(name);
            return Optional.of(new EndiveGlobalAdapter(globalInstance));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> extension(Class<T> extensionType) {
        if (extensionType == GcExtension.class) {
            return Optional.of((T) new EndiveGcExtension());
        }
        return Optional.empty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        if (nativeType.isInstance(nativeInstance)) {
            return Optional.of((T) nativeInstance);
        }
        return Optional.empty();
    }
}
