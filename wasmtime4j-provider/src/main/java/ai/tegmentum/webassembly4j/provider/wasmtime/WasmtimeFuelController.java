package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.wasmtime4j.Store;
import ai.tegmentum.wasmtime4j.exception.WasmException;
import ai.tegmentum.webassembly4j.api.capability.FuelController;
import ai.tegmentum.webassembly4j.api.exception.WebAssemblyException;

final class WasmtimeFuelController implements FuelController {

    private final Store store;

    WasmtimeFuelController(Store store) {
        this.store = store;
    }

    @Override
    public void addFuel(long fuel) {
        try {
            long current = store.getFuel();
            if (current < 0) {
                current = 0;
            }
            store.setFuel(current + fuel);
        } catch (WasmException e) {
            throw new WebAssemblyException("Failed to add fuel", e);
        }
    }

    @Override
    public long fuelRemaining() {
        try {
            long fuel = store.getFuel();
            return fuel < 0 ? 0 : fuel;
        } catch (WasmException e) {
            throw new WebAssemblyException("Failed to get fuel", e);
        }
    }
}
