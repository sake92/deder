package ba.sake.deder.publish

import java.io.*
import java.util.Base64
import java.io.ByteArrayInputStream
import java.security.Security
import org.bouncycastle.bcpg.{ArmoredOutputStream, HashAlgorithmTags}
import org.bouncycastle.openpgp.*
import org.bouncycastle.openpgp.operator.jcajce.*
import org.bouncycastle.jce.provider.BouncyCastleProvider

import java.nio.file.Files

object PgpSigner {

  // add the provider once at startup
  Security.addProvider(new BouncyCastleProvider())

  def signFile(inputFile: os.Path, secretB64: String, passphrase: Array[Char]): Unit = {
    val signatureFile = inputFile / os.up / s"${inputFile.last}.asc"

    val secretKey = loadSecretKeyFromEnv(secretB64)
    val privateKey = secretKey.extractPrivateKey(
      new JcePBESecretKeyDecryptorBuilder().setProvider("BC").build(passphrase)
    )

    val signatureGenerator = new PGPSignatureGenerator(
      new JcaPGPContentSignerBuilder(secretKey.getPublicKey.getAlgorithm, HashAlgorithmTags.SHA256).setProvider("BC")
    )
    signatureGenerator.init(PGPSignature.BINARY_DOCUMENT, privateKey)

    // detached signature generation
    val out = new ArmoredOutputStream(Files.newOutputStream(signatureFile.toNIO))
    val inputStream = Files.newInputStream(inputFile.toNIO)

    val buffer = new Array[Byte](1024)
    var len = 0
    while ({ len = inputStream.read(buffer); len != -1 }) {
      signatureGenerator.update(buffer, 0, len)
    }
    signatureGenerator.generate().encode(out)
    inputStream.close()
    out.close()
  }


  /** @param base64Key
    *   The Base64 encoded string of your PGP secret key (the output of gpg --export-secret-keys | base64)
    */
  private def loadSecretKeyFromEnv(base64Key: String): PGPSecretKey = {
    val decodedBytes = Base64.getDecoder.decode(base64Key.trim)
    val byteStream = new ByteArrayInputStream(decodedBytes)
    val decoderStream = PGPUtil.getDecoderStream(byteStream)
    val pgpSec = new PGPSecretKeyRingCollection(
      decoderStream,
      new JcaKeyFingerprintCalculator()
    )
    // take the first signing key
    import scala.jdk.CollectionConverters._
    val keyRing = pgpSec.getKeyRings.asScala.collectFirst { case ring: PGPSecretKeyRing =>
      ring.getSecretKeys.asScala.find(_.isSigningKey)
    }.flatten
    keyRing.getOrElse(throw new Exception("No signing key found in the provided env var!"))
  }

}
