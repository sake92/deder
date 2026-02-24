package ba.sake.deder.publish

import java.security.MessageDigest
import java.io.{File, FileInputStream}
import java.nio.file.{Files, Paths}

object Hasher {

  def generateChecksums(file: os.Path): Unit = {
    // maven usually wants both MD5 and SHA-1
    writeChecksum(file, "SHA-1", ".sha1")
    writeChecksum(file, "MD5", ".md5")
  }

  private def writeChecksum(target: os.Path, algorithm: String, extension: String): Unit = {
    val hash = calculateHash(target, algorithm)
    val checksumFile = target / os.up / s"${target.last}${extension}"
    os.write.over(checksumFile, hash.getBytes("UTF-8"))
  }

  private def calculateHash(file: os.Path, algorithm: String): String = {
    val digest = MessageDigest.getInstance(algorithm)
    val buffer = new Array[Byte](8192)
    var read = 0
    val fis = Files.newInputStream(file.toNIO)
    while ({ read = fis.read(buffer); read != -1 }) {
      digest.update(buffer, 0, read)
    }
    fis.close()
    // Convert byte array to Hex String
    digest.digest().map("%02x".format(_)).mkString
  }
}