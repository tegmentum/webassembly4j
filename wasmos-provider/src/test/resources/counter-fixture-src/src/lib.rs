#![no_std]

extern crate wee_alloc;

// Use `wee_alloc` — a tiny allocator suitable for wasm size-constrained
// components. The generated bindings and wit-bindgen-rt need a global
// allocator to satisfy `alloc` types (Vec, String) that show up in the
// resource machinery even if our own Rust code doesn't touch them.
#[global_allocator]
static ALLOC: wee_alloc::WeeAlloc = wee_alloc::WeeAlloc::INIT;

use core::cell::Cell;

// Generated bindings — cargo-component drops these into a `bindings` module
// keyed on the `[package.metadata.component]` metadata below.
#[allow(warnings)]
mod bindings;

use bindings::exports::tegmentum::counter::counter_api::{Guest, GuestCounter};

struct Component;

/// Minimal in-guest counter — the WIT `increment` receives a borrow, which
/// cargo-component's generated GuestCounter trait renders as `&self`;
/// interior mutability via `Cell` lets us update without needing `&mut`.
pub struct Counter {
    value: Cell<u32>,
}

impl GuestCounter for Counter {
    fn new(initial: u32) -> Self {
        Counter {
            value: Cell::new(initial),
        }
    }

    fn increment(&self) {
        self.value.set(self.value.get().wrapping_add(1));
    }

    fn get(&self) -> u32 {
        self.value.get()
    }
}

impl Guest for Component {
    type Counter = Counter;
}

bindings::export!(Component with_types_in bindings);

// Provide a minimal panic handler — we're `no_std` so libcore's default
// panic handler (which lives in `std`) isn't available. Aborting is fine
// for a test fixture: the counter methods are infallible and no path
// panics under normal use.
#[panic_handler]
fn panic(_info: &core::panic::PanicInfo) -> ! {
    core::arch::wasm32::unreachable()
}
