package edu.ucr.cs.riple.core.metadata.graph;

import com.google.common.collect.Sets;
import edu.ucr.cs.riple.core.FixType;
import edu.ucr.cs.riple.core.Report;
import edu.ucr.cs.riple.core.metadata.field.FieldDeclarationAnalysis;
import edu.ucr.cs.riple.core.metadata.index.Bank;
import edu.ucr.cs.riple.core.metadata.index.Fix;
import edu.ucr.cs.riple.core.metadata.method.MethodInheritanceTree;
import edu.ucr.cs.riple.core.metadata.trackers.Region;
import edu.ucr.cs.riple.core.metadata.trackers.RegionTracker;
import edu.ucr.cs.riple.injector.Location;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Node {

  /** Fix to process */
  public final Fix root;

  /** Tree of all fixes connecting to root. */
  public final Set<Fix> tree;

  public final Set<Region> regions;

  public Set<Fix> triggered;

  public int id;

  /** Effect of applying containing location */
  public int effect;

  /** if <code>true</code>, set of triggered has been updated */
  public boolean changed;

  public Report report;

  /** Regions where original errors reported, all are for root node */
  private Set<Region> rootSource;

  public Node(Fix root) {
    this.regions = new HashSet<>();
    this.root = root;
    this.triggered = new HashSet<>();
    this.effect = 0;
    this.tree = Sets.newHashSet(root);
  }

  public void setRootSource(Bank<Fix> fixBank, FieldDeclarationAnalysis analysis) {
    Set<String> fieldGroup =
        root.kind.equals(FixType.FIELD.name)
            ? analysis.getInLineMultipleFieldDeclarationsOnField(root)
            : Collections.emptySet();
    this.rootSource =
        fixBank.getRegionsForFixes(
            fix -> {
              if (fix.kind.equals(FixType.FIELD.name)) {
                return fix.clazz.equals(root.clazz)
                    && fix.uri.equals(root.uri)
                    && fieldGroup.contains(fix.variable);
              } else {
                return fix.equals(root);
              }
            });
  }

  public void updateRegions(RegionTracker tracker) {
    this.regions.clear();
    this.regions.addAll(this.rootSource);
    tree.forEach(fix -> regions.addAll(tracker.getRegions(fix)));
    tree.stream()
        .filter(fix -> fix.kind.equals(FixType.PARAMETER.name) && fix.isModifyingConstructor())
        .forEach(fix -> regions.add(new Region("null", fix.clazz)));
  }

  public boolean hasConflictInRegions(Node other) {
    return !Collections.disjoint(other.regions, this.regions);
  }

  public void updateStatus(
      int effect,
      Set<Fix> fixesInOneRound,
      List<Fix> triggered,
      MethodInheritanceTree mit,
      FieldDeclarationAnalysis fieldDeclarationAnalysis) {
    triggered.addAll(generateSubMethodParameterInheritanceFixes(mit, fixesInOneRound));
    updateTriggered(triggered, fieldDeclarationAnalysis);
    final int[] numberOfSuperMethodsAnnotatedOutsideTree = {0};
    this.tree
        .stream()
        .filter(fix -> fix.kind.equals(FixType.METHOD.name))
        .map(fix -> mit.getClosestSuperMethod(fix.method, fix.clazz))
        .filter(node -> node != null && !node.hasNullableAnnotation)
        .forEach(
            node -> {
              if (tree.stream()
                  .anyMatch(
                      fix ->
                          fix.kind.equals(FixType.METHOD.name)
                              && fix.method.equals(node.method)
                              && fix.clazz.equals(node.clazz))) {
                return;
              }
              if (fixesInOneRound
                  .stream()
                  .anyMatch(
                      fix ->
                          fix.kind.equals(FixType.METHOD.name)
                              && fix.method.equals(node.method)
                              && fix.clazz.equals(node.clazz))) {
                numberOfSuperMethodsAnnotatedOutsideTree[0]++;
              }
            });
    this.effect = effect + numberOfSuperMethodsAnnotatedOutsideTree[0];
  }

  public void updateTriggered(List<Fix> fixes, FieldDeclarationAnalysis analysis) {
    int sizeBefore = this.triggered.size();
    this.triggered.addAll(
        fixes
            .stream()
            .filter(fix -> !fixTargetsSameElement(triggered, fix, analysis))
            .collect(Collectors.toSet()));
    int sizeAfter = this.triggered.size();
    changed = (changed || (sizeAfter != sizeBefore));
  }

  /**
   * Checks for fixes targeting inline multiple field declarations and returns true, if the fix in
   * tree is already targeting that statement.
   *
   * @param set set of fixes.
   * @param target target fix.
   * @param analysis field declaration analysis.
   * @return returns true, if the fix is targeting an element which is already covered in tree.
   */
  private boolean fixTargetsSameElement(
      Set<Fix> set, Fix target, FieldDeclarationAnalysis analysis) {
    if (target.kind.equals(FixType.FIELD.name)) {
      Set<String> group = analysis.getInLineMultipleFieldDeclarationsOnField(target);
      return set.stream()
          .anyMatch(
              observed ->
                  observed.kind.equals(FixType.FIELD.name)
                      && observed.clazz.equals(target.clazz)
                      && group.contains(observed.variable));
    } else {
      return false;
    }
  }

  /**
   * Generates suggested fixes due to making a parameter {@code Nullable} for all overriding
   * methods.
   *
   * @param mit Method Inheritance Tree.
   * @return List of Fixes
   */
  private List<Fix> generateSubMethodParameterInheritanceFixes(
      MethodInheritanceTree mit, Set<Fix> fixesInOneRound) {
    return tree.stream()
        .filter(fix -> fix.kind.equals(FixType.PARAMETER.name))
        .flatMap(
            (Function<Fix, Stream<Fix>>)
                fix -> {
                  int index = Integer.parseInt(fix.index);
                  return mit.getSubMethods(fix.method, fix.clazz, false)
                      .stream()
                      .filter(
                          methodNode ->
                              (index < methodNode.annotFlags.length
                                  && !methodNode.annotFlags[index]))
                      .map(
                          node -> {
                            Location location =
                                new Location(
                                    fix.annotation,
                                    fix.method,
                                    node.parameterNames[index],
                                    FixType.PARAMETER.name,
                                    node.clazz,
                                    node.uri,
                                    "true");
                            location.index = String.valueOf(index);
                            return new Fix(
                                location, "WRONG_OVERRIDE_PARAM", node.clazz, node.method);
                          });
                })
        .filter(fix -> !fixesInOneRound.contains(fix))
        .collect(Collectors.toList());
  }

  public void mergeTriggered() {
    this.tree.addAll(this.triggered);
    this.triggered.clear();
  }

  @Override
  public int hashCode() {
    return getHash(root);
  }

  public static int getHash(Fix fix) {
    return Objects.hash(fix.variable, fix.index, fix.clazz, fix.method);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Node)) return false;
    Node node = (Node) o;
    return root.equals(node.root);
  }
}
