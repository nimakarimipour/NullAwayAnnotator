/*
 * MIT License
 *
 * Copyright (c) 2023 Nima Karimipour
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package edu.ucr.cs.riple.core.checkers.nullaway;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import edu.ucr.cs.riple.core.Context;
import edu.ucr.cs.riple.core.checkers.CheckerBaseClass;
import edu.ucr.cs.riple.core.module.ModuleConfiguration;
import edu.ucr.cs.riple.core.module.ModuleInfo;
import edu.ucr.cs.riple.core.registries.field.FieldInitializationStore;
import edu.ucr.cs.riple.core.registries.index.Error;
import edu.ucr.cs.riple.core.registries.index.Fix;
import edu.ucr.cs.riple.core.registries.region.Region;
import edu.ucr.cs.riple.core.util.Utility;
import edu.ucr.cs.riple.injector.Printer;
import edu.ucr.cs.riple.injector.changes.AddAnnotation;
import edu.ucr.cs.riple.injector.changes.AddMarkerAnnotation;
import edu.ucr.cs.riple.injector.changes.AddSingleElementAnnotation;
import edu.ucr.cs.riple.injector.changes.AddTypeUseMarkerAnnotation;
import edu.ucr.cs.riple.injector.location.Location;
import edu.ucr.cs.riple.injector.location.OnField;
import edu.ucr.cs.riple.injector.location.OnParameter;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Represents <a href="https://github.com/uber/NullAway">NullAway</a> checker in Annotator. */
public class NullAway extends CheckerBaseClass<NullAwayError> {

  /**
   * The name of the checker. To select this checker, this name must be used in the configurations.
   */
  public static final String NAME = "NULLAWAY";

  /** Supported version of NullAway serialization. */
  public static final int VERSION = 3;

  public NullAway(Context context) {
    super(context);
  }

  @Override
  public Set<NullAwayError> deserializeErrors(ModuleInfo module) {
    ImmutableSet<Path> paths =
        module.getModuleConfiguration().stream()
            .map(configuration -> configuration.dir.resolve("errors.tsv"))
            .collect(ImmutableSet.toImmutableSet());
    Set<NullAwayError> errors = new HashSet<>();
    paths.forEach(
        path -> {
          try {
            try (BufferedReader br = Files.newBufferedReader(path, Charset.defaultCharset())) {
              String line;
              // Skip header.
              br.readLine();
              while ((line = br.readLine()) != null) {
                errors.add(deserializeErrorFromTSVLine(module, line));
              }
            }
          } catch (IOException e) {
            throw new RuntimeException("Exception happened in reading errors at: " + path, e);
          }
        });
    return errors;
  }

  /**
   * Deserializes an error from a TSV line.
   *
   * @param moduleInfo Module info.
   * @param line Given TSV line.
   * @return the deserialized error corresponding to the values in the given tsv line.
   */
  private NullAwayError deserializeErrorFromTSVLine(ModuleInfo moduleInfo, String line) {
    Context context = moduleInfo.getContext();
    String[] values = line.split("\t");
    Preconditions.checkArgument(
        values.length == 12,
        "Expected 12 values to create Error instance in NullAway serialization version 2 but found: "
            + values.length);
    int offset = Integer.parseInt(values[4]);
    Path path = Printer.deserializePath(values[5]);
    String errorMessage = values[1];
    String errorType = values[0];
    Region region = new Region(values[2], values[3]);
    Location nonnullTarget =
        Location.createLocationFromArrayInfo(Arrays.copyOfRange(values, 6, 12));
    if (nonnullTarget == null && errorType.equals(NullAwayError.METHOD_INITIALIZER_ERROR)) {
      Set<AddAnnotation> annotationsOnField =
          computeAddAnnotationInstancesForUninitializedFields(
              errorMessage, region.clazz, moduleInfo);
      return createError(
          errorType,
          errorMessage,
          region,
          context.offsetHandler.getOriginalOffset(path, offset),
          annotationsOnField,
          moduleInfo);
    }
    if (nonnullTarget != null && nonnullTarget.isOnField()) {
      nonnullTarget = extendVariableList(nonnullTarget.toField(), moduleInfo);
    }
    Set<AddAnnotation> annotations;
    if (nonnullTarget == null) {
      annotations = Set.of();
    } else if (Utility.isTypeUseAnnotation(config.nullableAnnot)) {
      annotations = Set.of(new AddTypeUseMarkerAnnotation(nonnullTarget, config.nullableAnnot));
    } else {
      annotations = Set.of(new AddMarkerAnnotation(nonnullTarget, config.nullableAnnot));
    }

    return createError(
        errorType,
        errorMessage,
        region,
        context.offsetHandler.getOriginalOffset(path, offset),
        annotations,
        moduleInfo);
  }

  /**
   * Extracts uninitialized field names from the given error message.
   *
   * @param errorMessage Error message.
   * @return Set of uninitialized field names.
   */
  private Set<String> extractUninitializedFieldNames(String errorMessage) {
    String prefix = "initializer method does not guarantee @NonNull field";
    int begin = prefix.length();
    if (errorMessage.charAt(begin) == 's') {
      begin += 1;
    }
    int end = errorMessage.indexOf(" is initialized along");
    end = end == -1 ? errorMessage.indexOf(" are initialized along ") : end;
    if (end == -1) {
      throw new RuntimeException(
          "Error message for initializer error not recognized in version "
              + 3
              + ": "
              + errorMessage);
    }
    String[] fieldsData = errorMessage.substring(begin, end).split(",");
    Set<String> fields =
        Arrays.stream(fieldsData)
            // NullAway serializes line number right after a field name starting with an open
            // parentheses. (e.g. foo (line z)). This approach of extracting field names is
            // extremely dependent on the format of NullAway error messages. Should be watched
            // carefully and updated if the format is changed by NullAway (maybe regex?).
            .map(s -> s.substring(0, s.indexOf("(")).trim())
            .collect(Collectors.toSet());
    if (fields.size() == 0) {
      throw new RuntimeException(
          "Could not extract any uninitialized field in message for initializer error in version "
              + 3
              + ": "
              + errorMessage);
    }
    return fields;
  }

  /**
   * Computes a set of {@link AddAnnotation} instances for fields that are uninitialized. This
   * method extracts field names from the provided error message, and for each uninitialized field,
   * it attempts to find the location of the field within the specified class. If a field's location
   * is found, an {@link AddMarkerAnnotation} is created with the appropriate nullable annotation
   * and added to the result set.
   *
   * @param errorMessage the error message containing the details about uninitialized fields.
   * @param encClass The class where this error is reported.
   * @param module the {@link ModuleInfo} containing the field registry and configuration
   *     information.
   * @return an {@link ImmutableSet} of {@link AddAnnotation} instances representing the fields that
   *     should have annotations added, based on their uninitialized status.
   */
  private ImmutableSet<AddAnnotation> computeAddAnnotationInstancesForUninitializedFields(
      String errorMessage, String encClass, ModuleInfo module) {
    return extractUninitializedFieldNames(errorMessage).stream()
        .map(
            field -> {
              OnField locationOnField =
                  module.getFieldRegistry().getLocationOnField(encClass, field);
              if (locationOnField == null) {
                return null;
              }
              return new AddMarkerAnnotation(
                  extendVariableList(locationOnField, module), config.nullableAnnot);
            })
        .filter(Objects::nonNull)
        .collect(ImmutableSet.toImmutableSet());
  }

  /**
   * Suppresses remaining errors by following steps below:
   *
   * <ul>
   *   <li>Enclosing method of triggered errors will be marked with {@code @NullUnmarked}
   *       annotation.
   *   <li>Uninitialized fields (inline or by constructor) will be annotated as
   *       {@code @SuppressWarnings("NullAway.Init")}.
   *   <li>Explicit {@code Nullable} assignments to fields will be annotated as
   *       {@code @SuppressWarnings("NullAway")}.
   * </ul>
   */
  @Override
  public void suppressRemainingErrors() {
    // Collect regions with remaining errors.
    Utility.buildTarget(context);
    Set<NullAwayError> remainingErrors = deserializeErrors(context.targetModuleInfo);
    // Collect all regions for NullUnmarked.
    // For all errors in regions which correspond to a method's body, we can add @NullUnmarked at
    // the method level.
    Set<AddAnnotation> nullUnMarkedAnnotations =
        remainingErrors.stream()
            // find the corresponding method nodes.
            .map(
                error -> {
                  if (error.getRegion().isOnCallable()
                      &&
                      // We suppress initialization errors reported on constructors using
                      // @SuppressWarnings("NullAway.Init"). We add @NullUnmarked on constructors
                      // only for errors in the body of the constructor.
                      error.isNonInitializationError()) {
                    return context
                        .targetModuleInfo
                        .getMethodRegistry()
                        .findMethodByName(error.encClass(), error.encMember());
                  }
                  // For methods invoked in an initialization region, where the error is that
                  // `@Nullable` is being passed as an argument, we add a `@NullUnmarked` annotation
                  // to the called method.
                  if (error.messageType.equals("PASS_NULLABLE")
                      && error.isSingleAnnotationFix()
                      && error.toResolvingLocation().isOnParameter()) {
                    OnParameter nullableParameter = error.toResolvingParameter();
                    return context
                        .targetModuleInfo
                        .getMethodRegistry()
                        .findMethodByName(
                            nullableParameter.clazz, nullableParameter.enclosingMethod.method);
                  }
                  return null;
                })
            // Filter null values from map above.
            .filter(Objects::nonNull)
            .map(node -> new AddMarkerAnnotation(node.location, config.nullUnMarkedAnnotation))
            .collect(Collectors.toSet());

    // For errors within static initialization blocks, add a @NullUnmarked annotation on the
    // enclosing class
    nullUnMarkedAnnotations.addAll(
        remainingErrors.stream()
            .filter(
                error ->
                    error.getRegion().isOnInitializationBlock()
                        && !error.getRegion().isInAnonymousClass())
            .map(
                error ->
                    new AddMarkerAnnotation(
                        context.targetModuleInfo.getLocationOnClass(error.getRegion().clazz),
                        config.nullUnMarkedAnnotation))
            .collect(Collectors.toSet()));
    Set<AddAnnotation> result = new HashSet<>(nullUnMarkedAnnotations);

    // Collect suppress warnings, errors on field declaration regions.
    Set<OnField> fieldsWithSuppressWarnings =
        remainingErrors.stream()
            .filter(
                error -> {
                  if (!error.getRegion().isOnField()) {
                    return false;
                  }
                  if (error.messageType.equals("PASS_NULLABLE")) {
                    // It is already resolved with @NullUnmarked selected above.
                    return false;
                  }
                  // We can silence them by SuppressWarnings("NullAway.Init")
                  return error.isNonInitializationError();
                })
            .map(
                error ->
                    context
                        .targetModuleInfo
                        .getFieldRegistry()
                        .getLocationOnField(error.getRegion().clazz, error.getRegion().member))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    Set<AddAnnotation> suppressWarningsAnnotations =
        fieldsWithSuppressWarnings.stream()
            .map(
                onField ->
                    new AddSingleElementAnnotation(onField, "SuppressWarnings", "NullAway", false))
            .collect(Collectors.toSet());
    result.addAll(suppressWarningsAnnotations);

    // Collect NullAway.Init SuppressWarnings
    Set<AddAnnotation> initializationSuppressWarningsAnnotations =
        remainingErrors.stream()
            .filter(
                e ->
                    e.messageType.equals("METHOD_NO_INIT") || e.messageType.equals("FIELD_NO_INIT"))
            .flatMap(Error::getResolvingFixesStream)
            .filter(Fix::isOnField)
            // Filter nodes annotated with SuppressWarnings("NullAway")
            .filter(fix -> !fieldsWithSuppressWarnings.contains(fix.toField()))
            .map(
                fix ->
                    new AddSingleElementAnnotation(
                        fix.toField(), "SuppressWarnings", "NullAway.Init", false))
            .collect(Collectors.toSet());
    result.addAll(initializationSuppressWarningsAnnotations);
    context.getInjector().injectAnnotations(result);
    // update log
    context.log.updateInjectedAnnotations(result);
    // Collect @NullUnmarked annotations on classes for any remaining error.
    Utility.buildTarget(context);
    remainingErrors = deserializeErrors(context.targetModuleInfo);
    nullUnMarkedAnnotations =
        remainingErrors.stream()
            .filter(error -> !error.getRegion().isInAnonymousClass())
            .map(
                error ->
                    new AddMarkerAnnotation(
                        context.targetModuleInfo.getLocationOnClass(error.getRegion().clazz),
                        config.nullUnMarkedAnnotation))
            .collect(Collectors.toSet());
    context.getInjector().injectAnnotations(nullUnMarkedAnnotations);
    // update log
    context.log.updateInjectedAnnotations(nullUnMarkedAnnotations);
  }

  @Override
  public void preprocess() {
    // Collect @Initializer annotations. Heuristically, we add @Initializer on methods which writes
    // a @Nonnull value to more than one uninitialized field, and guarantees initialized fields are
    // nonnull at all exit paths.
    // Collect uninitialized fields.
    Set<OnField> uninitializedFields =
        Utility.readErrorsFromOutputDirectory(
                context, context.targetModuleInfo, NullAwayError.class)
            .stream()
            .filter(e -> e.messageType.equals("FIELD_NO_INIT"))
            .flatMap(Error::getResolvingFixesStream)
            .filter(Fix::isOnField)
            .map(Fix::toField)
            .collect(Collectors.toSet());
    FieldInitializationStore fieldInitializationStore = new FieldInitializationStore(context);
    // Collect selected initializers methods.
    Set<AddAnnotation> initializers =
        fieldInitializationStore.findInitializers(uninitializedFields).stream()
            .map(onMethod -> new AddMarkerAnnotation(onMethod, config.initializerAnnot))
            .collect(Collectors.toSet());
    // Inject @Initializer annotations.
    context.getInjector().injectAnnotations(initializers);
  }

  /**
   * Creates a {@link NullAwayError} instance using the provided arguments. It also removes
   * annotation change requests that are on an element with explict nonnull annotation.
   *
   * @param errorType Error Type from NullAway.
   * @param errorMessage Error Message from NullAway.
   * @param region Region where the error is reported.
   * @param offset Offset of program point in the source file where the error is reported.
   * @param annotations Annotations that should be added source file to resolve the error.
   * @param module Module where this error is reported.
   * @return Creates and returns the corresponding {@link NullAwayError} instance using the provided
   *     information.
   */
  private NullAwayError createError(
      String errorType,
      String errorMessage,
      Region region,
      int offset,
      Set<AddAnnotation> annotations,
      ModuleInfo module) {
    // Filter fixes on elements with explicit nonnull annotations.
    ImmutableSet<AddAnnotation> cleanedAnnotations =
        annotations.stream()
            .filter(
                annot ->
                    !module.getNonnullStore().hasExplicitNonnullAnnotation(annot.getLocation()))
            .collect(ImmutableSet.toImmutableSet());
    return new NullAwayError(errorType, errorMessage, region, offset, cleanedAnnotations);
  }

  @Override
  public void verifyCheckerCompatibility() {
    Path pathToSerializationVersion =
        config.globalDir.resolve("0").resolve("serialization_version.txt");
    if (!Files.exists(pathToSerializationVersion)) {
      throw new RuntimeException(
          "This version of Annotator does not support the using NullAway version, please upgrade NullAway to version >= 0.10.10");
    }
    try {
      int version =
          Integer.parseInt(Files.readString(pathToSerializationVersion, Charset.defaultCharset()));
      Preconditions.checkArgument(
          version == VERSION,
          "This Annotator version only supports NullAway serialization version "
              + VERSION
              + ", but found: "
              + version
              + ", Please update Annotator or NullAway accordingly.");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void prepareConfigFilesForBuild(ImmutableSet<ModuleConfiguration> configurations) {
    configurations.forEach(
        module -> {
          FixSerializationConfig.Builder nullAwayConfig =
              new FixSerializationConfig.Builder()
                  .setSuggest(true, true)
                  .setOutputDirectory(module.dir.toString())
                  .setFieldInitInfo(true);
          nullAwayConfig.writeAsXML(module.checkerConfig.toString());
        });
  }
}
