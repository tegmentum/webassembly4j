package ai.tegmentum.wasmos.runtime.generated;

import java.util.Optional;

public interface Info {
  RuntimeInfo getInfo();

  boolean supports(String feature);

  boolean native_(String feature);

  boolean available(String feature);

  Optional<String> provider(String feature);
}
