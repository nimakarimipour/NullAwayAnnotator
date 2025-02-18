/*
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

import static com.google.errorprone.BugPattern.SeverityLevel.SUGGESTION;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.ErrorProneFlags;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import edu.ucr.cs.riple.scanner.generatedcode.CodeAnnotationInfo;
import edu.ucr.cs.riple.scanner.out.ClassRecord;
import edu.ucr.cs.riple.scanner.out.ImpactedRegion;
import edu.ucr.cs.riple.scanner.out.MethodRecord;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.ElementKind;

@AutoService(BugChecker.class)
@BugPattern(
    name = "AnnotatorScanner",
    altNames = {"TypeBasedStructureSerializer"},
    summary = "Serializes type-based metadata regarding code structure.",
    tags = BugPattern.StandardTags.STYLE,
    severity = SUGGESTION)
@SuppressWarnings("BugPatternNaming")
public class AnnotatorScanner extends BugChecker
    implements BugChecker.MethodInvocationTreeMatcher,
        BugChecker.MemberSelectTreeMatcher,
        BugChecker.MethodTreeMatcher,
        BugChecker.IdentifierTreeMatcher,
        BugChecker.VariableTreeMatcher,
        BugChecker.NewClassTreeMatcher,
        BugChecker.ClassTreeMatcher,
        BugChecker.LambdaExpressionTreeMatcher,
        BugChecker.MemberReferenceTreeMatcher {

  /**
   * Scanner context to store the state of the checker. Could not use {@link VisitorState#context}
   * included in Error Prone. See {@link ScannerContext} class for more info.
   */
  private final ScannerContext context;

  public AnnotatorScanner() {
    this.context = new ScannerContext(new DummyOptionsConfig());
  }

  public AnnotatorScanner(ErrorProneFlags flags) {
    this.context = new ScannerContext(new ErrorProneCLIFlagsConfig(flags));
  }

  @Override
  public Description matchClass(ClassTree classTree, VisitorState visitorState) {
    if (!context.getConfig().isActive()) {
      return Description.NO_MATCH;
    }
    Symbol.ClassSymbol classSymbol = ASTHelpers.getSymbol(classTree);
    if (classSymbol == null || isInGeneratedClass(classSymbol, visitorState.context)) {
      return Description.NO_MATCH;
    }
    context
        .getConfig()
        .getSerializer()
        .serializeClassRecord(
            new ClassRecord(classSymbol, visitorState.getPath().getCompilationUnit()));
    return Description.NO_MATCH;
  }

  @Override
  public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
    Config config = context.getConfig();
    if (!config.isActive()) {
      return Description.NO_MATCH;
    }
    Symbol.MethodSymbol methodSymbol = ASTHelpers.getSymbol(tree);
    if (methodSymbol == null || isInGeneratedClass(methodSymbol, state.context)) {
      return Description.NO_MATCH;
    }
    config
        .getSerializer()
        .serializeImpactedRegionForMethod(
            new ImpactedRegion(config, methodSymbol, state.getPath()));
    return Description.NO_MATCH;
  }

  @Override
  public Description matchNewClass(NewClassTree tree, VisitorState state) {
    Config config = context.getConfig();
    if (!config.isActive()) {
      return Description.NO_MATCH;
    }
    Symbol.MethodSymbol methodSymbol = ASTHelpers.getSymbol(tree);
    if (methodSymbol == null) {
      throw new RuntimeException("not expecting unresolved method here");
    }
    if (methodSymbol.owner.enclClass().getSimpleName().isEmpty()
        || isInGeneratedClass(methodSymbol, state.context)) {
      // An anonymous class cannot declare its own constructors, so we do not need to serialize it.
      return Description.NO_MATCH;
    }
    config
        .getSerializer()
        .serializeImpactedRegionForMethod(
            new ImpactedRegion(config, methodSymbol, state.getPath()));
    return Description.NO_MATCH;
  }

  @Override
  public Description matchMethod(MethodTree tree, VisitorState state) {
    Config config = context.getConfig();
    if (!context.getConfig().isActive()) {
      return Description.NO_MATCH;
    }
    Symbol.MethodSymbol methodSymbol = ASTHelpers.getSymbol(tree);
    if (methodSymbol == null || isInGeneratedClass(methodSymbol, state.context)) {
      return Description.NO_MATCH;
    }
    serializeSymIfNonnull(methodSymbol);
    MethodRecord methodRecord = MethodRecord.findOrCreate(methodSymbol, context);
    methodRecord.findParent(state, context);
    methodRecord.collectMethodAnnotations();
    methodRecord.setURI(state);
    List<Boolean> paramAnnotations = new ArrayList<>();
    for (int i = 0; i < methodSymbol.getParameters().size(); i++) {
      paramAnnotations.add(SymbolUtil.paramHasNullableAnnotation(methodSymbol, i, config));
    }
    methodRecord.setAnnotationParameterFlags(paramAnnotations);
    config.getSerializer().serializeMethodRecord(methodRecord);
    return Description.NO_MATCH;
  }

  @Override
  public Description matchVariable(VariableTree tree, VisitorState state) {
    if (!context.getConfig().isActive()) {
      return Description.NO_MATCH;
    }
    Symbol.VarSymbol varSymbol = ASTHelpers.getSymbol(tree);
    if (varSymbol == null || isInGeneratedClass(varSymbol, state.context)) {
      return Description.NO_MATCH;
    }
    serializeSymIfField(ASTHelpers.getSymbol(tree.getInitializer()), state);
    serializeSymIfNonnull(varSymbol);
    return Description.NO_MATCH;
  }

  @Override
  public Description matchIdentifier(IdentifierTree tree, VisitorState state) {
    if (!context.getConfig().isActive()) {
      return Description.NO_MATCH;
    }
    Symbol symbol = ASTHelpers.getSymbol(tree);
    if (symbol == null || isInGeneratedClass(symbol, state.context)) {
      return Description.NO_MATCH;
    }
    serializeSymIfField(symbol, state);
    return Description.NO_MATCH;
  }

  @Override
  public Description matchMemberSelect(MemberSelectTree tree, VisitorState state) {
    if (!context.getConfig().isActive()) {
      return Description.NO_MATCH;
    }
    Symbol symbol = ASTHelpers.getSymbol(tree);
    if (symbol == null || isInGeneratedClass(symbol, state.context)) {
      return Description.NO_MATCH;
    }
    serializeSymIfField(symbol, state);
    return Description.NO_MATCH;
  }

  @Override
  public Description matchLambdaExpression(
      LambdaExpressionTree lambdaExpressionTree, VisitorState visitorState) {
    Config config = context.getConfig();
    if (!config.isActive()) {
      return Description.NO_MATCH;
    }
    // for e -> Foo.bar(e), assume that method "baz()" has been overridden. Then the containing
    // method for this lambda is an impacted region for "baz()".  The call to "Foo.bar" is handled
    // when scanning the body of the lambda.
    serializeImpactedRegionForFunctionalInterface(config, lambdaExpressionTree, visitorState);
    return Description.NO_MATCH;
  }

  @Override
  public Description matchMemberReference(
      MemberReferenceTree memberReferenceTree, VisitorState visitorState) {
    Config config = context.getConfig();
    if (!config.isActive()) {
      return Description.NO_MATCH;
    }
    // for Foo::bar, which is shorthand for e -> Foo.bar(e), assume that method "baz()" has been
    // overridden. We need to serialize the impacted region (leaf of path in visitor state)
    // for both "baz()" and also the called method "bar()".
    // serialize the overridden method: "baz()"
    serializeImpactedRegionForFunctionalInterface(config, memberReferenceTree, visitorState);
    if (memberReferenceTree instanceof JCTree.JCMemberReference) {
      Symbol calledMethod = ((JCTree.JCMemberReference) memberReferenceTree).sym;
      if (calledMethod == null || isInGeneratedClass(calledMethod, visitorState.context)) {
        return Description.NO_MATCH;
      }
      if (calledMethod instanceof Symbol.MethodSymbol) {
        // serialize the called method: "bar()"
        context
            .getConfig()
            .getSerializer()
            .serializeImpactedRegionForMethod(
                new ImpactedRegion(config, calledMethod, visitorState.getPath()));
      }
    }
    return Description.NO_MATCH;
  }

  /**
   * Serializes a field usage if the received symbol is a field.
   *
   * @param symbol Received symbol.
   * @param state Error prone visitor state.
   */
  private void serializeSymIfField(Symbol symbol, VisitorState state) {
    if (symbol != null && symbol.getKind() == ElementKind.FIELD) {
      context
          .getConfig()
          .getSerializer()
          .serializeFieldAccessRecord(
              new ImpactedRegion(context.getConfig(), symbol, state.getPath()));
    }
  }

  /**
   * Serializes the symbol if annotated with explicit {@code @Nonnull} annotations.
   *
   * @param symbol Given symbol to check its annotations.
   */
  private void serializeSymIfNonnull(Symbol symbol) {
    if (symbol instanceof Symbol.MethodSymbol) {
      Symbol.MethodSymbol methodSymbol = (Symbol.MethodSymbol) symbol;
      for (int i = 0; i < methodSymbol.getParameters().size(); i++) {
        if (SymbolUtil.paramHasNonnullAnnotation(methodSymbol, i, context.getConfig())) {
          context
              .getConfig()
              .getSerializer()
              .serializeNonnullSym(methodSymbol.getParameters().get(i));
        }
      }
    }
    if ((symbol.getKind().equals(ElementKind.METHOD) || symbol.getKind().isField())
        && SymbolUtil.hasNonnullAnnotations(symbol, context.getConfig())) {
      context.getConfig().getSerializer().serializeNonnullSym(symbol);
    }
  }

  /**
   * Serializes the impacted region for a functional interface. This method locates the overriden
   * method from the passed functional interface tree and serializes the impacted region for that
   * method.
   *
   * @param tree Given tree.
   * @param state Visitor State.
   */
  private static void serializeImpactedRegionForFunctionalInterface(
      Config config, ExpressionTree tree, VisitorState state) {
    Symbol.MethodSymbol methodSym = SymbolUtil.getFunctionalInterfaceMethod(tree, state.getTypes());
    if (methodSym == null) {
      System.err.println(
          "Expected a nonnull method symbol for functional interface:"
              + state.getSourceForNode(tree)
              + ", at path: "
              + state.getPath().getCompilationUnit().getSourceFile().toUri()
              + ", but received null.");
      return;
    }
    config
        .getSerializer()
        .serializeImpactedRegionForMethod(new ImpactedRegion(config, methodSym, state.getPath()));
  }

  /**
   * Checks if the given symbol is in a generated class.
   *
   * @param symbol Given symbol.
   * @param context Error prone context.
   * @return True if the symbol is in a generated class, false otherwise.
   */
  private boolean isInGeneratedClass(Symbol symbol, Context context) {
    CodeAnnotationInfo codeAnnotationInfo = CodeAnnotationInfo.instance(context);
    return codeAnnotationInfo.isInGeneratedClass(symbol);
  }
}
