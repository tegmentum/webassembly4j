// Synthetic counter component — end-to-end verification for the
// webassembly4j WitCallableResource surface.
//
// The exported `counter-api` interface owns a `counter` resource with a
// no-arg constructor, an `increment` method, and a `value` method that
// returns the current count. Instance state lives in a RefCell (a WASM
// component is single-threaded, so there is no cross-thread contention)
// so `increment` can mutate through the `&self` receiver that wit-bindgen's
// generated `GuestCounter` trait wants.
//
// The constructor + `Drop` impl for CounterState:
//   1. eprintln! to stderr — surfaces through wasi:cli/stderr and, when the
//      host inherits stderr, lands on the JVM process's stderr. Useful for
//      diagnostics but hard to capture from a JUnit test because a Java
//      `System.setErr` swap does NOT redirect fd 2.
//   2. std::fs::write to a preopened path — writes a marker file so the
//      Java smoke test can prove Drop actually fired without needing to
//      capture fd 2 or run the JVM as a subprocess.
//
// Both are best-effort (Drop can't return errors), which is why the tests
// treat the file as the primary signal and the stderr line as advisory.

use std::cell::RefCell;

#[allow(warnings)]
mod bindings;

use bindings::exports::tegmentum::test_counter::counter_api::{Guest, GuestCounter};

/// Preopen path (guest view) where lifecycle markers land. The host smoke
/// test grants a real temp dir at this guest path and reads the marker back
/// to prove constructor / drop actually reached the guest.
const LIFECYCLE_MARKER_DIR: &str = "/lifecycle";

struct Component;

pub struct CounterState {
    count: RefCell<u32>,
}

impl Guest for Component {
    type Counter = CounterState;
}

impl GuestCounter for CounterState {
    fn new() -> Self {
        eprintln!("counter_component: constructor called");
        write_marker("constructor", "0");
        CounterState {
            count: RefCell::new(0),
        }
    }

    fn increment(&self) {
        let mut c = self.count.borrow_mut();
        *c += 1;
    }

    fn value(&self) -> u32 {
        *self.count.borrow()
    }
}

impl Drop for CounterState {
    fn drop(&mut self) {
        let final_value = *self.count.borrow();
        eprintln!(
            "counter_component: drop called (final value = {})",
            final_value
        );
        write_marker("drop", &final_value.to_string());
    }
}

/// Best-effort write of "<kind>=<value>\n" to the lifecycle marker file. Silently
/// swallows errors — Drop can't return them, and the host may not have granted a
/// preopen (e.g. the plain lifecycle test doesn't need file evidence).
fn write_marker(kind: &str, value: &str) {
    let path = format!("{}/marker.log", LIFECYCLE_MARKER_DIR);
    let line = format!("{}={}\n", kind, value);
    let _ = std::fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(&path)
        .and_then(|mut f| {
            use std::io::Write;
            f.write_all(line.as_bytes())
        });
}

bindings::export!(Component with_types_in bindings);
