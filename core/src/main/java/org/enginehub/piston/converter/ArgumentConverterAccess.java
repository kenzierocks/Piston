/*
 * Piston, a flexible command management system.
 * Copyright (C) EngineHub <https://www.enginehub.org>
 * Copyright (C) Piston contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.enginehub.piston.converter;

import org.enginehub.piston.inject.Key;

import java.util.Optional;
import java.util.Set;

/**
 * Access to converters.
 */
public interface ArgumentConverterAccess {

    ArgumentConverterAccess EMPTY = EmptyArgumentConverterAccess.INSTANCE;

    Set<Key<?>> keySet();

    /**
     * Get a converter for a key.
     *
     * @param key the key the converter is registered under
     * @param <T> the type of value returned by the converter
     * @return the converter, if present
     */
    <T> Optional<ArgumentConverter<T>> getConverter(Key<T> key);
}
