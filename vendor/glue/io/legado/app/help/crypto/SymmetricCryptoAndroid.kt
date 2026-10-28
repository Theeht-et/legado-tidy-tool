package io.legado.app.help.crypto

import androidx.annotation.Keep
import cn.hutool.core.codec.Base64
import cn.hutool.core.util.HexUtil
import cn.hutool.crypto.KeyUtil
import cn.hutool.crypto.symmetric.SymmetricCrypto
import io.legado.app.utils.EncoderUtils
import io.legado.app.utils.isHex
import java.io.InputStream
import java.nio.charset.Charset
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@Keep
class SymmetricCryptoAndroid private constructor(
    algorithm: String,
    key: SecretKey?,
    @Suppress("UNUSED_PARAMETER") marker: Unit,
) : SymmetricCrypto(algorithm, key) {

    constructor(algorithm: String, key: ByteArray?) : this(
        normalize(algorithm),
        key?.let { keyOf(normalize(algorithm), it) },
        Unit,
    )

    override fun encryptBase64(data: ByteArray): String {
        return EncoderUtils.base64Encode(encrypt(data))
    }

    override fun encryptBase64(data: String, charset: String?): String {
        return EncoderUtils.base64Encode(encrypt(data, charset))
    }

    override fun encryptBase64(data: String, charset: Charset?): String {
        return EncoderUtils.base64Encode(encrypt(data, charset))
    }

    override fun encryptBase64(data: String): String {
        return EncoderUtils.base64Encode(encrypt(data))
    }

    override fun encryptBase64(data: InputStream): String {
        return EncoderUtils.base64Encode(encrypt(data))
    }

    override fun decrypt(data: String): ByteArray {
        val bytes = if (data.isHex()) {
            HexUtil.decodeHex(data)
        } else {
            Base64.decode(data)
        }
        return decrypt(bytes)
    }

    private companion object {
        /**
         * Android 的 provider 认 PKCS7Padding，JVM 只认 PKCS5Padding；两者
         * 对块密码等价，这里统一映射，否则这类源会直接 NoSuchAlgorithmException。
         */
        fun normalize(algorithm: String): String =
            algorithm.replace("PKCS7Padding", "PKCS5Padding", ignoreCase = true)

        /**
         * Hutool 的 byte[] 构造器会把完整 transformation（如
         * `AES/CBC/PKCS5Padding`）当成 SecretKeySpec 的算法名；JVM 的 AES
         * 只认 `AES`/`Rijndael`，会抛 `Wrong algorithm`。
         * 其它算法（DES/PBE）仍交给 Hutool 自己的 key 生成逻辑。
         */
        fun keyOf(algorithm: String, key: ByteArray): SecretKey {
            val main = algorithm.substringBefore('/')
            return if (main.equals("AES", ignoreCase = true)) {
                SecretKeySpec(key, "AES")
            } else {
                KeyUtil.generateKey(algorithm, key)
            }
        }
    }
}
