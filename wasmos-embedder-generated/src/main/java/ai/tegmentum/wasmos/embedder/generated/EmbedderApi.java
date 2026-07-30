package ai.tegmentum.wasmos.embedder.generated;

import ai.tegmentum.webassembly4j.bindgen.wit.WitResult;
import java.util.List;

public interface EmbedderApi {
  WitResult<List<Byte>, Error> dispatchHostCallback(long callbackId, List<Byte> argsFrame);
}
