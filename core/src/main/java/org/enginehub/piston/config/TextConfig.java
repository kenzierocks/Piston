/*
 * Piston, a flexible command management system.
 * Copyright (C) EngineHub <http://www.enginehub.com>
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

package org.enginehub.piston.config;

import net.kyori.text.Component;
import net.kyori.text.TextComponent;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Text configuration.
 */
public class TextConfig extends Config<String> {

    private static final TextConfig COMMAND_PREFIX = new TextConfig("piston.text.command.prefix");

    /**
     * Output command prefix -- all commands will be output with this prefix before their name.
     */
    public static TextConfig commandPrefix() {
        return COMMAND_PREFIX;
    }

    public static Component commandPrefixValue() {
        return commandPrefix().value();
    }

    private TextConfig(String key) {
        super(key, "");
    }

    @Override
    protected Config<String> copyForDefault() {
        return new TextConfig(getKey());
    }

    @Deprecated
    @Override
    public Component wrap(Component... args) {
        return super.wrap(args);
    }

    @Deprecated
    @Override
    public Component wrap(List<Component> args) {
        return super.wrap(args);
    }

    @Override
    protected void checkValue(String value) {
        checkNotNull(value);
    }

    @Override
    protected Component apply(List<Component> input) {
        checkState(input.isEmpty(), "TextConfig takes no arguments");
        return TextComponent.of(getValue());
    }
}