package ba.sake.deder

import java.nio.charset.StandardCharsets
import org.apache.commons.codec.digest.Blake3;
import org.apache.commons.codec.binary.Hex;

object HashUtils {
  def hash(bytes: Array[Byte]): Array[Byte] = {
    val hasher = Blake3.initHash()
    hasher.update(bytes)
    val hashRes = Array.ofDim[Byte](32)
    hasher.doFinalize(hashRes)
    hashRes
  }

  def hashStr(bytes: Array[Byte]):String =
    new String(Hex.encodeHex(hash(bytes)))
  

  def hashStr(data: String): String = 
    val hashBytes = hash(data.getBytes(StandardCharsets.UTF_8))
    new String(Hex.encodeHex(hashBytes))

}
