package edu.ucr.cs.riple.core.metadata.field;

import com.google.common.collect.ImmutableSet;
import edu.ucr.cs.riple.core.metadata.Hashable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Used to store information regarding multiple field declaration statements in classes. */
public class FieldDeclarationInfo implements Hashable {
  /** Set of al fields declared within one statement. */
  public final Set<ImmutableSet<String>> fields;
  /** Flat name of the containing class. */
  public final String clazz;
  /** Path to source file containing this class. */
  public final String pathToSourceFile;

  public FieldDeclarationInfo(String path, String clazz) {
    this.clazz = clazz;
    this.pathToSourceFile = path;
    this.fields = new HashSet<>();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof FieldDeclarationInfo)) {
      return false;
    }
    FieldDeclarationInfo other = (FieldDeclarationInfo) o;
    return clazz.equals(other.clazz);
  }

  /**
   * Calculates hash. This method is used outside this class to calculate the expected hash based on
   * classes value if the actual instance is not available.
   *
   * @param clazz flat name of the containing class.
   * @return Expected hash.
   */
  public static int hash(String clazz) {
    return Objects.hash(clazz);
  }

  @Override
  public int hashCode() {
    return hash(clazz);
  }

  /**
   * Checks if the class contains any inline multiple field declaration statement.
   *
   * @return ture, if the class does not contain any multiple field declaration statement.
   */
  public boolean isEmpty() {
    return this.fields.size() == 0;
  }

  /**
   * Adds a new set multiple field declaration to the existing set.
   *
   * @param collection Set of all fields declared within the same statement.
   */
  public void addNewSetOfFieldDeclarations(ImmutableSet<String> collection) {
    this.fields.add(collection);
  }
}
