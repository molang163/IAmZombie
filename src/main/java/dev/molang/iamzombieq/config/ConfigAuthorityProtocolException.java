package dev.molang.iamzombieq.config;

/**
 * A fail-closed authority handshake violation.
 */
final class ConfigAuthorityProtocolException extends IllegalArgumentException {
    ConfigAuthorityProtocolException(String message) {
        super(message);
    }
}
