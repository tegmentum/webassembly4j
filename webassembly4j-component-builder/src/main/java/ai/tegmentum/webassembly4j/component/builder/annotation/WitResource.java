/*
 * Copyright 2025 Tegmentum AI. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.tegmentum.webassembly4j.component.builder.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps a Java class to a WIT resource type (opaque handle).
 *
 * <p>Resources represent opaque handles with constructors and methods. Public
 * constructors become WIT resource constructors. Public instance methods become
 * resource methods. Static methods annotated with the resource class as the
 * first parameter become static resource functions.
 *
 * <p>Example:
 * <pre>{@code
 * @WitResource
 * public class FileHandle {
 *     public FileHandle(String path) { ... }     // constructor(path: string)
 *     public byte[] read(int len) { ... }         // read: func(len: s32) -> list<u8>
 *     public void write(byte[] data) { ... }      // write: func(data: list<u8>)
 *     public void close() { ... }                 // close: func()
 * }
 * }</pre>
 *
 * <p>Generates:
 * <pre>{@code
 * resource file-handle {
 *     constructor(path: string);
 *     read: func(len: s32) -> list<u8>;
 *     write: func(data: list<u8>);
 *     close: func();
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface WitResource {
}
