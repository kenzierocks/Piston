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

package org.enginehub.piston.impl;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;
import net.kyori.text.TextComponent;
import org.enginehub.piston.Command;
import org.enginehub.piston.CommandManager;
import org.enginehub.piston.converter.ArgumentConverter;
import org.enginehub.piston.converter.ArgumentConverters;
import org.enginehub.piston.exception.CommandException;
import org.enginehub.piston.exception.CommandExecutionException;
import org.enginehub.piston.exception.ConditionFailedException;
import org.enginehub.piston.exception.NoSuchCommandException;
import org.enginehub.piston.exception.NoSuchFlagException;
import org.enginehub.piston.exception.UsageException;
import org.enginehub.piston.inject.InjectedValueAccess;
import org.enginehub.piston.inject.Key;
import org.enginehub.piston.inject.MemoizingValueAccess;
import org.enginehub.piston.part.ArgAcceptingCommandFlag;
import org.enginehub.piston.part.ArgAcceptingCommandPart;
import org.enginehub.piston.part.CommandArgument;
import org.enginehub.piston.part.CommandFlag;
import org.enginehub.piston.part.CommandPart;
import org.enginehub.piston.part.NoArgCommandFlag;
import org.enginehub.piston.part.SubCommandPart;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

public class CommandManagerImpl implements CommandManager {

    private static CommandParseCache cacheCommand(Command command) {
        ImmutableList.Builder<CommandArgument> arguments = ImmutableList.builder();
        ImmutableList.Builder<ArgAcceptingCommandPart> defaultProvided = ImmutableList.builder();
        ImmutableMap.Builder<Character, CommandFlag> flags = ImmutableMap.builder();
        ImmutableMap.Builder<String, Command> subCommands = ImmutableMap.builder();
        boolean subCommandRequired = false;
        ImmutableList<CommandPart> parts = command.getParts();
        int requiredParts = 0;
        for (int i = 0; i < parts.size(); i++) {
            CommandPart part = parts.get(i);
            if (part instanceof ArgAcceptingCommandFlag || part instanceof NoArgCommandFlag) {
                CommandFlag flag = (CommandFlag) part;
                flags.put(flag.getName(), flag);
            } else if (part instanceof CommandArgument) {
                arguments.add((CommandArgument) part);
            } else if (part instanceof SubCommandPart) {
                checkState(i + 1 >= parts.size(),
                    "Sub-command must be last part.");
                for (Command cmd : ((SubCommandPart) part).getCommands()) {
                    subCommands.put(cmd.getName(), cmd);
                    for (String alias : cmd.getAliases()) {
                        subCommands.put(alias, cmd);
                    }
                }
                subCommandRequired = part.isRequired();
            } else {
                throw new IllegalStateException("Unknown part implementation " + part);
            }
            if (part.isRequired()) {
                requiredParts++;
            }
            if (part instanceof ArgAcceptingCommandPart) {
                ArgAcceptingCommandPart argPart = (ArgAcceptingCommandPart) part;
                if (argPart.getDefaults().size() > 0) {
                    defaultProvided.add(argPart);
                }
            }
        }
        ImmutableList<CommandArgument> commandArguments = arguments.build();
        int[] indexes = IntStream.range(0, commandArguments.size())
            .filter(idx -> commandArguments.get(idx).isVariable())
            .toArray();
        checkArgument(indexes.length <= 1, "Too many variable arguments");
        if (indexes.length > 0) {
            int varargIndex = indexes[0];
            checkArgument(varargIndex == commandArguments.size() - 1,
                "Variable argument must be the last argument");
        }
        return new CommandParseCache(
            commandArguments,
            defaultProvided.build(),
            flags.build(),
            subCommands.build(),
            subCommandRequired,
            requiredParts);
    }

    private static class CommandParseCache {
        final ImmutableList<CommandArgument> arguments;
        final ImmutableList<ArgAcceptingCommandPart> defaultProvided;
        final ImmutableMap<Character, CommandFlag> flags;
        final ImmutableMap<String, Command> subCommands;
        final boolean subCommandRequired;
        final int requiredParts;

        CommandParseCache(ImmutableList<CommandArgument> arguments,
                          ImmutableList<ArgAcceptingCommandPart> defaultProvided,
                          ImmutableMap<Character, CommandFlag> flags,
                          ImmutableMap<String, Command> subCommands,
                          boolean subCommandRequired, int requiredParts) {
            this.arguments = arguments;
            this.defaultProvided = defaultProvided;
            this.flags = flags;
            this.subCommands = subCommands;
            this.subCommandRequired = subCommandRequired;
            this.requiredParts = requiredParts;
        }
    }

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, Command> commands = new HashMap<>();
    // Cache information like flags if we can, but let GC clear it if needed.
    private final LoadingCache<Command, CommandParseCache> commandCache = CacheBuilder.newBuilder()
        .softValues()
        .build(CacheLoader.from(CommandManagerImpl::cacheCommand));
    private final Map<Key<?>, ArgumentConverter<?>> converters = new HashMap<>();

    public CommandManagerImpl() {
        registerConverter(Key.of(String.class), ArgumentConverters.forString());
        for (Class<?> wrapperType : ImmutableList.of(
            Byte.class, Short.class, Integer.class, Long.class,
            Float.class, Double.class,
            Character.class, Boolean.class
        )) {
            // just forcing the generic to work
            @SuppressWarnings("unchecked")
            Class<Object> fake = (Class<Object>) wrapperType;
            registerConverter(Key.of(fake), ArgumentConverters.get(TypeToken.of(fake)));
        }
    }

    @Override
    public Command.Builder newCommand(String name) {
        return CommandImpl.builder(name);
    }

    @Override
    public void register(Command command) {
        // Run it through the cache for a validity check,
        // and so that we can cache many commands in high-memory situations.
        commandCache.getUnchecked(command);
        lock.writeLock().lock();
        try {
            registerIfAvailable(command.getName(), command);
            for (String alias : command.getAliases()) {
                registerIfAvailable(alias, command);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void registerIfAvailable(String name, Command command) {
        Command existing = commands.put(name, command);
        if (existing != null) {
            commands.put(name, existing);
            throw new IllegalArgumentException("A command is already registered under "
                + name + "; existing=" + existing + ",rejected=" + command);
        }
    }

    @Override
    public <T> void registerConverter(Key<T> key, ArgumentConverter<T> converter) {
        lock.writeLock().lock();
        try {
            converters.put(key, converter);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public <T> Optional<ArgumentConverter<T>> getConverter(Key<T> key) {
        @SuppressWarnings("unchecked")
        ArgumentConverter<T> converter = (ArgumentConverter<T>) getArgumentConverter(key);
        return Optional.ofNullable(converter);
    }

    @Nullable
    private <T> ArgumentConverter<?> getArgumentConverter(Key<T> key) {
        lock.readLock().lock();
        try {
            return converters.get(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Stream<Command> getAllCommands() {
        ImmutableList<Command> allCommands;
        lock.readLock().lock();
        try {
            allCommands = ImmutableList.copyOf(commands.values());
        } finally {
            lock.readLock().unlock();
        }
        return allCommands.stream().distinct();
    }

    @Override
    public Optional<Command> getCommand(String name) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(commands.get(name));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int execute(InjectedValueAccess context, List<String> args) {
        lock.readLock().lock();
        try {
            String name = args.get(0);
            Command command = commands.get(name);
            if (command == null) {
                throw new NoSuchCommandException(name);
            }
            // cache if needed
            InjectedValueAccess cachedContext = MemoizingValueAccess.wrap(context);
            return executeSubCommand(name, ImmutableList.of(command), cachedContext,
                args.subList(1, args.size()));
        } finally {
            lock.readLock().unlock();
        }
    }

    private ImmutableList<Command> append(ImmutableList<Command> list, Command command) {
        return ImmutableList.<Command>builder()
            .addAll(list).add(command)
            .build();
    }

    private int executeSubCommand(String calledName, ImmutableList<Command> executionPath,
                                  InjectedValueAccess context, List<String> args) {
        CommandParametersImpl.Builder parameters = CommandParametersImpl.builder()
            .injectedValues(context)
            .metadata(CommandMetadataImpl.builder()
                .calledName(calledName)
                .arguments(args)
                .build());
        Command command = Iterables.getLast(executionPath);
        CommandParseCache parseCache = commandCache.getUnchecked(command);

        Set<ArgAcceptingCommandPart> defaultsNeeded = new HashSet<>(parseCache.defaultProvided);
        List<String> nonFlagArgs = removeFlagArguments(executionPath, context, args, parameters,
            parseCache, defaultsNeeded);

        Iterator<CommandArgument> partIter = parseCache.arguments.iterator();
        ListIterator<String> nonFlagArgsIter = nonFlagArgs.listIterator();
        int requiredPartsRemaining = parseCache.requiredParts + (parseCache.subCommandRequired ? 1 : 0);
        while (nonFlagArgsIter.hasNext()) {
            String next = nonFlagArgsIter.next();
            while (true) {
                if (!partIter.hasNext()) {
                    // we may still consume as a sub-command
                    if (parseCache.subCommands.isEmpty()) {
                        // but not in this case
                        break;
                    }
                    Command sub = parseCache.subCommands.get(next);
                    if (sub == null) {
                        throw new UsageException(TextComponent.of("Invalid sub-command. Options: "
                            + parseCache.subCommands.values().stream()
                            .distinct()
                            .map(Command::getName)
                            .collect(Collectors.joining(", "))),
                            executionPath);
                    }
                    executionPath = append(executionPath, sub);
                    // validate this condition first
                    addDefaults(executionPath, context, parameters, defaultsNeeded);
                    CommandParametersImpl builtParams = parameters.build();
                    if (!command.getCondition().satisfied(builtParams)) {
                        throw new ConditionFailedException(executionPath);
                    }
                    return executeSubCommand(next, executionPath, context, ImmutableList.copyOf(nonFlagArgsIter));
                }
                CommandArgument nextPart = partIter.next();
                int remainingArguments = nonFlagArgs.size() - nonFlagArgsIter.previousIndex();
                if (remainingArguments < requiredPartsRemaining) {
                    throw new UsageException(TextComponent.of("Not enough arguments"),
                        executionPath);
                }
                if (!nextPart.isRequired() && remainingArguments == requiredPartsRemaining) {
                    // skip optional parts when we need this for required parts
                    continue;
                }
                if (isAcceptedByTypeParsers(nextPart, next, context)) {
                    ImmutableList<String> values = nextPart.isVariable()
                        ? ImmutableList.<String>builder()
                        .add(next)
                        .addAll(nonFlagArgsIter).build()
                        : ImmutableList.of(next);
                    addValueFull(parameters, executionPath, nextPart, context, v -> v.values(values));
                    defaultsNeeded.remove(nextPart);
                    if (nextPart.isRequired()) {
                        requiredPartsRemaining--;
                    }
                    break;
                } else if (nextPart.isRequired()) {
                    throw new UsageException(TextComponent.builder("Missing argument for ")
                        .append(nextPart.getTextRepresentation())
                        .build(), executionPath);
                }
            }
        }

        // Handle error conditions.
        boolean moreParts = partIter.hasNext() && partIter.next().isRequired();
        // The sub-command is only handled on empty-parts.
        // If we made it here, we ran out of arguments before calling into it.
        if (moreParts || parseCache.subCommandRequired) {
            checkState(!nonFlagArgsIter.hasNext(), "Should not have more arguments to analyze.");
            throw new UsageException(TextComponent.of("Not enough arguments"),
                executionPath);
        }

        if (nonFlagArgsIter.hasNext()) {
            checkState(!partIter.hasNext(), "Should not have more parts to analyze.");
            throw new UsageException(TextComponent.of("Too many arguments"),
                executionPath);
        }

        addDefaults(executionPath, context, parameters, defaultsNeeded);

        // Run the command action.
        try {
            CommandParametersImpl builtParams = parameters.build();
            if (!command.getCondition().satisfied(builtParams)) {
                throw new ConditionFailedException(executionPath);
            }

            return command.getAction().run(builtParams);
        } catch (CommandException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandExecutionException(e, executionPath);
        }
    }

    private void addDefaults(ImmutableList<Command> executionPath, InjectedValueAccess context,
                             CommandParametersImpl.Builder parameters, Set<ArgAcceptingCommandPart> defaultsNeeded) {
        for (ArgAcceptingCommandPart part : defaultsNeeded) {
            addValueFull(parameters, executionPath, part, context, v -> v.values(part.getDefaults()));
        }
    }

    private List<String> removeFlagArguments(ImmutableList<Command> executionPath, InjectedValueAccess context,
                                             List<String> args, CommandParametersImpl.Builder parameters,
                                             CommandParseCache parseCache, Set<ArgAcceptingCommandPart> defaultsNeeded) {
        boolean flagsEnabled = true;
        Iterator<String> argIter = args.iterator();
        ImmutableList.Builder<String> nonFlagArgsBuilder = ImmutableList.builder();
        while (argIter.hasNext()) {
            String next = argIter.next();

            // Handle flags:
            if (next.startsWith("-") && flagsEnabled) {
                if (next.equals("--")) {
                    // Special option to stop flag handling.
                    flagsEnabled = false;
                    continue;
                }
                // verify not a negative number
                char firstFlag = next.charAt(1);
                if (!Character.isDigit(firstFlag) || parseCache.flags.containsKey(firstFlag)) {
                    // Pick out individual flags from the long-option form.
                        consumeFlags(executionPath, context, parameters, parseCache, defaultsNeeded, argIter, next);
                        continue;
                }
            }

            // Otherwise, eat it as the current argument.
            nonFlagArgsBuilder.add(next);
        }

        return nonFlagArgsBuilder.build();
    }

    /**
     * Check if {@code part} has type converters attached, and if so, return
     * {@code true} iff any of them will convert {@code next}. If there are no
     * type converters, also return {@code true}.
     */
    private boolean isAcceptedByTypeParsers(ArgAcceptingCommandPart part,
                                            String next,
                                            InjectedValueAccess context) {
        ImmutableSet<Key<?>> types = part.getTypes();
        if (types.isEmpty()) {
            return true;
        }

        return types.stream().anyMatch(type -> {
            ArgumentConverter<?> argumentConverter = getArgumentConverter(type);
            if (argumentConverter == null) {
                throw new IllegalStateException("No argument converter for " + type);
            }
            return argumentConverter.convert(next, context).isSuccessful();
        });
    }

    private void consumeFlags(ImmutableList<Command> executionPath,
                              InjectedValueAccess injectedValues,
                              CommandParametersImpl.Builder parameters,
                              CommandParseCache parseCache,
                              Set<ArgAcceptingCommandPart> defaultsNeeded,
                              Iterator<String> argIter, String next) {
        char[] flagArray = new char[next.length() - 1];
        next.getChars(1, next.length(), flagArray, 0);
        for (int i = 0; i < flagArray.length; i++) {
            char c = flagArray[i];
            CommandFlag flag = parseCache.flags.get(c);
            if (flag == null) {
                throw new NoSuchFlagException(executionPath, c);
            }
            if (flag instanceof ArgAcceptingCommandFlag) {
                if (i + 1 < flagArray.length) {
                    // Only allow argument-flags at the end of flag-combos.
                    throw new UsageException(TextComponent.of("Argument-accepting flags must be " +
                        "at the end of combined flag groups."), executionPath);
                }
                if (!argIter.hasNext()) {
                    break;
                }
                addValueFull(parameters, executionPath, flag, injectedValues, v -> v.value(argIter.next()));
                defaultsNeeded.remove(flag);
            } else {
                // Sanity-check. Real check is in `cacheCommand`.
                checkState(flag instanceof NoArgCommandFlag);
                parameters.addPresentPart(flag);
            }
        }
    }

    private void addValueFull(CommandParametersImpl.Builder parameters,
                              ImmutableList<Command> executionPath,
                              CommandPart part,
                              InjectedValueAccess injectedValues,
                              Consumer<CommandValueImpl.Builder> valueAdder) {
        parameters.addPresentPart(part);
        CommandValueImpl.Builder builder = CommandValueImpl.builder();
        valueAdder.accept(builder);
        parameters.addValue(part, builder
            .commandContext(executionPath)
            .partContext(part)
            .injectedValues(injectedValues)
            .manager(this)
            .build());
    }
}
