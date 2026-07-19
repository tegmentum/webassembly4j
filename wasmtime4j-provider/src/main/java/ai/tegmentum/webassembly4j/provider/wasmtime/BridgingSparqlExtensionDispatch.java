package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.wasmtime4j.wit.WitList;
import ai.tegmentum.wasmtime4j.wit.WitString;
import ai.tegmentum.wasmtime4j.wit.WitValue;
import ai.tegmentum.webassembly4j.api.ComponentInstance;
import ai.tegmentum.webassembly4j.api.WitCallableResource;
import ai.tegmentum.webassembly4j.api.exception.ExecutionException;

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Transitional helper that lets one JVM plugin dispatch against SPARQL extension guests speaking
 * either the new sparql-extension ABI ({@code tegmentum:webfunction@0.1.0}) or the old flat-export
 * ABI (per-crate world exporting {@code evaluate} / {@code aggregate-step} /
 * {@code aggregate-finish} at world root).
 *
 * <p>Motivation: the substrate is committed to the new ABI (see
 * {@code webfunction-wit/docs/design/custom-function-registry.md}) but 177 of 181 guest crates in
 * {@code ~/git/webfunctions/crates/} still export the old shape. Plugins that migrate dispatch
 * without a bridging layer trap every {@code wf:call} against an unmigrated guest. This helper
 * absorbs the try-new-fall-back-to-old branch so every JVM plugin gets bridging for free, and
 * every plugin retires the bridging by deleting one construction site the day the last old-shape
 * guest migrates. See {@code webfunction-wit/docs/design/bridging-dispatch.md}.
 *
 * <p>Lifecycle: one instance per {@link ComponentInstance}. Capability detection runs at
 * construction time — the helper probes the instance's export surface once and caches the result
 * so subsequent dispatches skip the fallback path. Every detection logs at {@link Level#FINE}
 * so operators can watch the migration curve.
 *
 * <p>Thread-safety: dispatch calls are as thread-safe as the underlying
 * {@link ComponentInstance}, which in wasmtime4j means single-threaded per instance. This class
 * adds no locking of its own.
 *
 * <p><b>Transitional. Delete when every guest in {@code ~/git/webfunctions/crates/} exports the
 * sparql-extension world.</b>
 */
public final class BridgingSparqlExtensionDispatch {

    private static final Logger LOG = Logger.getLogger(BridgingSparqlExtensionDispatch.class.getName());

    /**
     * New-shape filter call — the {@code call} function on the {@code extension} interface of
     * package {@code tegmentum:webfunction@0.1.0}. Interface-scoped exports use the
     * {@code <package>/<interface>@<version>#<name>} form on wasmtime4j.
     */
    static final String NEW_SHAPE_FILTER_EXPORT =
            "tegmentum:webfunction/extension@0.1.0#call";

    /**
     * New-shape filter interface — the parent interface hosting {@link #NEW_SHAPE_FILTER_EXPORT}.
     * Used as a shape-detection fallback for older wasmtime4j runtimes whose {@code hasFunction}
     * probe doesn't recognize interface-qualified export names (fixed in wasmtime4j 46.0.1-1.4.6
     * via commit 30f7fc5b, but downstream plugins on older wasmtime4j jars still hit the
     * limitation).
     */
    static final String NEW_SHAPE_FILTER_INTERFACE =
            "tegmentum:webfunction/extension@0.1.0";

    /**
     * Old-shape filter call — a bare {@code evaluate} function exported at the guest world's root.
     */
    static final String OLD_SHAPE_FILTER_EXPORT = "evaluate";

    /**
     * New-shape aggregate constructor — {@code new-aggregate: func(name: string)} on the
     * {@code aggregate} interface, returning an {@code own<aggregate-state>} handle.
     */
    static final String NEW_SHAPE_AGGREGATE_CTOR =
            "tegmentum:webfunction/aggregate@0.1.0#new-aggregate";

    /**
     * New-shape aggregate interface — the parent interface hosting {@link
     * #NEW_SHAPE_AGGREGATE_CTOR}. Same fallback rationale as {@link #NEW_SHAPE_FILTER_INTERFACE}.
     */
    static final String NEW_SHAPE_AGGREGATE_INTERFACE =
            "tegmentum:webfunction/aggregate@0.1.0";

    /**
     * New-shape aggregate methods. The plugin-side driver calls {@code step} and {@code finish}
     * on the {@link WitCallableResource} returned by {@link #newAggregate(String)}; these
     * constants name the methods the resource dispatches to when the guest is new-shape.
     */
    static final String NEW_SHAPE_AGGREGATE_STEP =
            "tegmentum:webfunction/aggregate@0.1.0#[method]aggregate-state.step";

    static final String NEW_SHAPE_AGGREGATE_FINISH =
            "tegmentum:webfunction/aggregate@0.1.0#[method]aggregate-state.finish";

    /**
     * Old-shape aggregate exports — bare {@code aggregate-step} + {@code aggregate-finish} at
     * world root. The shim {@link WitCallableResource} returned by
     * {@link #newAggregate(String)} routes {@code step} / {@code finish} method calls back to
     * these bare exports.
     */
    static final String OLD_SHAPE_AGGREGATE_STEP = "aggregate-step";
    static final String OLD_SHAPE_AGGREGATE_FINISH = "aggregate-finish";

    /** Neutral method names the shim recognizes. See {@link ShimAggregateResource}. */
    static final String SHIM_METHOD_STEP = "step";
    static final String SHIM_METHOD_FINISH = "finish";

    private final ComponentInstance instance;
    private final boolean filterIsNewShape;
    private final boolean aggregateIsNewShape;

    /**
     * Bind bridging to a specific component instance and detect its ABI shape once. Both
     * capability probes run at construction time; the results are cached for the lifetime of
     * this dispatcher.
     *
     * @param instance the underlying component instance; must be non-null and open
     */
    public BridgingSparqlExtensionDispatch(final ComponentInstance instance) {
        this.instance = Objects.requireNonNull(instance, "instance");
        // Detection: try the qualified-export probe first (fast, exact — a passing
        // `hasFunction` guarantees the invocation path will succeed). Fall back to
        // `exportsInterface` when it misses — on wasmtime4j runtimes older than
        // 46.0.1-1.4.6 the qualified-name probe returns false even when the export
        // is present, so without the interface-level fallback new-shape guests get
        // mis-detected as old-shape and dispatch traps on absent flat exports. The
        // filter side has carried this fallback at the plugin layer since the
        // limitation was diagnosed; hoisting it into the bridging helper lets the
        // aggregate side (which the plugins do not work around) reach new-shape
        // guests too, and lets the plugin-side filter workaround retire.
        this.filterIsNewShape = detectNewShape(
                instance, NEW_SHAPE_FILTER_EXPORT, NEW_SHAPE_FILTER_INTERFACE);
        this.aggregateIsNewShape = detectNewShape(
                instance, NEW_SHAPE_AGGREGATE_CTOR, NEW_SHAPE_AGGREGATE_INTERFACE);
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(
                    Level.FINE,
                    "Bridging dispatch: filter shape = {0}, aggregate shape = {1}",
                    new Object[] {
                            filterIsNewShape ? "new (sparql-extension@0.1.0)" : "old (flat evaluate)",
                            aggregateIsNewShape ? "new (aggregate-state resource)" : "old (bare aggregate-step/finish)"
                    });
        }
    }

    /**
     * Returns whether the underlying instance dispatches its filter function calls through the
     * new sparql-extension ABI. Exposed for observability and for plugin-side callers whose
     * downstream marshalling differs between shapes (new-shape returns a single {@code term};
     * old-shape returns a {@code list<binding-set>}).
     */
    public boolean filterIsNewShape() {
        return filterIsNewShape;
    }

    /**
     * Returns whether the underlying instance dispatches its aggregate lifecycle through the
     * new sparql-extension ABI.
     */
    public boolean aggregateIsNewShape() {
        return aggregateIsNewShape;
    }

    /**
     * Dispatch a filter call against either the new or the old ABI. The choice is made once at
     * construction time (see {@link #filterIsNewShape()}); this method reads the cached
     * decision and calls the appropriate underlying export.
     *
     * <p>New-shape signature:
     * {@code call(name: string, args: list<term>) -> result<term, string>}. The
     * {@code functionName} is marshalled as the {@code name} argument; {@code args} become the
     * {@code list<term>}.
     *
     * <p>Old-shape signature: {@code evaluate(args: list<value>) -> result<binding-sets, string>}.
     * The {@code functionName} is discarded — old-shape guests are per-function crates and
     * their identity comes from which artifact was loaded.
     *
     * @param functionName the SPARQL-level function name (e.g. {@code "upper"})
     * @param args the filter arguments as provider {@code WitValue}s
     * @return the provider's raw WitValue result (shape differs by ABI; see method contract)
     * @throws ExecutionException if the underlying invocation fails
     */
    public WitValue callFilter(final String functionName, final List<WitValue> args) {
        Objects.requireNonNull(functionName, "functionName");
        Objects.requireNonNull(args, "args");
        if (args.isEmpty()) {
            // WitList.of rejects an empty list because element type isn't inferrable from an
            // empty varargs — SPARQL filter functions with zero terms are also degenerate at
            // the language layer. Callers with a real need (a pre-typed WitList.empty) should
            // pass through the bridge(newShapePath, oldShapePath, args) method directly.
            throw new IllegalArgumentException(
                    "callFilter args list must be non-empty; use bridge() directly if you have "
                            + "a pre-typed WitList");
        }
        final WitValue argList = WitList.of(args);
        if (filterIsNewShape) {
            return (WitValue) instance.invokeWit(
                    NEW_SHAPE_FILTER_EXPORT, witString(functionName), argList);
        }
        // Old-shape: bare `evaluate` takes just the arg list. Any old-shape crate that needs
        // to distinguish by name is broken by construction — the crate IS the function.
        return (WitValue) instance.invokeWit(OLD_SHAPE_FILTER_EXPORT, argList);
    }

    /**
     * Return a {@link WitCallableResource} that models the aggregate lifecycle. For new-shape
     * guests, this is the real resource returned by {@code new-aggregate(name)}. For old-shape
     * guests, this is a shim whose {@code step} / {@code finish} method calls dispatch back to
     * the guest's flat {@code aggregate-step} / {@code aggregate-finish} exports.
     *
     * <p>Either way, the plugin-side driver calls
     * {@code resource.invokeMethodWit("step", args)} and
     * {@code resource.invokeMethodWit("finish")} — the bridging hides which ABI the guest
     * actually speaks.
     *
     * <p>Old-shape caveat: the shim discards the {@code mult: u64} row-multiplicity argument
     * the flat aggregate-step signature carries (defaults to {@code 1}) because the new-shape
     * ABI does not model row multiplicity. Guests that need multiplicity semantics under the
     * old shape must migrate to new-shape or bypass the bridging.
     *
     * @param aggregateName the SPARQL-level aggregate name (e.g. {@code "sum"})
     * @return an open {@link WitCallableResource} ready for {@code step} / {@code finish} calls
     * @throws ExecutionException if new-shape ctor invocation fails
     */
    public WitCallableResource newAggregate(final String aggregateName) {
        Objects.requireNonNull(aggregateName, "aggregateName");
        if (aggregateIsNewShape) {
            // new-aggregate: func(name: string) -> result<aggregate-state, string>.
            // The wire return is a WitResult; asCallableResource wants the ok-arm
            // resource handle directly (a WitOwn/WitBorrow/WitResource, per the
            // provider adapter's contract). Unwrap here so callers don't have to
            // know the ctor is result-typed — the shim's error surface stays a
            // plain ExecutionException carrying the guest's error message.
            final Object handle = instance.invokeWit(
                    NEW_SHAPE_AGGREGATE_CTOR, witString(aggregateName));
            final WitCallableResource raw =
                    instance.asCallableResource(unwrapResultOk(handle, NEW_SHAPE_AGGREGATE_CTOR));
            // Wrap so callers can dispatch with the neutral method names
            // ("step"/"finish") the shim also accepts — the wasmtime resource
            // machinery underneath wants the fully-qualified WIT method export
            // name and doesn't know about the neutral vocabulary.
            return new NeutralMethodNameResource(raw);
        }
        return new ShimAggregateResource(instance);
    }

    /**
     * Unwrap the ok arm of a {@link ai.tegmentum.wasmtime4j.wit.WitResult}. Non-result values pass
     * through unchanged — old-shape or degenerate guest returns don't get wrapped in a result on
     * the wire, and this method is only load-bearing on the new-shape ctor path where the WIT
     * signature ({@code result<aggregate-state, string>}) forces the wrapper. An err arm becomes
     * an {@link ExecutionException} carrying the guest's message; a null/absent ok payload throws
     * the same to name the export that surfaced the empty result.
     */
    private static Object unwrapResultOk(final Object handle, final String exportName) {
        if (!(handle instanceof ai.tegmentum.wasmtime4j.wit.WitResult)) {
            return handle;
        }
        final ai.tegmentum.wasmtime4j.wit.WitResult wr =
                (ai.tegmentum.wasmtime4j.wit.WitResult) handle;
        if (wr.isErr()) {
            final String message = wr.getErr()
                    .map(v -> v instanceof WitString ? ((WitString) v).getValue() : v.toString())
                    .orElse("component returned err with no payload");
            throw new ExecutionException(exportName + " returned err: " + message);
        }
        return wr.getOk().orElseThrow(() -> new ExecutionException(
                exportName + " returned ok with no payload"));
    }

    /**
     * Shape-detection helper. Returns true when the guest exports the new-shape entry either
     * as a qualified function ({@code hasFunction}) or transitively via its containing WIT
     * interface ({@code exportsInterface}).
     *
     * <p>The two probes are not redundant: {@code hasFunction} is the exact answer but requires
     * wasmtime4j 46.0.1-1.4.6+ to recognize interface-qualified names, and downstream plugins
     * pin older wasmtime4j jars in ways this helper cannot control. {@code exportsInterface}
     * catches every case where the qualified probe misses.
     */
    private static boolean detectNewShape(
            final ComponentInstance instance,
            final String qualifiedExport,
            final String interfaceName) {
        return instance.hasFunction(qualifiedExport) || instance.exportsInterface(interfaceName);
    }

    /**
     * Adapter over {@link WitString#of(String)}, which declares a checked
     * {@link ai.tegmentum.wasmtime4j.exception.ValidationException} for the null-arg case that
     * cannot occur here (we {@link Objects#requireNonNull} the string on entry to each dispatch
     * method). Converting to the API-level {@link ExecutionException} keeps the public method
     * signatures free of provider-specific checked exceptions.
     */
    private static WitString witString(final String value) {
        try {
            return WitString.of(value);
        } catch (final ai.tegmentum.wasmtime4j.exception.ValidationException e) {
            throw new ExecutionException("Invalid WIT string: " + value, e);
        }
    }

    /**
     * Generic bridging entry point. Try the new-shape export path first; if the underlying
     * instance does not export it, fall back to the old-shape path. Callers whose dispatch
     * shape isn't covered by {@link #callFilter} or {@link #newAggregate} — the Stardog
     * plugin's {@code cardinality-estimate} overlay is the running example — compose against
     * this method rather than duplicate the try/fallback pattern.
     *
     * <p>Detection is performed lazily on every call (unlike the filter/aggregate probes done
     * at construction) because callers may bridge many distinct export paths and pre-probing
     * every one would grow the constructor arbitrarily. Callers who bridge the same path
     * repeatedly should cache the result themselves.
     *
     * @param newShapePath the interface-scoped export name to try first
     * @param oldShapePath the bare export name to fall back to
     * @param args the arguments to pass through
     * @return the provider's raw WitValue result
     * @throws ExecutionException if both paths are absent or invocation fails
     */
    public WitValue bridge(
            final String newShapePath,
            final String oldShapePath,
            final Object... args) {
        Objects.requireNonNull(newShapePath, "newShapePath");
        Objects.requireNonNull(oldShapePath, "oldShapePath");
        if (instance.hasFunction(newShapePath)) {
            return (WitValue) instance.invokeWit(newShapePath, args);
        }
        if (instance.hasFunction(oldShapePath)) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(
                        Level.FINE,
                        "Bridging dispatch: {0} not exported; falling back to {1}",
                        new Object[] {newShapePath, oldShapePath});
            }
            return (WitValue) instance.invokeWit(oldShapePath, args);
        }
        throw new ExecutionException(
                "Neither new-shape export '" + newShapePath
                        + "' nor old-shape export '" + oldShapePath
                        + "' is present on the component instance");
    }

    /**
     * Adapter over a real wasmtime {@link WitCallableResource} that also accepts the neutral
     * method names ({@code "step"} / {@code "finish"}) the {@link ShimAggregateResource} accepts.
     * Translates them to the fully qualified WIT method-export paths
     * ({@link #NEW_SHAPE_AGGREGATE_STEP} / {@link #NEW_SHAPE_AGGREGATE_FINISH}) that
     * {@code invokeResourceMethodWit} expects on wasmtime. Fully-qualified names pass through
     * unchanged so a caller that isn't going through the bridging vocabulary still works.
     */
    static final class NeutralMethodNameResource implements WitCallableResource {

        private final WitCallableResource delegate;

        NeutralMethodNameResource(final WitCallableResource delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public String resourceTypeName() {
            return delegate.resourceTypeName();
        }

        @Override
        public Object invokeMethod(final String methodExportName, final Object... args) {
            return delegate.invokeMethod(translate(methodExportName), args);
        }

        @Override
        public Object invokeMethodWit(final String methodExportName, final Object... args) {
            return delegate.invokeMethodWit(translate(methodExportName), args);
        }

        private static String translate(final String methodExportName) {
            if (SHIM_METHOD_STEP.equals(methodExportName)) {
                return NEW_SHAPE_AGGREGATE_STEP;
            }
            if (SHIM_METHOD_FINISH.equals(methodExportName)) {
                return NEW_SHAPE_AGGREGATE_FINISH;
            }
            return methodExportName;
        }

        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    /**
     * Shim {@link WitCallableResource} that fronts a pair of flat exports
     * ({@code aggregate-step} / {@code aggregate-finish}) as if they were methods on a WIT
     * resource. The shim owns no native handle — {@link #close()} is a no-op because old-shape
     * aggregates keep their accumulator state on the component instance itself, not on a
     * per-group resource.
     */
    static final class ShimAggregateResource implements WitCallableResource {

        private final ComponentInstance instance;
        private volatile boolean closed;

        ShimAggregateResource(final ComponentInstance instance) {
            this.instance = Objects.requireNonNull(instance, "instance");
        }

        @Override
        public String resourceTypeName() {
            // No native resource type — this is a synthesized handle over flat exports.
            return "";
        }

        @Override
        public Object invokeMethod(final String methodExportName, final Object... args) {
            final Object result = invokeMethodWit(methodExportName, args);
            if (result instanceof WitValue) {
                return ((WitValue) result).toJava();
            }
            return result;
        }

        @Override
        public Object invokeMethodWit(final String methodExportName, final Object... args) {
            ensureOpen();
            // Accept both the neutral shim method names ("step" / "finish") and the fully
            // qualified new-shape method paths a plugin might pass through unchanged when it
            // isn't aware which ABI it's holding. The latter path lets a plugin that thinks
            // it's talking to a new-shape resource work against the shim without branching.
            if (SHIM_METHOD_STEP.equals(methodExportName)
                    || NEW_SHAPE_AGGREGATE_STEP.equals(methodExportName)) {
                return dispatchStep(args);
            }
            if (SHIM_METHOD_FINISH.equals(methodExportName)
                    || NEW_SHAPE_AGGREGATE_FINISH.equals(methodExportName)) {
                return dispatchFinish();
            }
            throw new ExecutionException(
                    "Shim aggregate resource does not recognize method '" + methodExportName
                            + "'; expected 'step' or 'finish'");
        }

        private Object dispatchStep(final Object[] args) {
            // Old-shape signature: aggregate-step(args: list<value>, mult: u64) -> result<_, string>.
            // Default mult to 1 — new-shape has no multiplicity, and per the memo any guest
            // that needs multiplicity must migrate off the bridging.
            if (args == null || args.length == 0) {
                throw new IllegalArgumentException(
                        "Shim aggregate step requires at least one argument (the row's terms)");
            }
            final Object argList;
            if (args.length == 1 && args[0] instanceof WitList) {
                argList = args[0];
            } else {
                final java.util.List<WitValue> collected = new java.util.ArrayList<>(args.length);
                for (final Object a : args) {
                    if (!(a instanceof WitValue)) {
                        throw new ExecutionException(
                                "Shim aggregate step expects WitValue args; got "
                                        + (a == null ? "null" : a.getClass().getName()));
                    }
                    collected.add((WitValue) a);
                }
                argList = WitList.of(collected);
            }
            return instance.invokeWit(
                    OLD_SHAPE_AGGREGATE_STEP,
                    argList,
                    ai.tegmentum.wasmtime4j.wit.WitU64.of(1L));
        }

        private Object dispatchFinish() {
            return instance.invokeWit(OLD_SHAPE_AGGREGATE_FINISH);
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            // Idempotent — old-shape has no resource to drop, but we still flip the flag so
            // downstream code that treats the resource as consumed sees the expected state.
            closed = true;
        }

        private void ensureOpen() {
            if (closed) {
                throw new IllegalStateException("Shim aggregate resource has been closed");
            }
        }
    }
}
