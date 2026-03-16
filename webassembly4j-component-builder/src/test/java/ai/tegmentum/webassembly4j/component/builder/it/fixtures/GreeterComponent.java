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
package ai.tegmentum.webassembly4j.component.builder.it.fixtures;

import ai.tegmentum.webassembly4j.component.builder.annotation.WitComponent;
import ai.tegmentum.webassembly4j.component.builder.annotation.WitWorld;

/**
 * Sample component entry point for integration testing.
 */
@WitComponent(packageName = "example:greeter", version = "0.1.0")
@WitWorld(name = "greeter-world")
public class GreeterComponent {
}
