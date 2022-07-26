/*
 * Copyright (c) 2022 University of California, Riverside.
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

package edu.ucr.cs.riple.librarymodel;

import static com.uber.nullaway.LibraryModels.MethodRef.methodRef;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.uber.nullaway.LibraryModels;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * NullAway Library Model, used to communicate (Nullability assumptions) with NullAway when analysing
 * downstream dependencies.
 */
@AutoService(LibraryModels.class)
public class LibraryModelLoader implements LibraryModels {

  public final String NULLABLE_METHOD_LIST_FILE_NAME = "nullable-methods.tsv";
  /** Methods with nullable returns. */
  public final ImmutableSet<MethodRef> nullableMethods;

  // Assuming this constructor will be called when picked by service loader
  public LibraryModelLoader() {
    nullableMethods = parseTSVFileFromResourcesToMethodRef(NULLABLE_METHOD_LIST_FILE_NAME);
  }

  /**
   * Loads a file from resources and parses the content into set of {@link
   * com.uber.nullaway.LibraryModels.MethodRef}.
   *
   * @param name File name in resources.
   * @return ImmutableSet of content in the passed file. Returns empty if the file does not exist.
   */
  private static ImmutableSet<MethodRef> parseTSVFileFromResourcesToMethodRef(String name) {
    // Check if resource exists
    if (LibraryModelLoader.class.getResource(name) == null) {
      return ImmutableSet.of();
    }
    try (InputStream is = LibraryModelLoader.class.getResourceAsStream(name)) {
      if (is == null) {
        return ImmutableSet.of();
      }
      ImmutableSet.Builder<MethodRef> contents = ImmutableSet.builder();
      BufferedReader reader = new BufferedReader(new InputStreamReader(is));
      String line = reader.readLine();
      while (line != null) {
        String[] values = line.split("\t");
        contents.add(methodRef(values[0], values[1]));
        line = reader.readLine();
      }
      return contents.build();
    } catch (IOException e) {
      throw new RuntimeException("Error while reading content of resource: " + name, e);
    }
  }

  @Override
  public ImmutableSetMultimap<MethodRef, Integer> failIfNullParameters() {
    return null;
  }

  @Override
  public ImmutableSetMultimap<MethodRef, Integer> explicitlyNullableParameters() {
    return null;
  }

  @Override
  public ImmutableSetMultimap<MethodRef, Integer> nonNullParameters() {
    return null;
  }

  @Override
  public ImmutableSetMultimap<MethodRef, Integer> nullImpliesTrueParameters() {
    return null;
  }

  @Override
  public ImmutableSetMultimap<MethodRef, Integer> nullImpliesFalseParameters() {
    return null;
  }

  @Override
  public ImmutableSetMultimap<MethodRef, Integer> nullImpliesNullParameters() {
    return null;
  }

  @Override
  public ImmutableSet<MethodRef> nullableReturns() {
    return null;
  }

  @Override
  public ImmutableSet<MethodRef> nonNullReturns() {
    return null;
  }

  @Override
  public ImmutableSetMultimap<MethodRef, Integer> castToNonNullMethods() {
    return null;
  }
}
