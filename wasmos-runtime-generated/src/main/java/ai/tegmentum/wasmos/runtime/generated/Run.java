package ai.tegmentum.wasmos.runtime.generated;

import ai.tegmentum.webassembly4j.bindgen.wit.WitResult;
import java.util.List;

public interface Run {
  WitResult<Value, Error> exec(List<Byte> componentBytes, List<Capability> caps);
}
