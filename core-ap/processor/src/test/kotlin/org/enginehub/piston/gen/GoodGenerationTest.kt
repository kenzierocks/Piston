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

package org.enginehub.piston.gen

import com.google.testing.compile.CompilationSubject.assertThat
import com.google.testing.compile.JavaFileObjects
import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import org.enginehub.piston.CommandParameters
import org.enginehub.piston.CommandValue
import org.enginehub.piston.annotation.Command
import org.enginehub.piston.annotation.param.Arg
import org.enginehub.piston.annotation.param.ArgFlag
import org.enginehub.piston.annotation.param.Switch
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import javax.lang.model.element.Modifier


@DisplayName("core-ap can generate")
@Execution(ExecutionMode.CONCURRENT)
class GenerationTest {

    @DisplayName("some no argument commands")
    @Test
    fun generatesNoArg() {
        val commands = commands("NoArg", listOf(
            MethodSpec.methodBuilder("noArg")
                .addAnnotation(AnnotationSpec.builder(Command::class.java)
                    .addMember("name", "\$S", "noArgument")
                    .addMember("desc", "\$S", "DESCRIPTION")
                    .build())
                .returns(Void.TYPE)
                .addException(Exception::class.java)
                .build(),
            MethodSpec.methodBuilder("noArgFooter")
                .addAnnotation(AnnotationSpec.builder(Command::class.java)
                    .addMember("name", "\$S", "noArgumentFooter")
                    .addMember("desc", "\$S", "DESCRIPTION")
                    .addMember("descFooter", "\$S", "DESC FOOTER")
                    .build())
                .returns(Void.TYPE)
                .build(),
            MethodSpec.methodBuilder("noArgCondition")
                .addAnnotation(AnnotationSpec.builder(Command::class.java)
                    .addMember("name", "\$S", "noArgumentCondition")
                    .addMember("desc", "\$S", "DESCRIPTION")
                    .build())
                .addAnnotation(AlwaysTrueCondition::class.java)
                .returns(Void.TYPE)
                .build(),
            MethodSpec.methodBuilder("noArgStatic")
                .addAnnotation(AnnotationSpec.builder(Command::class.java)
                    .addMember("name", "\$S", "noArgumentStatic")
                    .addMember("desc", "\$S", "DESCRIPTION")
                    .build())
                .addModifiers(Modifier.STATIC)
                .returns(Void.TYPE)
                .build()
        ))
        val compilation = compiler().compile(commands)
        assertThat(compilation)
            .succeededWithoutWarnings()
        assertThat(compilation)
            .generatedSourceFile("$PACKAGE.NoArgRegistration")
            .hasSourceEquivalentTo(JavaFileObjects.forResource("gen/NoArgRegistration.java"))
    }

    @DisplayName("some no argument commands (with non-arg parameters)")
    @Test
    fun generatesNoArgWithNonArgParameters() {
        val commands = commands("NonArgParameters", listOf(
            MethodSpec.methodBuilder("nonArgCommandParameters")
                .addAnnotation(AnnotationSpec.builder(Command::class.java)
                    .addMember("name", "\$S", "nonArgCommandParameters")
                    .addMember("desc", "\$S", "DESCRIPTION")
                    .build())
                .returns(Void.TYPE)
                .addParameter(CommandParameters::class.java, "params")
                .build(),
            MethodSpec.methodBuilder("nonArgInjected")
                .addAnnotation(AnnotationSpec.builder(Command::class.java)
                    .addMember("name", "\$S", "nonArgInjected")
                    .addMember("desc", "\$S", "DESCRIPTION")
                    .build())
                .returns(Void.TYPE)
                .addParameter(Object::class.java, "injected")
                .build()
        ))
        val compilation = compiler().compile(commands)
        assertThat(compilation)
            .succeededWithoutWarnings()
        assertThat(compilation)
            .generatedSourceFile("$PACKAGE.NonArgParametersRegistration")
            .hasSourceEquivalentTo(JavaFileObjects.forResource("gen/NonArgParametersRegistration.java"))
    }

    @DisplayName("a one argument (CommandValue) command")
    @Test
    fun generatesOneCommandValueArg() {
        val commands = commands("CommandValueArg", listOf(
            MethodSpec.methodBuilder("valueArg")
                .addAnnotation(AnnotationSpec.builder(Command::class.java)
                    .addMember("name", "\$S", "valueArgument")
                    .addMember("desc", "\$S", "DESCRIPTION")
                    .build())
                .returns(Void.TYPE)
                .addParameter(
                    ParameterSpec.builder(CommandValue::class.java, "arg")
                        .addAnnotation(AnnotationSpec.builder(Arg::class.java)
                            .addMember("desc", "\$S", "ARG DESCRIPTION")
                            .build())
                        .build()
                )
                .build()
        ))
        val compilation = compiler().compile(commands)
        assertThat(compilation)
            .succeededWithoutWarnings()
        assertThat(compilation)
            .generatedSourceFile("$PACKAGE.CommandValueArgRegistration")
            .hasSourceEquivalentTo(JavaFileObjects.forResource("gen/CommandValueArgRegistration.java"))
    }

    @DisplayName("a one argument (Collection) command")
    @Test
    fun generatesOneCollectionArg() {
        val commands = commands("CollectionArg", listOf(
            MethodSpec.methodBuilder("collectionArg")
                .addAnnotation(AnnotationSpec.builder(Command::class.java)
                    .addMember("name", "\$S", "collectionArgument")
                    .addMember("desc", "\$S", "DESCRIPTION")
                    .build())
                .returns(Void.TYPE)
                .addParameter(
                    ParameterSpec.builder(
                        ParameterizedTypeName.get(Collection::class.java, String::class.java), "arg"
                    )
                        .addAnnotation(AnnotationSpec.builder(Arg::class.java)
                            .addMember("desc", "\$S", "ARG DESCRIPTION")
                            .build())
                        .build()
                )
                .build(),
            // Validate Object not multi-compatible
            MethodSpec.methodBuilder("objectArg")
                .addAnnotation(AnnotationSpec.builder(Command::class.java)
                    .addMember("name", "\$S", "objectArgument")
                    .addMember("desc", "\$S", "DESCRIPTION")
                    .build())
                .returns(Void.TYPE)
                .addParameter(
                    ParameterSpec.builder(Object::class.java, "arg")
                        .addAnnotation(AnnotationSpec.builder(Arg::class.java)
                            .addMember("desc", "\$S", "ARG DESCRIPTION")
                            .build())
                        .build()
                )
                .build()
        ))
        val compilation = compiler().compile(commands)
        assertThat(compilation)
            .succeededWithoutWarnings()
        assertThat(compilation)
            .generatedSourceFile("$PACKAGE.CollectionArgRegistration")
            .hasSourceEquivalentTo(JavaFileObjects.forResource("gen/CollectionArgRegistration.java"))
    }

    @DisplayName("a one or more argument (int) command")
    @Test
    fun generatesOneIntArg() {
        val commands = commands("IntArg", listOf(
            MethodSpec.methodBuilder("intArg")
                .addAnnotation(AnnotationSpec.builder(Command::class.java)
                    .addMember("name", "\$S", "intArgument")
                    .addMember("desc", "\$S", "DESCRIPTION")
                    .build())
                .returns(TypeName.INT)
                .addParameter(
                    ParameterSpec.builder(TypeName.INT, "arg")
                        .addAnnotation(AnnotationSpec.builder(Arg::class.java)
                            .addMember("desc", "\$S", "ARG DESCRIPTION")
                            .build())
                        .build()
                )
                .addStatement("return arg")
                .build(),
            MethodSpec.methodBuilder("variableIntArg")
                .addAnnotation(AnnotationSpec.builder(Command::class.java)
                    .addMember("name", "\$S", "variableIntArgument")
                    .addMember("desc", "\$S", "DESCRIPTION")
                    .build())
                .returns(TypeName.VOID)
                .addParameter(
                    ParameterSpec.builder(
                        ParameterizedTypeName.get(List::class.java, Integer::class.java), "arg"
                    )
                        .addAnnotation(AnnotationSpec.builder(Arg::class.java)
                            .addMember("name", "\$S", "args")
                            .addMember("desc", "\$S", "ARG DESCRIPTION")
                            .addMember("variable", "true")
                            .build())
                        .build()
                )
                .build()
        ))
        val compilation = compiler().compile(commands)
        assertThat(compilation)
            .succeededWithoutWarnings()
        assertThat(compilation)
            .generatedSourceFile("$PACKAGE.IntArgRegistration")
            .hasSourceEquivalentTo(JavaFileObjects.forResource("gen/IntArgRegistration.java"))
    }

    @DisplayName("various flag commands")
    @Test
    fun generatesFlags() {
        val commands = commands("Flags", listOf(
            MethodSpec.methodBuilder("booleanFlag")
                .addAnnotation(AnnotationSpec.builder(Command::class.java)
                    .addMember("name", "\$S", "booleanFlag")
                    .addMember("desc", "\$S", "DESCRIPTION")
                    .build())
                .returns(TypeName.VOID)
                .addParameter(
                    ParameterSpec.builder(TypeName.BOOLEAN, "flag")
                        .addAnnotation(AnnotationSpec.builder(Switch::class.java)
                            .addMember("name", "'f'")
                            .addMember("desc", "\$S", "ARG DESCRIPTION")
                            .build())
                        .build()
                )
                .build(),
            MethodSpec.methodBuilder("stringArgFlag")
                .addAnnotation(AnnotationSpec.builder(Command::class.java)
                    .addMember("name", "\$S", "stringArgFlag")
                    .addMember("desc", "\$S", "DESCRIPTION")
                    .build())
                .returns(TypeName.VOID)
                .addParameter(
                    ParameterSpec.builder(String::class.java, "flag")
                        .addAnnotation(AnnotationSpec.builder(ArgFlag::class.java)
                            .addMember("name", "'f'")
                            .addMember("desc", "\$S", "ARG DESCRIPTION")
                            .addMember("def", "\$S", "DEFAULT")
                            .build())
                        .build()
                )
                .build(),
            MethodSpec.methodBuilder("stringArgFlagCustom")
                .addAnnotation(AnnotationSpec.builder(Command::class.java)
                    .addMember("name", "\$S", "stringArgFlagCustom")
                    .addMember("desc", "\$S", "DESCRIPTION")
                    .build())
                .returns(TypeName.VOID)
                .addParameter(
                    ParameterSpec.builder(String::class.java, "flag")
                        .addAnnotation(AnnotationSpec.builder(ArgFlag::class.java)
                            .addMember("name", "'f'")
                            .addMember("argName", "\$S", "ARG NAME")
                            .addMember("desc", "\$S", "ARG DESCRIPTION")
                            .addMember("def", "\$S", "DEFAULT")
                            .build())
                        .build()
                )
                .build()
        ))
        val compilation = compiler().compile(commands)
        assertThat(compilation)
            .succeededWithoutWarnings()
        assertThat(compilation)
            .generatedSourceFile("$PACKAGE.FlagsRegistration")
            .hasSourceEquivalentTo(JavaFileObjects.forResource("gen/FlagsRegistration.java"))
    }

}