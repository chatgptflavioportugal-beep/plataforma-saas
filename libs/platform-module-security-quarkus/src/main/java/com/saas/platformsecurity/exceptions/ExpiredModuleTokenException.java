package com.saas.platformsecurity.exceptions;

/** Token com exp no passado. */
public class ExpiredModuleTokenException extends ModuleSecurityException {
    public ExpiredModuleTokenException(String message) {
        super(message, 401);
    }
}
