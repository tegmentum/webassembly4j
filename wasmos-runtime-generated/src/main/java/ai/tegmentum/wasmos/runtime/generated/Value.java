package ai.tegmentum.wasmos.runtime.generated;

import java.util.List;

public interface Value {
  final class VI32 implements Value {
    private final int value;

    public VI32(int value) {
      this.value = value;
    }

    public int value() {
      return this.value;
    }
  }

  final class VI64 implements Value {
    private final long value;

    public VI64(long value) {
      this.value = value;
    }

    public long value() {
      return this.value;
    }
  }

  final class VF32 implements Value {
    private final float value;

    public VF32(float value) {
      this.value = value;
    }

    public float value() {
      return this.value;
    }
  }

  final class VF64 implements Value {
    private final double value;

    public VF64(double value) {
      this.value = value;
    }

    public double value() {
      return this.value;
    }
  }

  final class VString implements Value {
    private final String value;

    public VString(String value) {
      this.value = value;
    }

    public String value() {
      return this.value;
    }
  }

  final class VBytes implements Value {
    private final List<Byte> value;

    public VBytes(List<Byte> value) {
      this.value = value;
    }

    public List<Byte> value() {
      return this.value;
    }
  }

  final class VNull implements Value {
    public VNull() {
    }
  }
}
