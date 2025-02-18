/*
 * MIT License
 *
 * Copyright (c) 2024 Nima Karimipour
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

package edu.ucr.cs.riple.scanner.generatedcode;

import static edu.ucr.cs.riple.scanner.SymbolUtil.hasDirectAnnotationWithSimpleName;

import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.errorprone.util.ASTHelpers;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.util.Context;

/**
 * Provides APIs for querying whether code is annotated for nullness checking, and for related
 * queries on what annotations are present on a class/method and/or on relevant enclosing scopes
 * (i.e. enclosing classes or methods). Makes use of caching internally for performance.
 *
 * <p>NOTE: THIS SOURCE FILE IS COPIED AND MODIFIED FROM UBER <a
 * href="https://github.com/uber/NullAway">NULLAWAY</a> SOURCE CODE
 */
public final class CodeAnnotationInfo {

  private static final Context.Key<CodeAnnotationInfo> ANNOTATION_INFO_KEY = new Context.Key<>();

  private static final int MAX_CLASS_CACHE_SIZE = 200;

  private final Cache<Symbol.ClassSymbol, Symbol.ClassSymbol> classCache =
      CacheBuilder.newBuilder().maximumSize(MAX_CLASS_CACHE_SIZE).build();

  private CodeAnnotationInfo() {}

  /**
   * Get the CodeAnnotationInfo for the given javac context. We ensure there is one instance per
   * context (as opposed to using static fields) to avoid memory leaks.
   */
  public static CodeAnnotationInfo instance(Context context) {
    CodeAnnotationInfo annotationInfo = context.get(ANNOTATION_INFO_KEY);
    if (annotationInfo == null) {
      annotationInfo = new CodeAnnotationInfo();
      context.put(ANNOTATION_INFO_KEY, annotationInfo);
    }
    return annotationInfo;
  }

  /**
   * Check if a symbol comes from generated code.
   *
   * @param symbol symbol for entity
   * @return true if symbol represents an entity contained in a class annotated with
   *     {@code @Generated}; false otherwise
   */
  public boolean isInGeneratedClass(Symbol symbol) {
    Symbol.ClassSymbol classSymbol =
        symbol instanceof Symbol.ClassSymbol
            ? (Symbol.ClassSymbol) symbol
            : ASTHelpers.enclosingClass(symbol);
    if (classSymbol == null) {
      return false;
    }
    Symbol.ClassSymbol outermostClassSymbol = get(classSymbol);
    return hasDirectAnnotationWithSimpleName(outermostClassSymbol, "Generated");
  }

  /**
   * Retrieve the (outermostClass, isNullMarked) record for a given class symbol.
   *
   * <p>This method is recursive, using the cache on the way up and populating it on the way down.
   *
   * @param classSymbol The class to query, possibly an inner class
   * @return A record including the outermost class in which the given class is nested, as well as
   *     boolean flag noting whether it should be treated as nullness-annotated, taking into account
   *     annotations on enclosing classes, the containing package, and other NullAway configuration
   *     like annotated packages
   */
  private Symbol.ClassSymbol get(Symbol.ClassSymbol classSymbol) {
    Symbol.ClassSymbol record = classCache.getIfPresent(classSymbol);
    if (record != null) {
      return record;
    }
    if (classSymbol.getNestingKind().isNested()) {
      Symbol owner = classSymbol.owner;
      Preconditions.checkNotNull(owner, "Symbol.owner should only be null for modules!");
      Symbol.ClassSymbol enclosingClass = ASTHelpers.enclosingClass(classSymbol);
      // enclosingClass can be null in weird cases like for array methods
      if (enclosingClass != null) {
        record = get(enclosingClass);
      }
    }
    if (record == null) {
      // We are already at the outermost class (we can find), so let's create a record for it
      record = classSymbol;
    }
    classCache.put(classSymbol, record);
    return record;
  }
}
