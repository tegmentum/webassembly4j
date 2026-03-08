package ai.tegmentum.webassembly4j.api.capability;

public interface FuelController {

    void addFuel(long fuel);

    long fuelRemaining();
}
