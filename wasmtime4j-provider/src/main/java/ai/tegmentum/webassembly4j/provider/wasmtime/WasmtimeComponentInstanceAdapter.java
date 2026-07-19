package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.wasmtime4j.wit.WitBool;
import ai.tegmentum.wasmtime4j.wit.WitBorrow;
import ai.tegmentum.wasmtime4j.wit.WitChar;
import ai.tegmentum.wasmtime4j.wit.WitFloat32;
import ai.tegmentum.wasmtime4j.wit.WitFloat64;
import ai.tegmentum.wasmtime4j.wit.WitList;
import ai.tegmentum.wasmtime4j.wit.WitOwn;
import ai.tegmentum.wasmtime4j.wit.WitResource;
import ai.tegmentum.wasmtime4j.wit.WitS16;
import ai.tegmentum.wasmtime4j.wit.WitS32;
import ai.tegmentum.wasmtime4j.wit.WitS64;
import ai.tegmentum.wasmtime4j.wit.WitS8;
import ai.tegmentum.wasmtime4j.wit.WitString;
import ai.tegmentum.wasmtime4j.wit.WitU8;
import ai.tegmentum.wasmtime4j.wit.WitValue;
import ai.tegmentum.webassembly4j.api.ComponentInstance;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Global;
import ai.tegmentum.webassembly4j.api.Memory;
import ai.tegmentum.webassembly4j.api.Table;
import ai.tegmentum.webassembly4j.api.WitCallableResource;
import ai.tegmentum.webassembly4j.api.exception.ExecutionException;
import ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class WasmtimeComponentInstanceAdapter implements ComponentInstance {

    private final ai.tegmentum.wasmtime4j.component.ComponentInstance nativeInstance;

    WasmtimeComponentInstanceAdapter(
            ai.tegmentum.wasmtime4j.component.ComponentInstance nativeInstance) {
        this.nativeInstance = nativeInstance;
    }

    @Override
    public Object invoke(String functionName, Object... args) {
        final Object[] witArgs = marshalArgs(functionName, args);
        try {
            return nativeInstance.invoke(functionName, witArgs);
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            throw new ExecutionException(
                    "Failed to invoke component function: " + functionName, e);
        }
    }

    @Override
    public Object invokeWit(String functionName, Object... args) {
        final Object[] witArgs = marshalArgs(functionName, args);
        try {
            return nativeInstance.invokeWit(functionName, witArgs);
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            throw new ExecutionException(
                    "Failed to invoke component function: " + functionName, e);
        }
    }

    private static Object[] marshalArgs(String functionName, Object[] args) {
        final Object[] witArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            try {
                witArgs[i] = toWitValue(args[i]);
            } catch (UnsupportedFeatureException e) {
                throw new UnsupportedFeatureException(
                        "Argument " + i + " of '" + functionName + "': " + e.getMessage(), e);
            } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
                throw new ExecutionException(
                        "Argument " + i + " of '" + functionName + "' is not a valid WIT value", e);
            }
        }
        return witArgs;
    }

    /**
     * Marshal a natural Java argument into a {@link WitValue}, fulfilling the
     * {@link ComponentInstance#invoke} contract. Values already of type {@code WitValue} pass
     * through unchanged — the escape hatch for WIT types whose target type can't be inferred from a
     * bare Java value (option/result/record/variant/enum/flags, and exact integer widths beyond the
     * defaults below).
     *
     * <p>Mapping: Boolean→bool, Byte→s8, Short→s16, Integer→s32, Long→s64, Float→f32, Double→f64,
     * Character→char, String→string, {@code byte[]}→list&lt;u8&gt;, non-empty homogeneous List→list
     * (elements marshalled recursively). Integer/Long default to the signed widths; pass an explicit
     * {@code WitValue} (e.g. {@code WitU64.of(..)}) for unsigned or narrower-than-natural widths.
     */
    static WitValue toWitValue(Object arg)
            throws ai.tegmentum.wasmtime4j.exception.WasmException {
        if (arg instanceof WitValue) {
            return (WitValue) arg;
        }
        if (arg instanceof Boolean) {
            return WitBool.of((Boolean) arg);
        }
        if (arg instanceof Byte) {
            return WitS8.of((Byte) arg);
        }
        if (arg instanceof Short) {
            return WitS16.of((Short) arg);
        }
        if (arg instanceof Integer) {
            return WitS32.of((Integer) arg);
        }
        if (arg instanceof Long) {
            return WitS64.of((Long) arg);
        }
        if (arg instanceof Float) {
            return WitFloat32.of((Float) arg);
        }
        if (arg instanceof Double) {
            return WitFloat64.of((Double) arg);
        }
        if (arg instanceof Character) {
            return WitChar.of((Character) arg);
        }
        if (arg instanceof String) {
            return WitString.of((String) arg);
        }
        if (arg instanceof byte[]) {
            byte[] bytes = (byte[]) arg;
            List<WitValue> elems = new ArrayList<>(bytes.length);
            for (byte b : bytes) {
                elems.add(WitU8.of(b));
            }
            return WitList.of(elems);
        }
        if (arg instanceof List) {
            List<?> list = (List<?>) arg;
            if (list.isEmpty()) {
                throw new UnsupportedFeatureException(
                        "cannot infer element type of an empty list; pass a typed "
                        + "WitList.empty(elementType)");
            }
            List<WitValue> elems = new ArrayList<>(list.size());
            for (Object e : list) {
                elems.add(toWitValue(e));
            }
            return WitList.of(elems);
        }
        throw new UnsupportedFeatureException(
                "cannot marshal Java type "
                + (arg == null ? "null" : arg.getClass().getName())
                + " to a WIT value; build a WitValue explicitly "
                + "(option/result/record/variant/enum/flags and null require an explicit type)");
    }

    @Override
    public WitCallableResource asCallableResource(Object resource) {
        // The wasmtime WIT deserializer returns owned/borrowed resource handles as WitOwn /
        // WitBorrow (discriminators 22/23), NOT WitResource — the latter is a higher-level
        // convenience wrapper. The invokeResourceMethodWit / dropResource entry points on
        // wasmtime4j's ComponentInstance want WitResource though, so translate here rather
        // than making every caller do it (and rather than expanding WitCallableResource's
        // input contract, which is intentionally provider-neutral).
        final WitResource wr;
        if (resource instanceof WitResource) {
            wr = (WitResource) resource;
        } else if (resource instanceof WitOwn) {
            wr = WitResource.fromHandle(((WitOwn) resource).getHandle());
        } else if (resource instanceof WitBorrow) {
            wr = WitResource.fromHandle(((WitBorrow) resource).getHandle());
        } else {
            throw new IllegalArgumentException(
                    "Wasmtime provider expects a wasmtime4j WitResource / WitOwn / WitBorrow;"
                            + " got "
                            + (resource == null ? "null" : resource.getClass().getName()));
        }
        return new WasmtimeCallableResource(wr, nativeInstance);
    }

    @Override
    public boolean hasFunction(String name) {
        return nativeInstance.hasFunction(name);
    }

    @Override
    public List<String> exportedFunctions() {
        Set<String> functions = nativeInstance.getExportedFunctions();
        return Collections.unmodifiableList(new ArrayList<>(functions));
    }

    @Override
    public List<String> exportedInterfaces() {
        // ComponentInstance doesn't directly expose interface names;
        // delegate to the parent component
        ai.tegmentum.wasmtime4j.component.Component component = nativeInstance.getComponent();
        try {
            Set<String> interfaces = component.getExportedInterfaces();
            return Collections.unmodifiableList(new ArrayList<>(interfaces));
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public boolean exportsInterface(String name) {
        ai.tegmentum.wasmtime4j.component.Component component = nativeInstance.getComponent();
        try {
            return component.exportsInterface(name);
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            return false;
        }
    }

    @Override
    public Optional<Function> function(String name) {
        // Component instances don't expose core module functions directly
        return Optional.empty();
    }

    @Override
    public Optional<Memory> memory(String name) {
        return Optional.empty();
    }

    @Override
    public Optional<Table> table(String name) {
        return Optional.empty();
    }

    @Override
    public Optional<Global> global(String name) {
        return Optional.empty();
    }

    @Override
    public void consumeFuel(long amount) {
        if (amount < 0L) {
            throw new IllegalArgumentException("amount must be non-negative, got " + amount);
        }
        try {
            nativeInstance.consumeFuel(amount);
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            throw new ExecutionException("Failed to consume fuel: " + e.getMessage(), e);
        }
    }

    @Override
    public long fuelConsumed() {
        try {
            return nativeInstance.fuelConsumed();
        } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
            throw new ExecutionException("Failed to read fuel consumed: " + e.getMessage(), e);
        }
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
