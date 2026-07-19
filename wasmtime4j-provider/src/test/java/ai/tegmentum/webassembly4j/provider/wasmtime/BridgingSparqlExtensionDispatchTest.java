package ai.tegmentum.webassembly4j.provider.wasmtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.tegmentum.wasmtime4j.wit.WitList;
import ai.tegmentum.wasmtime4j.wit.WitString;
import ai.tegmentum.wasmtime4j.wit.WitU64;
import ai.tegmentum.wasmtime4j.wit.WitValue;
import ai.tegmentum.webassembly4j.api.Component;
import ai.tegmentum.webassembly4j.api.ComponentInstance;
import ai.tegmentum.webassembly4j.api.DefaultLinkingContext;
import ai.tegmentum.webassembly4j.api.DefaultWasiContext;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Global;
import ai.tegmentum.webassembly4j.api.Memory;
import ai.tegmentum.webassembly4j.api.Table;
import ai.tegmentum.webassembly4j.api.WitCallableResource;
import ai.tegmentum.webassembly4j.api.exception.ExecutionException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Round-trip verification for {@link BridgingSparqlExtensionDispatch}. Constructs two fake
 * {@link ComponentInstance} implementations — one advertising the new sparql-extension exports,
 * one advertising only the old flat exports — and asserts the same dispatch surface routes both
 * to the ABI-appropriate underlying call with identical caller-visible results.
 *
 * <p>Rationale for fakes over real components: the bridging is a pure dispatch selector on top
 * of {@link ComponentInstance#invokeWit(String, Object...)} + {@link
 * ComponentInstance#hasFunction(String)}. Real component fixtures would additionally test the
 * wasmtime4j FFI, which is out of scope here and already covered by
 * {@link WasmtimeCallableResourceTest}. The fake records every dispatched call so we can assert
 * the exact export path the bridging chose per ABI shape — the fixture approach would only be
 * able to assert the observable result, not the path taken to produce it.
 */
class BridgingSparqlExtensionDispatchTest {

    @Test
    @DisplayName("callFilter — new-shape guest dispatches through extension@0.1.0#call")
    void newShapeFilterCall() throws Exception {
        final RecordingComponentInstance instance = RecordingComponentInstance.newShape();
        final BridgingSparqlExtensionDispatch dispatch =
                new BridgingSparqlExtensionDispatch(instance);

        assertTrue(dispatch.filterIsNewShape(),
                "detection at construction should recognize the new-shape filter export");

        final WitValue result =
                dispatch.callFilter("upper", args(WitString.of("hello")));

        assertEquals(1, instance.calls.size(),
                "exactly one underlying invokeWit call per callFilter");
        assertEquals(
                BridgingSparqlExtensionDispatch.NEW_SHAPE_FILTER_EXPORT,
                instance.calls.get(0).exportName,
                "new-shape callFilter must route through the extension@0.1.0#call path");
        // Args to the new-shape ABI: [name: string, args: list<term>]
        assertEquals(2, instance.calls.get(0).args.length);
        assertTrue(instance.calls.get(0).args[0] instanceof WitString);
        assertTrue(instance.calls.get(0).args[1] instanceof WitList);
        assertEquals("HELLO", ((WitString) result).getValue(),
                "fake returns the uppercased argument to prove the caller can consume the result");
    }

    @Test
    @DisplayName("callFilter — old-shape guest falls back to bare evaluate export")
    void oldShapeFilterCall() throws Exception {
        final RecordingComponentInstance instance = RecordingComponentInstance.oldShape();
        final BridgingSparqlExtensionDispatch dispatch =
                new BridgingSparqlExtensionDispatch(instance);

        assertFalse(dispatch.filterIsNewShape(),
                "old-shape guest should not detect as new-shape");

        final WitValue result =
                dispatch.callFilter("upper", args(WitString.of("hello")));

        assertEquals(1, instance.calls.size());
        assertEquals(
                BridgingSparqlExtensionDispatch.OLD_SHAPE_FILTER_EXPORT,
                instance.calls.get(0).exportName,
                "old-shape callFilter must fall back to bare evaluate");
        // Args to the old-shape ABI: [args: list<value>]. Function name is discarded.
        assertEquals(1, instance.calls.get(0).args.length);
        assertTrue(instance.calls.get(0).args[0] instanceof WitList);
        assertEquals("HELLO", ((WitString) result).getValue(),
                "both ABIs surface the same caller-visible result through the bridging");
    }

    @Test
    @DisplayName("callFilter — same helper interface, same result across both ABIs")
    void bothAbiSurfacesReturnIdenticalResults() throws Exception {
        final RecordingComponentInstance newShape = RecordingComponentInstance.newShape();
        final RecordingComponentInstance oldShape = RecordingComponentInstance.oldShape();

        final WitValue fromNew = new BridgingSparqlExtensionDispatch(newShape)
                .callFilter("upper", args(WitString.of("hello")));
        final WitValue fromOld = new BridgingSparqlExtensionDispatch(oldShape)
                .callFilter("upper", args(WitString.of("hello")));

        assertNotSame(fromNew, fromOld, "distinct instances produce distinct return values");
        assertEquals(((WitString) fromNew).getValue(), ((WitString) fromOld).getValue(),
                "the whole point of the bridging: caller sees identical results regardless "
                        + "of which ABI the guest speaks");
    }

    @Test
    @DisplayName("callFilter — empty args rejected with clear diagnostic")
    void emptyArgsRejected() {
        final BridgingSparqlExtensionDispatch dispatch =
                new BridgingSparqlExtensionDispatch(RecordingComponentInstance.newShape());
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> dispatch.callFilter("noop", Collections.<WitValue>emptyList()));
        assertTrue(ex.getMessage().contains("non-empty"),
                "diagnostic should point at the non-empty precondition; got: " + ex.getMessage());
    }

    @Test
    @DisplayName("newAggregate — new-shape guest returns the real WitCallableResource")
    void newShapeAggregate() {
        final RecordingComponentInstance instance = RecordingComponentInstance.newShape();
        final BridgingSparqlExtensionDispatch dispatch =
                new BridgingSparqlExtensionDispatch(instance);

        assertTrue(dispatch.aggregateIsNewShape());

        try (WitCallableResource agg = dispatch.newAggregate("sum")) {
            assertNotNull(agg);
            assertEquals(1, instance.calls.size(),
                    "new-aggregate ctor call should be the only invokeWit so far");
            assertEquals(
                    BridgingSparqlExtensionDispatch.NEW_SHAPE_AGGREGATE_CTOR,
                    instance.calls.get(0).exportName);
        }
    }

    @Test
    @DisplayName("newAggregate — old-shape guest returns a shim that routes step/finish "
            + "to bare aggregate-step/aggregate-finish")
    void oldShapeAggregateShim() {
        final RecordingComponentInstance instance = RecordingComponentInstance.oldShape();
        final BridgingSparqlExtensionDispatch dispatch =
                new BridgingSparqlExtensionDispatch(instance);

        assertFalse(dispatch.aggregateIsNewShape());

        try (WitCallableResource agg = dispatch.newAggregate("sum")) {
            // No underlying call yet — the shim is constructed in Java without touching the
            // component instance. This mirrors old-shape semantics where accumulator state
            // lives on the instance itself, not on a per-group resource.
            assertEquals(0, instance.calls.size(),
                    "shim construction should not touch the underlying instance");

            agg.invokeMethodWit("step", WitU64.of(42L));
            assertEquals(1, instance.calls.size());
            assertEquals(
                    BridgingSparqlExtensionDispatch.OLD_SHAPE_AGGREGATE_STEP,
                    instance.calls.get(0).exportName,
                    "shim.step must dispatch to bare aggregate-step");
            // Old-shape signature: aggregate-step(args: list<value>, mult: u64). Shim
            // supplies mult=1 as documented.
            assertEquals(2, instance.calls.get(0).args.length);
            assertTrue(instance.calls.get(0).args[1] instanceof WitU64,
                    "shim must append the multiplicity argument the old-shape signature requires");

            agg.invokeMethodWit("finish");
            assertEquals(2, instance.calls.size());
            assertEquals(
                    BridgingSparqlExtensionDispatch.OLD_SHAPE_AGGREGATE_FINISH,
                    instance.calls.get(1).exportName,
                    "shim.finish must dispatch to bare aggregate-finish");
        }
    }

    @Test
    @DisplayName("shim aggregate — close() is idempotent and further calls throw")
    void shimAggregateCloseSemantics() {
        final RecordingComponentInstance instance = RecordingComponentInstance.oldShape();
        final BridgingSparqlExtensionDispatch dispatch =
                new BridgingSparqlExtensionDispatch(instance);
        final WitCallableResource agg = dispatch.newAggregate("sum");

        assertFalse(agg.isClosed());
        agg.close();
        assertTrue(agg.isClosed());
        // Second close is a no-op; try-with-resources double-close must not blow up.
        agg.close();
        // Post-close invocation should be rejected — same contract as the real WitCallableResource.
        assertThrows(IllegalStateException.class,
                () -> agg.invokeMethodWit("step", WitU64.of(1L)));
    }

    @Test
    @DisplayName("bridge() — generic fallback picks the old-shape path when new-shape absent")
    void bridgeGenericFallback() {
        final RecordingComponentInstance instance = RecordingComponentInstance.oldShape();
        // Register an extra bare export the general bridge should find.
        instance.exports.add("cardinality_estimate");
        instance.returnValues.put("cardinality_estimate", WitU64.of(100L));

        final BridgingSparqlExtensionDispatch dispatch =
                new BridgingSparqlExtensionDispatch(instance);
        final WitValue result = dispatch.bridge(
                "stardog:webfunction/planner@0.3.0#cardinality-estimate",
                "cardinality_estimate");

        assertEquals("cardinality_estimate", instance.calls.get(0).exportName,
                "bridge() should fall back to the bare path when the interface-scoped one is absent");
        assertNotNull(result);
    }

    @Test
    @DisplayName("bridge() — throws when neither shape is exported")
    void bridgeNeitherShape() {
        final RecordingComponentInstance instance = RecordingComponentInstance.oldShape();
        final BridgingSparqlExtensionDispatch dispatch =
                new BridgingSparqlExtensionDispatch(instance);
        final ExecutionException ex = assertThrows(ExecutionException.class,
                () -> dispatch.bridge("no-such-new", "no-such-old"));
        assertTrue(ex.getMessage().contains("no-such-new")
                        && ex.getMessage().contains("no-such-old"),
                "diagnostic should name both attempted paths; got: " + ex.getMessage());
    }

    /**
     * Regression: {@link ComponentInstance#hasFunction(String)} must agree with the invocation
     * path on interface-qualified export names ({@code <package>:<interface>@<version>#<func>}).
     *
     * <p>Before the wasmtime4j fix this probe returned {@code false} for interface-scoped
     * exports even when {@code invokeWit} of the same qualified name succeeded — which broke
     * {@link BridgingSparqlExtensionDispatch#filterIsNewShape} at construction time (the
     * constructor asked {@code hasFunction("tegmentum:webfunction/extension@0.1.0#call")} and
     * always mis-detected new-shape guests as old-shape). Rather than reuse the webfunction
     * artifact this test drives the counter component fixture that already backs the callable
     * resource smoke test — it exports {@link #COUNTER_VALUE_METHOD} solely through the
     * {@code counter-api} interface, so a passing probe here proves the qualified-name lookup
     * on the underlying wasmtime4j {@code ComponentInstance} is fixed. A negative-control
     * check on a fake export name guards against the "always returns true" failure mode.
     */
    @Test
    @EnabledIf("runtimeAvailable")
    @DisplayName("regression: hasFunction agrees with invokeWit on interface-qualified names")
    void hasFunctionRecognizesInterfaceQualifiedExport() throws Exception {
        final byte[] wasm = loadCounterComponent();
        try (Engine engine = WasmtimeEngineAdapter.create(null);
             Component component = engine.loadComponent(wasm)) {
            final ComponentInstance instance = component.instantiate(wasiLinking());

            assertTrue(
                    instance.hasFunction(COUNTER_VALUE_METHOD),
                    "hasFunction must recognize the interface-qualified export "
                            + COUNTER_VALUE_METHOD
                            + " that the counter guest exports only via counter-api; if this "
                            + "regressed, BridgingSparqlExtensionDispatch#filterIsNewShape will "
                            + "silently fall back to old-shape dispatch for new-shape guests");
            assertTrue(
                    instance.hasFunction(COUNTER_CTOR_EXPORT),
                    "and it must also see the interface-scoped constructor export");
            assertFalse(
                    instance.hasFunction(
                            "tegmentum:test-counter/counter-api@0.1.0#[method]counter.does-not-exist"),
                    "sanity check: an unknown qualified name still returns false — otherwise the "
                            + "fix would be masking every probe with an unconditional true");
        }
    }

    // --- helpers -------------------------------------------------------------------------

    static boolean runtimeAvailable() {
        return new WasmtimeProvider().availability().available();
    }

    private static final String COUNTER_INTERFACE =
            "tegmentum:test-counter/counter-api@0.1.0";
    private static final String COUNTER_CTOR_EXPORT =
            COUNTER_INTERFACE + "#[constructor]counter";
    private static final String COUNTER_VALUE_METHOD =
            COUNTER_INTERFACE + "#[method]counter.value";

    private static DefaultLinkingContext wasiLinking() {
        return DefaultLinkingContext.builder()
                .wasiContext(DefaultWasiContext.builder().build())
                .build();
    }

    private static byte[] loadCounterComponent() throws IOException {
        try (InputStream is =
                BridgingSparqlExtensionDispatchTest.class.getResourceAsStream(
                        "/counter_component.wasm")) {
            assertNotNull(
                    is,
                    "counter_component.wasm not on classpath — the Maven build copies it into "
                            + "src/test/resources/ from src/test/rust/counter_component/");
            final ByteArrayOutputStream buf = new ByteArrayOutputStream();
            final byte[] chunk = new byte[8192];
            int n;
            while ((n = is.read(chunk)) != -1) {
                buf.write(chunk, 0, n);
            }
            return buf.toByteArray();
        }
    }

    private static List<WitValue> args(final WitValue... values) throws Exception {
        return Arrays.asList(values);
    }

    /**
     * Fake ComponentInstance that records every {@code invokeWit} call and returns a
     * canned per-export result. Two factory presets:
     *
     * <ul>
     *   <li>{@link #newShape()} — advertises the sparql-extension interface exports (filter +
     *       aggregate) and returns uppercased results plus a stub resource handle.
     *   <li>{@link #oldShape()} — advertises only bare {@code evaluate} / {@code aggregate-step} /
     *       {@code aggregate-finish} and returns the same uppercased result on evaluate.
     * </ul>
     */
    private static final class RecordingComponentInstance implements ComponentInstance {

        final Set<String> exports;
        final List<RecordedCall> calls = new ArrayList<>();
        final java.util.Map<String, Object> returnValues = new java.util.HashMap<>();

        private RecordingComponentInstance(final Set<String> exports) {
            this.exports = exports;
        }

        static RecordingComponentInstance newShape() {
            final Set<String> exports = new HashSet<>();
            exports.add(BridgingSparqlExtensionDispatch.NEW_SHAPE_FILTER_EXPORT);
            exports.add(BridgingSparqlExtensionDispatch.NEW_SHAPE_AGGREGATE_CTOR);
            final RecordingComponentInstance inst = new RecordingComponentInstance(exports);
            // "upper" filter behavior: return uppercased first arg.
            inst.uppercaseOn(BridgingSparqlExtensionDispatch.NEW_SHAPE_FILTER_EXPORT, /*argIdx*/ 1);
            // Aggregate ctor: return a stub resource-shaped Object the shim
            // asCallableResource can wrap. We return a marker string here because
            // asCallableResource is overridden in this fake to short-circuit.
            inst.returnValues.put(
                    BridgingSparqlExtensionDispatch.NEW_SHAPE_AGGREGATE_CTOR,
                    "STUB_RESOURCE");
            return inst;
        }

        static RecordingComponentInstance oldShape() {
            final Set<String> exports = new HashSet<>();
            exports.add(BridgingSparqlExtensionDispatch.OLD_SHAPE_FILTER_EXPORT);
            exports.add(BridgingSparqlExtensionDispatch.OLD_SHAPE_AGGREGATE_STEP);
            exports.add(BridgingSparqlExtensionDispatch.OLD_SHAPE_AGGREGATE_FINISH);
            final RecordingComponentInstance inst = new RecordingComponentInstance(exports);
            // Old-shape evaluate takes args as arg[0].
            inst.uppercaseOn(BridgingSparqlExtensionDispatch.OLD_SHAPE_FILTER_EXPORT, /*argIdx*/ 0);
            inst.returnValues.put(BridgingSparqlExtensionDispatch.OLD_SHAPE_AGGREGATE_STEP, null);
            inst.returnValues.put(BridgingSparqlExtensionDispatch.OLD_SHAPE_AGGREGATE_FINISH, null);
            return inst;
        }

        /**
         * Configure the fake so that calling {@code exportName} returns a WitString containing
         * the uppercased value of the first WitString found inside the {@code argIdx}-th arg
         * (unwrapping {@link WitList} as needed).
         */
        private void uppercaseOn(final String exportName, final int argIdx) {
            returnValues.put(exportName, new UppercaseBehavior(argIdx));
        }

        @Override
        public Object invoke(final String functionName, final Object... args) {
            return invokeWit(functionName, args);
        }

        @Override
        public Object invokeWit(final String functionName, final Object... args) {
            calls.add(new RecordedCall(functionName, args));
            final Object canned = returnValues.get(functionName);
            if (canned instanceof UppercaseBehavior) {
                return ((UppercaseBehavior) canned).apply(args);
            }
            return canned;
        }

        @Override
        public WitCallableResource asCallableResource(final Object resource) {
            // In the fake, we don't have real wasmtime resource machinery. Return a passive
            // resource handle for the new-shape aggregate ctor test.
            return new StubCallableResource((String) resource);
        }

        @Override
        public boolean hasFunction(final String name) {
            return exports.contains(name);
        }

        @Override
        public List<String> exportedFunctions() {
            return new ArrayList<>(exports);
        }

        @Override
        public List<String> exportedInterfaces() {
            return Collections.emptyList();
        }

        @Override
        public boolean exportsInterface(final String name) {
            return false;
        }

        @Override
        public Optional<Function> function(final String name) {
            return Optional.empty();
        }

        @Override
        public Optional<Memory> memory(final String name) {
            return Optional.empty();
        }

        @Override
        public Optional<Table> table(final String name) {
            return Optional.empty();
        }

        @Override
        public Optional<Global> global(final String name) {
            return Optional.empty();
        }

        @Override
        public <T> Optional<T> unwrap(final Class<T> nativeType) {
            return Optional.empty();
        }
    }

    private static final class RecordedCall {
        final String exportName;
        final Object[] args;

        RecordedCall(final String exportName, final Object[] args) {
            this.exportName = exportName;
            this.args = args;
        }
    }

    /**
     * Behavior descriptor: unwrap the WitString inside args[argIdx] (unwrapping WitList as
     * needed) and return its uppercased value as a fresh WitString.
     */
    private static final class UppercaseBehavior {
        final int argIdx;

        UppercaseBehavior(final int argIdx) {
            this.argIdx = argIdx;
        }

        Object apply(final Object[] args) {
            final Object slot = args[argIdx];
            final WitString found;
            if (slot instanceof WitList) {
                final List<WitValue> elems = ((WitList) slot).getElements();
                found = (WitString) elems.get(0);
            } else if (slot instanceof WitString) {
                found = (WitString) slot;
            } else {
                throw new IllegalStateException(
                        "test fake expects WitList or WitString at arg " + argIdx
                                + "; got " + slot);
            }
            try {
                return WitString.of(found.getValue().toUpperCase());
            } catch (final ai.tegmentum.wasmtime4j.exception.ValidationException e) {
                throw new AssertionError("test-fake uppercased string cannot be invalid", e);
            }
        }
    }

    /**
     * Minimal WitCallableResource used only to prove the new-shape aggregate path routes
     * through {@code asCallableResource} — the real resource machinery is covered by
     * {@link WasmtimeCallableResourceTest}.
     */
    private static final class StubCallableResource implements WitCallableResource {
        private final String tag;
        private boolean closed;

        StubCallableResource(final String tag) {
            this.tag = tag;
        }

        @Override
        public String resourceTypeName() {
            return tag;
        }

        @Override
        public Object invokeMethod(final String methodExportName, final Object... args) {
            return null;
        }

        @Override
        public Object invokeMethodWit(final String methodExportName, final Object... args) {
            return null;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
