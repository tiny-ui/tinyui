import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { generateKeyPair, isPublicKey, publicKeyOf, sign, verify } from "../src/keys.ts";

describe("keys", () => {
    const pair = generateKeyPair();

    it("generates a PKCS#8 private key and an X9.63 uncompressed public point", () => {
        assert.match(pair.privateKeyPem, /^-----BEGIN PRIVATE KEY-----\n/);
        const point = Buffer.from(pair.publicKey, "base64");
        assert.equal(point.length, 65);
        assert.equal(point[0], 0x04);
        assert.ok(isPublicKey(pair.publicKey));
        assert.equal(publicKeyOf(pair.privateKeyPem), pair.publicKey);
    });

    it("rejects anything that is not a 65-byte 04-prefixed point", () => {
        assert.ok(!isPublicKey("not base64!"));
        assert.ok(!isPublicKey(Buffer.alloc(65, 2).toString("base64")), "wrong prefix");
        assert.ok(!isPublicKey(Buffer.from(pair.publicKey, "base64").subarray(0, 64).toString("base64")), "wrong length");
    });

    it("signs DER-encoded ECDSA over the exact bytes and verifies with the raw point", () => {
        const data = Buffer.from('{"a":1}\n');
        const signature = sign(pair.privateKeyPem, data);
        assert.equal(Buffer.from(signature, "base64")[0], 0x30, "DER SEQUENCE");
        assert.ok(verify(pair.publicKey, data, signature));
        assert.ok(!verify(pair.publicKey, Buffer.from('{"a":1}'), signature), "one byte less is another message");
        assert.ok(!verify(generateKeyPair().publicKey, data, signature), "another key");
    });
});
