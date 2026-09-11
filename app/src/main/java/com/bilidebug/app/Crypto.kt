package com.bilidebug.app

import android.util.Base64
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

object Crypto {
    /** base64( RSA_PKCS1_v1.5(hash + password) )，对应 Python rsa.encrypt */
    fun rsaEncryptPkcs1(pemPublicKey: String, data: String): String {
        val base64 = pemPublicKey
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\n", "")
            .replace("\r", "")
            .trim()
        val der = Base64.decode(base64, Base64.DEFAULT)
        val keySpec = X509EncodedKeySpec(der)
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKey = keyFactory.generatePublic(keySpec)
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }
}
