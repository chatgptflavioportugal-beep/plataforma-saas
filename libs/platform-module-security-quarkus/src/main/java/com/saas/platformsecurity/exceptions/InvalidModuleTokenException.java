package com.saas.platformsecurity.exceptions;

/** Token malformado, com assinatura inválida ou tokenType diferente de MODULE_ACCESS. */
public class InvalidModuleTokenException extends ModuleSecurityException {
    public InvalidModuleTokenException(String message) {
        super(message, 401);
    }
}
