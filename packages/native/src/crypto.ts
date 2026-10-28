// docs/native-api.md §16: platform crypto, so neither the algorithm nor a JS library ships in a hot-updatable package.
import { internal } from "tinyui-core";

export interface RsaEncryptOptions {
    /** The public key as X.509 SubjectPublicKeyInfo DER, base64 (what `-----BEGIN PUBLIC KEY-----` wraps). */
    publicKey: string;
}

/**
 * RSA public-key encryption with PKCS#1 v1.5 padding; resolves with the ciphertext in base64. [text] is encoded as
 * UTF-8 and may be at most the key size in bytes minus 11. The padding is random: the same input never gives the
 * same ciphertext twice. Rejects E_INVALID for a key it cannot read or a text too long for it.
 */
export function rsaEncrypt(text: string, options: RsaEncryptOptions): Promise<string> {
    return internal.call("crypto.rsaEncrypt", { text, publicKey: options.publicKey });
}
