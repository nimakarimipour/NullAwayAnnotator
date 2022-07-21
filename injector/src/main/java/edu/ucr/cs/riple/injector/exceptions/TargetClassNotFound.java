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

package edu.ucr.cs.riple.injector.exceptions;

import com.github.javaparser.ast.Node;

/** Exception class which should be thrown when a target class on a {@link Node} is not found. */
public class TargetClassNotFound extends Exception {

  static final String REPORT_MESSAGE =
      "If you do see the class on the source code, please report this bug at: https://github.com/nimakarimipour/NullAwayAnnotator/issues. Thank you!";

  public TargetClassNotFound(String type, String name, Node cursor) {
    super(
        "Could not find class of type: "
            + type
            + " with name: "
            + name
            + " on Cursor:\n"
            + cursor
            + "\n"
            + REPORT_MESSAGE);
  }
}
