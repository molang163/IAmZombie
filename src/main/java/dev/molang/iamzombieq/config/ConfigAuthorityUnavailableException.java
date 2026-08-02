package dev.molang.iamzombieq.config;

/**
 * Raised when client code attempts to read server authority before the current
 * connection has completed its configuration epoch.
 */
final class ConfigAuthorityUnavailableException extends IllegalStateException {
    ConfigAuthorityUnavailableException(String message) {
        super(message);
    }
}
