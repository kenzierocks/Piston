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

package eh;

import static org.enginehub.piston.internal.RegistrationUtil.getCommandMethod;
import static org.enginehub.piston.internal.RegistrationUtil.listenersAfterCall;
import static org.enginehub.piston.internal.RegistrationUtil.listenersAfterThrow;
import static org.enginehub.piston.internal.RegistrationUtil.listenersBeforeCall;
import static org.enginehub.piston.internal.RegistrationUtil.requireOptional;
import static org.enginehub.piston.part.CommandParts.arg;
import static org.enginehub.piston.part.CommandParts.flag;

import com.google.common.collect.ImmutableList;
import java.lang.Integer;
import java.lang.Throwable;
import java.lang.reflect.Method;
import java.util.Collection;
import net.kyori.text.TextComponent;
import net.kyori.text.TranslatableComponent;
import org.enginehub.piston.CommandManager;
import org.enginehub.piston.CommandParameters;
import org.enginehub.piston.gen.CommandCallListener;
import org.enginehub.piston.gen.CommandRegistration;
import org.enginehub.piston.inject.Key;
import org.enginehub.piston.part.CommandArgument;

final class IntArgRegistration implements CommandRegistration<IntArg> {
    private static final Key<Integer> integer_Key = Key.of(Integer.class);

    private CommandManager commandManager;

    private IntArg containerInstance;

    private ImmutableList<CommandCallListener> listeners;

    private final CommandArgument argPart = arg(TranslatableComponent.of("piston.argument.arg"), TextComponent.of("ARG DESCRIPTION"))
        .defaultsTo(ImmutableList.of())
        .ofTypes(ImmutableList.of(integer_Key))
        .build();

    private IntArgRegistration() {
        this.listeners = ImmutableList.of();
    }

    static IntArgRegistration builder() {
        return new IntArgRegistration();
    }

    public IntArgRegistration commandManager(CommandManager commandManager) {
        this.commandManager = commandManager;
        return this;
    }

    public IntArgRegistration containerInstance(IntArg containerInstance) {
        this.containerInstance = containerInstance;
        return this;
    }

    public IntArgRegistration listeners(Collection<CommandCallListener> listeners) {
        this.listeners = ImmutableList.copyOf(listeners);
        return this;
    }

    public void build() {
        commandManager.register("intArgument", b -> {
            b.aliases(ImmutableList.of());
            b.description(TextComponent.of("DESCRIPTION"));
            b.parts(ImmutableList.of(argPart));
            b.action(this::intArgument);
        });
    }

    private int intArgument(CommandParameters parameters) {
        Method cmdMethod = getCommandMethod(IntArg.class, "intArg", int.class);
        listenersBeforeCall(listeners, cmdMethod, parameters);
        try {
            int result;
            result = containerInstance.intArg(this.extract$arg(parameters));
            listenersAfterCall(listeners, cmdMethod, parameters);
            return result;
        } catch (Throwable t) {
            listenersAfterThrow(listeners, cmdMethod, parameters, t);
            throw t;
        }
    }

    private int extract$arg(CommandParameters parameters) {
        return argPart.value(parameters).asSingle(integer_Key);
    }
}
