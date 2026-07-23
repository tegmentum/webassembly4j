package ai.tegmentum.wasmos.embedder.generated;

import ai.tegmentum.webassembly4j.bindgen.wit.WitResult;
import java.util.List;

public interface EmbedderCallbacks {
  WitResult<List<Byte>, String> hostCall(int providerHandle, int funcIdx, List<Byte> args);

  long nowMonotonicNs();

  List<Byte> readRandom(int len);
}
