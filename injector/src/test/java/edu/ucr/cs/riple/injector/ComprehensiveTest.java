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

package edu.ucr.cs.riple.injector;

import static java.util.Collections.singleton;

import edu.ucr.cs.riple.injector.changes.AddMarkerAnnotation;
import edu.ucr.cs.riple.injector.changes.RemoveAnnotation;
import edu.ucr.cs.riple.injector.location.OnField;
import edu.ucr.cs.riple.injector.location.OnMethod;
import edu.ucr.cs.riple.injector.location.OnParameter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ComprehensiveTest extends BaseInjectorTest {

  @Test
  public void addFieldMethodParamTest() {
    String annot = "javax.annotation.Nullable";
    injectorTestHelper
        .addInput(
            "Test.java",
            "package edu.ucr;",
            "public class Test {",
            "   Field foo1;",
            "   Field foo2;",
            "   Field foo3, foo4;",
            "   Object bar1(Object p1, Object p2) {",
            "       return new Object();",
            "   }",
            "   Object bar2(Object p1, Object p2) {",
            "       return new Object();",
            "   }",
            "   Field foo5;",
            "   Object bar3(Object p1, Object p2, Object p3, Object p4) {",
            "       return new Object();",
            "   }",
            "   Object bar4() {",
            "       return new Object();",
            "   }",
            "}")
        .expectOutput(
            "package edu.ucr;",
            "import javax.annotation.Nullable;",
            "public class Test {",
            "   Field foo1;",
            "   @Nullable",
            "   Field foo2;",
            "   @Nullable",
            "   Field foo3, foo4;",
            "   @Nullable",
            "   Object bar1(@Nullable Object p1, Object p2) {",
            "       return new Object();",
            "   }",
            "   Object bar2(Object p1, @Nullable Object p2) {",
            "       return new Object();",
            "   }",
            "   @Nullable",
            "   Field foo5;",
            "   @Nullable",
            "   Object bar3(@Nullable Object p1, @Nullable Object p2, Object p3, @Nullable Object p4) {",
            "       return new Object();",
            "   }",
            "   @Nullable",
            "   Object bar4() {",
            "       return new Object();",
            "   }",
            "}")
        .addChanges(
            new AddMarkerAnnotation(
                new OnField("Test.java", "edu.ucr.Test", singleton("foo2")), annot),
            new AddMarkerAnnotation(
                new OnField("Test.java", "edu.ucr.Test", singleton("foo4")), annot),
            new AddMarkerAnnotation(
                new OnField("Test.java", "edu.ucr.Test", singleton("foo5")), annot),
            new AddMarkerAnnotation(
                new OnMethod("Test.java", "edu.ucr.Test", "bar1(Object, Object)"), annot),
            new AddMarkerAnnotation(
                new OnMethod("Test.java", "edu.ucr.Test", "bar3(Object, Object, Object, Object)"),
                annot),
            new AddMarkerAnnotation(new OnMethod("Test.java", "edu.ucr.Test", "bar4()"), annot),
            new AddMarkerAnnotation(
                new OnParameter("Test.java", "edu.ucr.Test", "bar1(Object, Object)", 0), annot),
            new AddMarkerAnnotation(
                new OnParameter("Test.java", "edu.ucr.Test", "bar2(Object, Object)", 1), annot),
            new AddMarkerAnnotation(
                new OnParameter(
                    "Test.java", "edu.ucr.Test", "bar3(Object, Object, Object, Object)", 0),
                annot),
            new AddMarkerAnnotation(
                new OnParameter(
                    "Test.java", "edu.ucr.Test", "bar3(Object, Object, Object, Object)", 1),
                annot),
            new AddMarkerAnnotation(
                new OnParameter(
                    "Test.java", "edu.ucr.Test", "bar3(Object, Object, Object, Object)", 3),
                annot))
        .start();
  }

  @Test
  public void removeFieldMethodParamTest() {
    String annot = "javax.annotation.Nullable";
    injectorTestHelper
        .addInput(
            "Test.java",
            "package edu.ucr;",
            "import javax.annotation.Nullable;",
            "public class Test {",
            "   Field foo1;",
            "   @Nullable",
            "   Field foo2;",
            "   @Nullable",
            "   Field foo3, foo4;",
            "   @Nullable",
            "   Object bar1(@Nullable Object p1, Object p2) {",
            "       return new Object();",
            "   }",
            "   Object bar2(Object p1, @Nullable Object p2) {",
            "       return new Object();",
            "   }",
            "   @Nullable",
            "   Field foo5;",
            "   @Nullable",
            "   Object bar3(@Nullable Object p1, @Nullable Object p2, Object p3, @Nullable Object p4) {",
            "       return new Object();",
            "   }",
            "   @Nullable",
            "   Object bar4() {",
            "       return new Object();",
            "   }",
            "}")
        .expectOutput(
            "package edu.ucr;",
            "import javax.annotation.Nullable;", // We do not support removing imports
            "public class Test {",
            "   Field foo1;",
            "   Field foo2;",
            "   Field foo3, foo4;",
            "   Object bar1(Object p1, Object p2) {",
            "       return new Object();",
            "   }",
            "   Object bar2(Object p1, Object p2) {",
            "       return new Object();",
            "   }",
            "   Field foo5;",
            "   Object bar3(Object p1, Object p2, Object p3, Object p4) {",
            "       return new Object();",
            "   }",
            "   Object bar4() {",
            "       return new Object();",
            "   }",
            "}")
        .addChanges(
            new RemoveAnnotation(
                new OnField("Test.java", "edu.ucr.Test", singleton("foo2")), annot),
            new RemoveAnnotation(
                new OnField("Test.java", "edu.ucr.Test", singleton("foo4")), annot),
            new RemoveAnnotation(
                new OnField("Test.java", "edu.ucr.Test", singleton("foo5")), annot),
            new RemoveAnnotation(
                new OnMethod("Test.java", "edu.ucr.Test", "bar1(Object, Object)"), annot),
            new RemoveAnnotation(
                new OnMethod("Test.java", "edu.ucr.Test", "bar3(Object, Object, Object, Object)"),
                annot),
            new RemoveAnnotation(new OnMethod("Test.java", "edu.ucr.Test", "bar4()"), annot),
            new RemoveAnnotation(
                new OnParameter("Test.java", "edu.ucr.Test", "bar1(Object, Object)", 0), annot),
            new RemoveAnnotation(
                new OnParameter("Test.java", "edu.ucr.Test", "bar2(Object, Object)", 1), annot),
            new RemoveAnnotation(
                new OnParameter(
                    "Test.java", "edu.ucr.Test", "bar3(Object, Object, Object, Object)", 0),
                annot),
            new RemoveAnnotation(
                new OnParameter(
                    "Test.java", "edu.ucr.Test", "bar3(Object, Object, Object, Object)", 1),
                annot),
            new RemoveAnnotation(
                new OnParameter(
                    "Test.java", "edu.ucr.Test", "bar3(Object, Object, Object, Object)", 3),
                annot))
        .start();
  }
}
