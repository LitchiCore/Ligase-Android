package com.limelight.ligase.pairing

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.math.ec.rfc7748.X25519
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Arrays

internal class AttendedPairingCrypto(
    private val random: SecureRandom = SecureRandom(),
) {
    data class EphemeralKeyPair(val privateKey: ByteArray, val publicKey: ByteArray)

    fun generateKeyPair(): EphemeralKeyPair {
        val privateKey = ByteArray(X25519.SCALAR_SIZE)
        X25519.generatePrivateKey(random, privateKey)
        val publicKey = ByteArray(X25519.POINT_SIZE)
        X25519.generatePublicKey(privateKey, 0, publicKey, 0)
        return EphemeralKeyPair(privateKey, publicKey)
    }

    fun randomBytes(size: Int) = ByteArray(size).also(random::nextBytes)

    fun certificateSha256(certificate: X509Certificate): ByteArray = sha256(certificate.encoded)

    fun derive(
        request: AttendedCreateRequest,
        response: AttendedCreateResponse,
        clientPrivateKey: ByteArray,
        clientNonce: ByteArray,
    ): AttendedPairingMaterial {
        require(response.requestId == request.requestId) { "requestIdMismatch" }
        val hostPublic = AttendedPairingJson.decodeBase64Url(response.hostEphemeralKey, 32)
        val hostNonce = AttendedPairingJson.decodeBase64Url(response.hostNonce, 32)
        val shared = ByteArray(32)
        require(X25519.calculateAgreement(clientPrivateKey, 0, hostPublic, 0, shared, 0)) {
            "invalidSharedSecret"
        }
        require(shared.any { it.toInt() != 0 }) { "invalidSharedSecret" }

        val transcript = AttendedPairingJson.objectOf(
            "request" to request.json(),
            "response" to response.withoutTokenJson(),
        )
        val transcriptHash = sha256(AttendedPairingJson.canonicalBytes(transcript))
        val salt = sha256(clientNonce + hostNonce)
        val info = "Ligase attended pairing v1".toByteArray(StandardCharsets.UTF_8) +
            byteArrayOf(0) + transcriptHash
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(shared, salt, info))
        val pairingKey = ByteArray(32)
        hkdf.generateBytes(pairingKey, 0, pairingKey.size)

        val hmac = HMac(SHA256Digest())
        hmac.init(KeyParameter(pairingKey))
        val sasInput = "Ligase attended pairing SAS v1".toByteArray(StandardCharsets.UTF_8) +
            byteArrayOf(0) + transcriptHash
        hmac.update(sasInput, 0, sasInput.size)
        val digest = ByteArray(hmac.macSize)
        hmac.doFinal(digest, 0)
        val rawCode = base32(digest.copyOfRange(0, 5))
        val code = "${rawCode.substring(0, 4)}-${rawCode.substring(4)}"

        Arrays.fill(shared, 0)
        Arrays.fill(hostPublic, 0)
        Arrays.fill(hostNonce, 0)
        Arrays.fill(salt, 0)
        Arrays.fill(info, 0)
        Arrays.fill(digest, 0)
        return AttendedPairingMaterial(request, response, transcriptHash, pairingKey, code)
    }

    fun createEnvelope(
        pin: CharArray,
        pairingKey: ByteArray,
        transcriptHash: ByteArray,
        nonce: ByteArray = randomBytes(12),
    ): StrictJsonValue.Obj {
        require(pin.size == 4 && pin.all { it in '0'..'9' }) { "invalidPin" }
        val plaintext = AttendedPairingJson.canonicalBytes(
            AttendedPairingJson.objectOf("legacyPin" to AttendedPairingJson.string(String(pin))),
        )
        require(plaintext.size == 20)
        val cipher = ChaCha20Poly1305()
        cipher.init(true, AEADParameters(KeyParameter(pairingKey), 128, nonce, transcriptHash))
        val ciphertext = ByteArray(cipher.getOutputSize(plaintext.size))
        var written = cipher.processBytes(plaintext, 0, plaintext.size, ciphertext, 0)
        written += cipher.doFinal(ciphertext, written)
        require(written == 36)
        Arrays.fill(plaintext, 0)
        return AttendedPairingJson.objectOf(
            "nonce" to AttendedPairingJson.string(AttendedPairingJson.canonicalBase64Url(nonce)),
            "ciphertext" to AttendedPairingJson.string(
                AttendedPairingJson.canonicalBase64Url(ciphertext),
            ),
        )
    }

    fun constantTimeCertificateMatch(
        certificate: X509Certificate,
        expectedBase64Url: String,
    ): Boolean {
        val expected = AttendedPairingJson.decodeBase64Url(expectedBase64Url, 32)
        val actual = certificateSha256(certificate)
        val matches = MessageDigest.isEqual(actual, expected)
        Arrays.fill(expected, 0)
        Arrays.fill(actual, 0)
        return matches
    }

    fun wipe(vararg values: ByteArray?) {
        values.forEach { it?.let { bytes -> Arrays.fill(bytes, 0) } }
    }

    private fun sha256(value: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value)

    private fun base32(bytes: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var buffer = 0
        var bits = 0
        val output = StringBuilder(8)
        bytes.forEach { byte ->
            buffer = (buffer shl 8) or (byte.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                output.append(alphabet[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) output.append(alphabet[(buffer shl (5 - bits)) and 31])
        return output.toString()
    }
}
