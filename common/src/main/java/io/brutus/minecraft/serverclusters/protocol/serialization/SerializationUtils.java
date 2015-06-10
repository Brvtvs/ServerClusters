package io.brutus.minecraft.serverclusters.protocol.serialization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Utilities for serializing objects to byte arrays and back.
 */
public class SerializationUtils {

  private SerializationUtils() {}

  /**
   * <p>
   * Serializes an <code>Object</code> to a byte array for storage/serialization.
   * </p>
   *
   * @param obj the object to serialize to bytes
   * @return a byte[] with the converted Serializable
   * @throws IOException if an I/O error occurs while writing stream header or any exception thrown
   *         by the underlying OutputStream.
   * @throws SerializationException (runtime) if the serialization fails
   */
  public static byte[] serialize(Serializable obj) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(512);

    ObjectOutputStream oos = null;
    try {
      oos = new ObjectOutputStream(baos);
      oos.writeObject(obj);

    } catch (IOException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("could not serialize that object");

    } finally {
      try {
        if (oos != null) {
          oos.close();
        }
      } catch (IOException ex) {
        // ignores close exception
      }
    }

    return baos.toByteArray();
  }

  /**
   * Deserializes a single <code>Object</code> from an array of bytes.
   *
   * @param objectData the serialized object, must not be null
   * @return the deserialized object
   * @throws IOException if an I/O error occurs while reading stream header.
   * @throws ClassNotFoundException Class of a serialized object cannot be found.
   * @throws IllegalArgumentException if <code>objectData</code> is <code>null</code>
   * @throws SerializationException (runtime) if the serialization fails
   */
  public static Object deserialize(byte[] objectData) throws IOException, ClassNotFoundException {
    if (objectData == null) {
      throw new IllegalArgumentException("The byte[] must not be null");
    }
    ByteArrayInputStream bais = new ByteArrayInputStream(objectData);
    ObjectInputStream ois = new ObjectInputStream(bais);
    return ois.readObject();
  }

}
