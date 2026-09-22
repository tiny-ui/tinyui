import { createPrivateKey, createPublicKey, generateKeyPairSync, sign as cryptoSign, verify as cryptoVerify, type KeyObject } from "node:crypto";

// ECDSA P-256 + SHA-256; keys and signatures in the formats docs/updates.md §7 fixes for all three sides.

export interface KeyPair {
    /** PKCS#8 PEM; stays with the publisher, goes to `tinyui bundle --signing-key`. */
    privateKeyPem: string;
    /** X9.63 uncompressed point (`04‖X‖Y`, 65 bytes) base64: `tinyui.config.json`, the manifest and the server all take this. */
    publicKey: string;
}

export function generateKeyPair(): KeyPair {
    const { privateKey, publicKey } = generateKeyPairSync("ec", { namedCurve: "P-256" });
    return { privateKeyPem: privateKey.export({ type: "pkcs8", format: "pem" }) as string, publicKey: rawPoint(publicKey) };
}

export function publicKeyOf(privateKeyPem: string): string {
    return rawPoint(createPublicKey(createPrivateKey(privateKeyPem)));
}

/** Canonical base64 of a 65-byte `04‖X‖Y` whose coordinates are a point on P-256. */
export function isPublicKey(publicKey: string): boolean {
    try {
        fromRawPoint(publicKey);
        return true;
    } catch {
        return false;
    }
}

/** DER-encoded ECDSA signature over [data], base64. */
export function sign(privateKeyPem: string, data: Uint8Array): string {
    return cryptoSign("sha256", data, { key: createPrivateKey(privateKeyPem), dsaEncoding: "der" }).toString("base64");
}

export function verify(publicKey: string, data: Uint8Array, signature: string): boolean {
    return cryptoVerify("sha256", data, { key: fromRawPoint(publicKey), dsaEncoding: "der" }, Buffer.from(signature, "base64"));
}

function rawPoint(key: KeyObject): string {
    const { x, y } = key.export({ format: "jwk" }) as { x: string; y: string };
    return Buffer.concat([Buffer.from([0x04]), Buffer.from(x, "base64url"), Buffer.from(y, "base64url")]).toString("base64");
}

function fromRawPoint(publicKey: string): KeyObject {
    const bytes = Buffer.from(publicKey, "base64");
    if (bytes.length !== 65 || bytes[0] !== 0x04 || bytes.toString("base64") !== publicKey) {
        throw new Error("public key is not a P-256 uncompressed point in base64");
    }
    const x = bytes.subarray(1, 33).toString("base64url");
    const y = bytes.subarray(33, 65).toString("base64url");
    return createPublicKey({ key: { kty: "EC", crv: "P-256", x, y }, format: "jwk" });
}
