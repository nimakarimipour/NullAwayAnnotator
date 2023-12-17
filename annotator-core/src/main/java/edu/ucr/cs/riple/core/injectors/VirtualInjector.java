/*
 * MIT License
 *
 * Copyright (c) 2022 Nima Karimipour
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

package edu.ucr.cs.riple.core.injectors;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import edu.ucr.cs.riple.core.Config;
import edu.ucr.cs.riple.core.Context;
import edu.ucr.cs.riple.injector.changes.AddAnnotation;
import edu.ucr.cs.riple.injector.changes.RemoveAnnotation;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Wrapper tool used to inject annotations virtually to the source code. This injector serializes
 * requested changes to a file which later can be read by a library model loaders and make its
 * impact by library models.
 */
public class VirtualInjector extends AnnotationInjector {

  /** Path to library model loader */
  private final Path libraryModelPath;
  /**
   * Annotator configuration, required to check if downstream dependencies analysis is activated or
   * retrieve the path to library model loader.
   */
  private final Config config;
  /** Name of the resource file in library model loader which contains list of nullable methods. */
  public static final String NULLABLE_METHOD_LIST_FILE_NAME = "nullable-methods.tsv";
  /** Name of the resource file in library model loader which contains list of nullable fields. */
  public static final String NULLABLE_FIELD_LIST_FILE_NAME = "nullable-fields.tsv";

  public VirtualInjector(Context context) {
    super(context);
    this.config = context.config;
    this.libraryModelPath = config.nullawayLibraryModelLoaderPath;
    if (config.downStreamDependenciesAnalysisActivated) {
      Preconditions.checkNotNull(
          libraryModelPath,
          "NullawayLibraryModelLoaderPath cannot be null while downstream dependencies analysis is activated.");
      clear();
    }
  }

  @Override
  public void removeAnnotations(Set<RemoveAnnotation> changes) {
    clear();
  }

  @Override
  public void injectAnnotations(Set<AddAnnotation> changes) {
    if (!config.downStreamDependenciesAnalysisActivated) {
      throw new IllegalStateException(
          "Downstream dependencies analysis not activated, cannot inject annotations virtually!");
    }
    // write methods
    writeAnnotationsToFile(
        changes.stream().filter(addAnnotation -> addAnnotation.getLocation().isOnMethod()),
        libraryModelPath.resolve(NULLABLE_METHOD_LIST_FILE_NAME),
        annot ->
            Stream.of(
                annot.getLocation().clazz + "\t" + annot.getLocation().toMethod().method + "\n"));
    // write fields
    writeAnnotationsToFile(
        changes.stream().filter(addAnnotation -> addAnnotation.getLocation().isOnField()),
        libraryModelPath.resolve(NULLABLE_FIELD_LIST_FILE_NAME),
        annot ->
            // An annotation on a single statement with multiple declaration will be considered for
            // each declaration as well. Hence, we have to consider it for all variables.
            // E.g. for {@Nullable String a, b;} we have to consider both a and b be nullable and
            // each one on a separate line.
            annot.getLocation().toField().variables.stream()
                .map(variable -> annot.getLocation().clazz + "\t" + variable + "\n"));
  }

  /**
   * Writes the passed annotation to the passed file. It uses the passed mapper to map the
   * annotation to a string. And writes each string to a separate line.
   *
   * @param annotations Annotations to be written.
   * @param path Path to the file to be written.
   * @param mapper Mapper to map the annotation to a string.
   */
  private static void writeAnnotationsToFile(
      Stream<AddAnnotation> annotations,
      Path path,
      Function<AddAnnotation, Stream<String>> mapper) {
    try (BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(path.toFile()))) {
      Set<String> rows = annotations.flatMap(mapper).collect(Collectors.toSet());
      for (String row : rows) {
        os.write(row.getBytes(Charset.defaultCharset()), 0, row.length());
      }
      os.flush();
    } catch (IOException e) {
      throw new RuntimeException("Error happened for writing at file: " + path, e);
    }
  }

  /** Removes any existing entry from library models. */
  private void clear() {
    try {
      new FileOutputStream(libraryModelPath.resolve(NULLABLE_FIELD_LIST_FILE_NAME).toFile())
          .close();
      new FileOutputStream(libraryModelPath.resolve(NULLABLE_METHOD_LIST_FILE_NAME).toFile())
          .close();
    } catch (IOException e) {
      throw new RuntimeException("Could not clear library model loader content", e);
    }
  }
}
