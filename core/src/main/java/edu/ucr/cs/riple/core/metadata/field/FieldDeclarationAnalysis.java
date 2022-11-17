package edu.ucr.cs.riple.core.metadata.field;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.google.common.collect.ImmutableSet;
import edu.ucr.cs.riple.core.Config;
import edu.ucr.cs.riple.core.ModuleInfo;
import edu.ucr.cs.riple.core.metadata.MetaData;
import edu.ucr.cs.riple.injector.Helper;
import edu.ucr.cs.riple.injector.exceptions.TargetClassNotFound;
import edu.ucr.cs.riple.injector.location.OnField;
import edu.ucr.cs.riple.scanner.TypeAnnotatorScanner;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * Detects multiple inline field declarations. An annotation will be injected on top of the field
 * declaration statement, and in inline multiple field declarations that annotation will be
 * considered for all declaring fields. This class is used to detect these cases and adjust the
 * suggested fix instances. (e.g. If we have Object f, i, j; and a Fix suggesting f to be {@code
 * Nullable}, this class will replace that fix with a fix suggesting f, i, and j be {@code
 * Nullable}.)
 */
public class FieldDeclarationAnalysis extends MetaData<FieldDeclarationInfo> {

  /**
   * Output file name. It contains information about all classes existing in source code. Each line
   * has the format: (class-flat-name \t uri to file containing the class). It is generated by
   * {@link TypeAnnotatorScanner} checker.
   */
  public static final String FILE_NAME = "class_info.tsv";

  /**
   * Constructor for {@link FieldDeclarationAnalysis}.
   *
   * @param config Annotator config.
   * @param module Information of the target module.
   */
  public FieldDeclarationAnalysis(Config config, ModuleInfo module) {
    super(config, module.dir.resolve(FILE_NAME));
  }

  /**
   * Constructor for {@link FieldDeclarationAnalysis}. Contents are accumulated from multiple
   * sources.
   *
   * @param config Annotator config.
   * @param modules Information of set of modules.
   */
  public FieldDeclarationAnalysis(Config config, ImmutableSet<ModuleInfo> modules) {
    super(
        config,
        modules.stream()
            .map(info -> info.dir.resolve(FILE_NAME))
            .collect(ImmutableSet.toImmutableSet()));
  }

  @Override
  protected FieldDeclarationInfo addNodeByLine(String[] values) {
    // Class flat name.
    String clazz = values[0];
    // Path to class.
    String path = values[1];
    CompilationUnit tree;
    try {
      tree = StaticJavaParser.parse(new File(path));
      NodeList<BodyDeclaration<?>> members;
      try {
        members = Helper.getTypeDeclarationMembersByFlatName(tree, clazz);
      } catch (TargetClassNotFound notFound) {
        System.err.println(notFound.getMessage());
        return null;
      }
      FieldDeclarationInfo info = new FieldDeclarationInfo(path, clazz);
      members.forEach(
          bodyDeclaration ->
              bodyDeclaration.ifFieldDeclaration(
                  fieldDeclaration -> {
                    NodeList<VariableDeclarator> vars = fieldDeclaration.getVariables();
                    if (vars.size() > 1) { // Check if it is an inline multiple field declaration.
                      info.addNewSetOfFieldDeclarations(
                          vars.stream()
                              .map(NodeWithSimpleName::getNameAsString)
                              .collect(ImmutableSet.toImmutableSet()));
                    }
                  }));
      return info.isEmpty() ? null : info;
    } catch (FileNotFoundException e) {
      return null;
    }
  }

  /**
   * Returns all field names declared within the same declaration statement for any field given in
   * the parameter.
   *
   * @param clazz Flat name of the enclosing class.
   * @param fields Subset of all fields declared within the same statement in the given class.
   * @return Set of all fields declared within that statement.
   */
  public ImmutableSet<String> getInLineMultipleFieldDeclarationsOnField(
      String clazz, Set<String> fields) {
    FieldDeclarationInfo candidate =
        findNodeWithHashHint(node -> node.clazz.equals(clazz), FieldDeclarationInfo.hash(clazz));
    if (candidate == null) {
      // No inline multiple field declarations.
      return ImmutableSet.copyOf(fields);
    }
    Optional<ImmutableSet<String>> inLineGroupFieldDeclaration =
        candidate.fields.stream().filter(group -> !Collections.disjoint(group, fields)).findFirst();
    return inLineGroupFieldDeclaration.orElse(ImmutableSet.copyOf(fields));
  }

  /**
   * Creates a {@link OnField} instance targeting the passed field and class.
   *
   * @param clazz Enclosing class of the field.
   * @param field Field name.
   * @return {@link OnField} instance targeting the passed field and class.
   */
  public OnField getLocationOnField(String clazz, String field) {
    FieldDeclarationInfo candidate =
        findNodeWithHashHint(node -> node.clazz.equals(clazz), FieldDeclarationInfo.hash(clazz));
    if (candidate == null) {
      return null;
    }
    return new OnField(candidate.pathToSourceFile, candidate.clazz, Collections.singleton(field));
  }
}
