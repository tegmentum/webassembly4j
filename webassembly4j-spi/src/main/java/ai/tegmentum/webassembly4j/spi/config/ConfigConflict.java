package ai.tegmentum.webassembly4j.spi.config;

public final class ConfigConflict {

    private final String property;
    private final String commonValue;
    private final String engineValue;

    public ConfigConflict(String property, String commonValue, String engineValue) {
        this.property = property;
        this.commonValue = commonValue;
        this.engineValue = engineValue;
    }

    public String property() {
        return property;
    }

    public String commonValue() {
        return commonValue;
    }

    public String engineValue() {
        return engineValue;
    }

    @Override
    public String toString() {
        return "ConfigConflict{property='" + property + "', common='" + commonValue
                + "', engine='" + engineValue + "'}";
    }
}
