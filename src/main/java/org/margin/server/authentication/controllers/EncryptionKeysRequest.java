package org.margin.server.authentication.controllers;

public record EncryptionKeysRequest(String publicKey, String encryptedPrivateKey, String salt, String iv) {
}