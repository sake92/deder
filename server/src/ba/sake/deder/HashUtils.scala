package ba.sake.deder

import java.nio.charset.StandardCharsets
import org.apache.commons.codec.digest.Blake3;
import org.apache.commons.codec.binary.Hex;

object HashUtils {
  def hash(bytes: Array[Byte]): Array[Byte] = {
    val hasher = Blake3.initHash()
    hasher.update(bytes)
    val hashBytes = Array.ofDim[Byte](32)
    hasher.doFinalize(hashBytes)
    hashBytes
  }

  def hashStr(bytes: Array[Byte]): String =
    new String(Hex.encodeHex(hash(bytes)))

  def hashStr(inputStream: java.io.InputStream): String = {
    val hasher = Blake3.initHash()
    val buffer = Array.ofDim[Byte](8192)
    var read = -1

    while ({ read = inputStream.read(buffer); read != -1 }) {
      hasher.update(buffer, 0, read)
    }
    val hashBytes = Array.ofDim[Byte](32)
    hasher.doFinalize(hashBytes)
    new String(Hex.encodeHex(hashBytes))
  }

  def hashStr(data: String): String =
    val hashBytes = hash(data.getBytes(StandardCharsets.UTF_8))
    new String(Hex.encodeHex(hashBytes))

}
