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

package edu.ucr.cs.riple.scanner.out;

import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Symbol;

public class TrackerNode {
  private final Symbol member;
  private final Symbol.ClassSymbol callerClass;
  private final Symbol callerMember;

  public TrackerNode(Symbol member, TreePath path) {
    this.member = member;
    ClassTree enclosingClass;
    MethodTree enclosingMethod;
    enclosingMethod =
        path.getLeaf() instanceof MethodTree
            ? (MethodTree) path.getLeaf()
            : ASTHelpers.findEnclosingNode(path, MethodTree.class);
    enclosingClass =
        path.getLeaf() instanceof ClassTree
            ? (ClassTree) path.getLeaf()
            : ASTHelpers.findEnclosingNode(path, ClassTree.class);
    if (enclosingClass != null) {
      Symbol.ClassSymbol classSymbol = ASTHelpers.getSymbol(enclosingClass);
      callerClass = classSymbol;
      if (enclosingMethod != null) {
        Symbol.MethodSymbol methodSymbol = ASTHelpers.getSymbol(enclosingMethod);
        if (!methodSymbol.isEnclosedBy(classSymbol)) {
          enclosingMethod = null;
        }
      }
      if (enclosingMethod != null) {
        callerMember = ASTHelpers.getSymbol(enclosingMethod);
      } else {
        // Node is not enclosed by any method, can be a field declaration or enclosed by it.
        Symbol sym = ASTHelpers.getSymbol(path.getLeaf());
        Symbol.VarSymbol fieldSymbol = null;
        if (sym != null && sym.getKind().isField() && sym.isEnclosedBy(classSymbol)) {
          // Directly on a field declaration.
          fieldSymbol = (Symbol.VarSymbol) sym;
        } else {
          // Can be enclosed by a field declaration tree.
          VariableTree fieldDeclTree = ASTHelpers.findEnclosingNode(path, VariableTree.class);
          if (fieldDeclTree != null) {
            fieldSymbol = ASTHelpers.getSymbol(fieldDeclTree);
          }
        }
        if (fieldSymbol != null && fieldSymbol.isEnclosedBy(classSymbol)) {
          callerMember = fieldSymbol;
        } else {
          callerMember = null;
        }
      }
    } else {
      // just to make them final in declaration.
      callerMember = null;
      callerClass = null;
    }
  }

  @Override
  public String toString() {
    if (callerClass == null) {
      return "";
    }
    Symbol enclosingClass = member.enclClass();
    return String.join(
        "\t",
        callerClass.flatName(),
        ((callerMember == null) ? "null" : callerMember.toString()),
        member.toString(),
        ((enclosingClass == null) ? "null" : enclosingClass.flatName()));
  }

  public static String header() {
    return "CALLER_CLASS" + '\t' + "CALLER_MEMBER" + '\t' + "MEMBER" + '\t' + "CALLEE_CLASS";
  }
}
