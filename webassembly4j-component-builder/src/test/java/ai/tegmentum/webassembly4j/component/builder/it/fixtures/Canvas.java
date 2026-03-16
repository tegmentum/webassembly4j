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

import ai.tegmentum.webassembly4j.component.builder.annotation.WitExport;

import java.util.List;
import java.util.Optional;

/**
 * Sample exported interface that uses record and enum types in its methods.
 * Tests that referenced types are collected and emitted inline within the interface.
 */
@WitExport
public interface Canvas {

    void drawPoint(Point point, Color color);

    Point getOrigin();

    List<Point> getPoints();

    Optional<Color> getBackground();
}
