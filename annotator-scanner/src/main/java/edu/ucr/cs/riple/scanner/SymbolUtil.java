/*
 * Methods in this class are copied from NullAway from Uber Inc.
 *
 * MIT License
 *
 * Copyright (c) 2020 Nima Karimipour
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

package edu.ucr.cs.riple.scanner;

import com.google.common.base.Preconditions;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.TargetType;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.lang.model.element.AnnotationMirror;

/** Helper class for working with Symbols. */
public class SymbolUtil {

  /**
   * find the closest ancestor method in a superclass or superinterface that method overrides
   *
   * @param method the subclass method
   * @param types the types data structure from javac
   * @return closest overridden ancestor method, or <code>null</code> if method does not override
   *     anything
   */
  @Nullable
  public static Symbol.MethodSymbol getClosestOverriddenMethod(
      Symbol.MethodSymbol method, Types types) {
    // taken from Error Prone MethodOverrides check
    Symbol.ClassSymbol owner = method.enclClass();
    for (Type s : types.closure(owner.type)) {
      if (types.isSameType(s, owner.type)) {
        continue;
      }
      for (Symbol m : s.tsym.members().getSymbolsByName(method.name)) {
        if (!(m instanceof Symbol.MethodSymbol)) {
          continue;
        }
        Symbol.MethodSymbol msym = (Symbol.MethodSymbol) m;
        if (msym.isStatic()) {
          continue;
        }
        if (method.overrides(msym, owner, types, /*checkReturn*/ false)) {
          return msym;
        }
      }
    }
    return null;
  }

  /**
   * Does the symbol have a {@code @Nullable} declaration or type-use annotation?
   *
   * <p>NOTE: this method does not work for checking all annotations of parameters of methods from
   * class files. For that case, use {@link #paramHasNullableAnnotation(Symbol.MethodSymbol, int,
   * Config)}
   */
  public static boolean hasNullableAnnotation(Symbol symbol, Config config) {
    return hasNullableAnnotation(SymbolUtil.getAllAnnotations(symbol), config);
  }

  public static boolean hasNullableAnnotation(
      Stream<? extends AnnotationMirror> annotations, Config config) {
    return annotations
        .map(anno -> anno.getAnnotationType().toString())
        .anyMatch(anno -> isNullableAnnotation(anno, config));
  }

  /**
   * Does the symbol have a {@code @Nonnull} declaration or type-use annotation?
   *
   * <p>NOTE: this method does not work for checking all annotations of parameters of methods from
   * class files. For that case, use {@link #paramHasNullableAnnotation(Symbol.MethodSymbol, int,
   * Config)}
   */
  public static boolean hasNonnullAnnotations(Symbol symbol, Config config) {
    return hasNonnullAnnotations(SymbolUtil.getAllAnnotations(symbol), config);
  }

  public static boolean hasNonnullAnnotations(
      Stream<? extends AnnotationMirror> annotations, Config config) {
    return annotations
        .map(anno -> anno.getAnnotationType().toString())
        .anyMatch(anno -> isNonnullAnnotation(anno, config));
  }

  private static boolean isNonnullAnnotation(String annotName, Config config) {
    return annotName.endsWith(".NonNull")
        || annotName.endsWith(".NotNull")
        || annotName.endsWith(".Nonnull")
        || annotName.equals("androidx.annotation.RecentlyNonNull")
        || config.isNonnullAnnotation(annotName);
  }

  /**
   * Check whether an annotation should be treated as equivalent to <code>@Nullable</code>.
   *
   * @param annotName annotation name
   * @return true if we treat annotName as a <code>@Nullable</code> annotation, false otherwise
   */
  public static boolean isNullableAnnotation(String annotName, Config config) {
    return annotName.endsWith(".Nullable")
        // endsWith and not equals and no `org.`, because gradle's shadow plug in rewrites strings
        // and will replace `org.checkerframework` with `shadow.checkerframework`. Yes, really...
        // I assume it's something to handle reflection.
        || annotName.endsWith(".checkerframework.checker.nullness.compatqual.NullableDecl")
        // matches javax.annotation.CheckForNull and edu.umd.cs.findbugs.annotations.CheckForNull
        || annotName.endsWith(".CheckForNull");
  }

  /**
   * Does the parameter of {@code symbol} at {@code paramInd} have a {@code @Nullable} declaration
   * or type-use annotation? This method works for methods defined in either source or class files.
   */
  public static boolean paramHasNullableAnnotation(
      Symbol.MethodSymbol symbol, int paramInd, Config config) {
    return hasNullableAnnotation(getAllAnnotationsForParameter(symbol, paramInd), config);
  }

  /**
   * Does the parameter of {@code symbol} at {@code paramInd} have a {@code @Nonnull} declaration or
   * type-use annotation? This method works for methods defined in either source or class files.
   */
  public static boolean paramHasNonnullAnnotation(
      Symbol.MethodSymbol symbol, int paramInd, Config config) {
    return hasNonnullAnnotations(getAllAnnotationsForParameter(symbol, paramInd), config);
  }

  /**
   * Works for method parameters defined either in source or in class files
   *
   * @param symbol the method symbol
   * @param paramInd index of the parameter
   * @return all declaration and type-use annotations for the parameter
   */
  public static Stream<? extends AnnotationMirror> getAllAnnotationsForParameter(
      Symbol.MethodSymbol symbol, int paramInd) {
    Symbol.VarSymbol varSymbol = symbol.getParameters().get(paramInd);
    return Stream.concat(
        varSymbol.getAnnotationMirrors().stream(),
        symbol.getRawTypeAttributes().stream()
            .filter(
                t ->
                    t.position.type.equals(TargetType.METHOD_FORMAL_PARAMETER)
                        && t.position.parameter_index == paramInd));
  }

  /**
   * NOTE: this method does not work for getting all annotations of parameters of methods from class
   * files. For that case, use {@link #getAllAnnotationsForParameter(Symbol.MethodSymbol, int)}
   *
   * @param symbol the symbol
   * @return all annotations on the symbol and on the type of the symbol
   */
  public static Stream<? extends AnnotationMirror> getAllAnnotations(Symbol symbol) {
    // for methods, we care about annotations on the return type, not on the method type itself
    Stream<? extends AnnotationMirror> typeUseAnnotations = getTypeUseAnnotations(symbol);
    return Stream.concat(symbol.getAnnotationMirrors().stream(), typeUseAnnotations);
  }

  private static Stream<? extends AnnotationMirror> getTypeUseAnnotations(Symbol symbol) {
    Stream<Attribute.TypeCompound> rawTypeAttributes = symbol.getRawTypeAttributes().stream();
    if (symbol instanceof Symbol.MethodSymbol) {
      // for methods, we want the type-use annotations on the return type
      return rawTypeAttributes.filter((t) -> t.position.type.equals(TargetType.METHOD_RETURN));
    }
    return rawTypeAttributes;
  }

  /**
   * Locates the region member for the enclosing region at the given path. A region member, can be a
   * field, method or {@code null} if path leads to a static block initialization.
   *
   * @param path Path leading to region.
   * @param enclosingClass Enclosing class of the path.
   * @return Region member.
   */
  @Nullable
  public static Symbol locateRegionMemberForSymbolAtPath(
      TreePath path, Symbol.ClassSymbol enclosingClass) {
    // Check if enclosed by a method.
    MethodTree enclosingMethod =
        path.getLeaf() instanceof MethodTree
            ? (MethodTree) path.getLeaf()
            : ASTHelpers.findEnclosingNode(path, MethodTree.class);
    if (enclosingMethod != null) {
      Symbol.MethodSymbol methodSymbol = ASTHelpers.getSymbol(enclosingMethod);
      if (!methodSymbol.isEnclosedBy(enclosingClass)) {
        enclosingMethod = null;
      }
    }
    if (enclosingMethod != null) {
      return ASTHelpers.getSymbol(enclosingMethod);
    }
    // Node is not enclosed by any method, can be a field declaration or enclosed by it.
    Symbol sym = ASTHelpers.getSymbol(path.getLeaf());
    Symbol.VarSymbol fieldSymbol = null;
    if (sym != null && sym.getKind().isField() && sym.isEnclosedBy(enclosingClass)) {
      // Directly on a field declaration.
      fieldSymbol = (Symbol.VarSymbol) sym;
    } else {
      // Can be enclosed by a field declaration tree.
      VariableTree fieldDeclTree = ASTHelpers.findEnclosingNode(path, VariableTree.class);
      if (fieldDeclTree != null) {
        fieldSymbol = ASTHelpers.getSymbol(fieldDeclTree);
      }
    }
    if (fieldSymbol != null && fieldSymbol.isEnclosedBy(enclosingClass)) {
      return fieldSymbol;
    }
    return null;
  }

  /**
   * finds the corresponding functional interface method for a lambda expression or a method
   * reference
   *
   * @param tree the lambda expression or method reference
   * @return the functional interface method
   */
  public static Symbol.MethodSymbol getFunctionalInterfaceMethod(ExpressionTree tree, Types types) {
    Preconditions.checkArgument(
        (tree instanceof LambdaExpressionTree) || (tree instanceof MemberReferenceTree));
    Type funcInterfaceType = ((JCTree.JCFunctionalExpression) tree).type;
    return (Symbol.MethodSymbol) types.findDescriptorSymbol(funcInterfaceType.tsym);
  }

  /**
   * NOTE: THIS SOURCE FILE IS COPIED AND MODIFIED FROM UBER <a
   * href="https://github.com/uber/NullAway">NULLAWAY</a> SOURCE CODE
   *
   * <p>A wrapper for {@link ASTHelpers#hasDirectAnnotationWithSimpleName(Symbol, String)} to avoid
   * binary compatibility issues with new overloads in recent Error Prone versions. NullAway code
   * should only use this method and not call the corresponding ASTHelpers methods directly.
   *
   * <p>TODO: delete this method and switch to ASTHelpers once we can require Error Prone 2.24.0
   *
   * @param sym the symbol
   * @param simpleName the simple name
   * @return {@code true} iff the symbol has a direct annotation with the given simple name
   */
  public static boolean hasDirectAnnotationWithSimpleName(Symbol sym, String simpleName) {
    return ASTHelpers.hasDirectAnnotationWithSimpleName(sym, simpleName);
  }
}
