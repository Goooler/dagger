/*
 * Copyright (C) 2014 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.internal.codegen.binding;

import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static com.google.auto.common.MoreTypes.asTypeElement;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.consumingIterable;
import static com.squareup.javapoet.TypeName.OBJECT;
import static dagger.internal.codegen.base.ComponentAnnotation.subcomponentAnnotation;
import static dagger.internal.codegen.base.ModuleAnnotation.moduleAnnotation;
import static dagger.internal.codegen.base.MoreAnnotationMirrors.getTypeListValue;
import static dagger.internal.codegen.binding.ComponentCreatorAnnotation.subcomponentCreatorAnnotations;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.langmodel.DaggerElements.isAnyAnnotationPresent;
import static dagger.internal.codegen.xprocessing.XElements.hasAnyAnnotation;
import static javax.lang.model.util.ElementFilter.typesIn;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import dagger.Component;
import dagger.Module;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * Utility methods related to dagger configuration annotations (e.g.: {@link Component} and {@link
 * Module}).
 */
public final class ConfigurationAnnotations {

  public static Optional<XTypeElement> getSubcomponentCreator(XTypeElement subcomponent) {
    checkArgument(subcomponentAnnotation(subcomponent).isPresent());
    return subcomponent.getEnclosedTypeElements().stream()
        .filter(ConfigurationAnnotations::isSubcomponentCreator)
        // TODO(bcorso): Consider doing toOptional() instead since there should be at most 1.
        .findFirst();
  }

  public static Optional<TypeElement> getSubcomponentCreator(TypeElement subcomponent) {
    checkArgument(subcomponentAnnotation(subcomponent).isPresent());
    return typesIn(subcomponent.getEnclosedElements()).stream()
        .filter(ConfigurationAnnotations::isSubcomponentCreator)
        // TODO(bcorso): Consider doing toOptional() instead since there should be at most 1.
        .findFirst();
  }

  static boolean isSubcomponentCreator(XElement element) {
    return isSubcomponentCreator(toJavac(element));
  }

  static boolean isSubcomponentCreator(Element element) {
    return isAnyAnnotationPresent(element, subcomponentCreatorAnnotations());
  }

  // Dagger 1 support.
  public static ImmutableList<TypeMirror> getModuleInjects(AnnotationMirror moduleAnnotation) {
    checkNotNull(moduleAnnotation);
    return getTypeListValue(moduleAnnotation, "injects");
  }

  /** Returns the first type that specifies this' nullability, or empty if none. */
  public static Optional<XAnnotation> getNullableAnnotation(XElement element) {
    return element.getAllAnnotations().stream()
        .filter(annotation -> annotation.getName().contentEquals("Nullable"))
        .findFirst();
  }

  public static Optional<XType> getNullableType(XElement element) {
    return getNullableAnnotation(element).map(XAnnotation::getType);
  }

  /** Returns the first type that specifies this' nullability, or empty if none. */
  public static Optional<DeclaredType> getNullableType(Element element) {
    List<? extends AnnotationMirror> mirrors = element.getAnnotationMirrors();
    for (AnnotationMirror mirror : mirrors) {
      if (mirror.getAnnotationType().asElement().getSimpleName().contentEquals("Nullable")) {
        return Optional.of(mirror.getAnnotationType());
      }
    }
    return Optional.empty();
  }

  /**
   * Returns the full set of modules transitively {@linkplain Module#includes included} from the
   * given seed modules. If a module is malformed and a type listed in {@link Module#includes} is
   * not annotated with {@link Module}, it is ignored.
   *
   * @deprecated Use {@link ComponentDescriptor#modules()}.
   */
  @Deprecated
  public static ImmutableSet<TypeElement> getTransitiveModules(
      Collection<TypeElement> seedModules) {
    Set<TypeElement> processedElements = Sets.newLinkedHashSet();
    Queue<TypeElement> moduleQueue = new ArrayDeque<>(seedModules);
    ImmutableSet.Builder<TypeElement> moduleElements = ImmutableSet.builder();
    for (TypeElement moduleElement : consumingIterable(moduleQueue)) {
      if (processedElements.add(moduleElement)) {
        moduleAnnotation(moduleElement)
            .ifPresent(
                moduleAnnotation -> {
                  moduleElements.add(moduleElement);
                  moduleQueue.addAll(moduleAnnotation.includes());
                  moduleQueue.addAll(includesFromSuperclasses(moduleElement));
                });
      }
    }
    return moduleElements.build();
  }

  /** Returns the enclosed types annotated with the given annotation. */
  public static ImmutableSet<XTypeElement> enclosedAnnotatedTypes(
      XTypeElement typeElement, ImmutableSet<ClassName> annotations) {
    return typeElement.getEnclosedTypeElements().stream()
        .filter(enclosedType -> hasAnyAnnotation(enclosedType, annotations))
        .collect(toImmutableSet());
  }

  /** Returns {@link Module#includes()} from all transitive super classes. */
  private static ImmutableSet<TypeElement> includesFromSuperclasses(TypeElement element) {
    ImmutableSet.Builder<TypeElement> builder = ImmutableSet.builder();
    TypeMirror superclass = element.getSuperclass();
    while (superclass.getKind() == TypeKind.DECLARED && !OBJECT.equals(TypeName.get(superclass))) {
      element = asTypeElement(superclass);
      moduleAnnotation(element)
          .ifPresent(moduleAnnotation -> builder.addAll(moduleAnnotation.includes()));
      superclass = element.getSuperclass();
    }
    return builder.build();
  }

  private ConfigurationAnnotations() {}
}
