package edu.ucr.cs.riple.core.metadata.field;

import edu.ucr.cs.riple.core.metadata.Hashable;
import edu.ucr.cs.riple.injector.location.OnMethod;
import java.util.Objects;

/**
 * Stores field initialization information serialized by NullAway. Each instance contains the
 * initialized field, and the initializer method information.
 */
public class FieldInitializationNode implements Hashable {

  /** Location of the initializer method. */
  private final OnMethod initializerLocation;
  /** Initialized field by the {@link FieldInitializationNode#initializerLocation}. */
  private final String field;

  public FieldInitializationNode(OnMethod initializerLocation, String field) {
    this.initializerLocation = initializerLocation;
    this.field = field;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FieldInitializationNode)) return false;
    FieldInitializationNode that = (FieldInitializationNode) o;
    return initializerLocation.equals(that.initializerLocation) && this.field.equals(that.field);
  }

  /**
   * Gets containing class of the initialization point.
   *
   * @return Fully qualified name of the containing class.
   */
  public String getClassName() {
    return initializerLocation.clazz;
  }

  /**
   * Gets initializer method.
   *
   * @return Signature of the initializer method.
   */
  public String getInitializerMethod() {
    return initializerLocation.method;
  }

  /**
   * Gets uri to src file where the initialization happened.
   *
   * @return URI in string.
   */
  public String getURI() {
    return initializerLocation.uri;
  }

  /**
   * Gets initialized field.
   *
   * @return Initialized field name.
   */
  public String getFieldName() {
    return field;
  }

  /**
   * Calculates hash. This method is used outside this class to calculate the expected hash based on
   * classes value, if the actual instance is not available.
   *
   * @param clazz Full qualified name.
   * @return Expected hash.
   */
  public static int hash(String clazz) {
    return Objects.hash(clazz);
  }

  @Override
  public int hashCode() {
    return hash(initializerLocation.clazz);
  }
}
