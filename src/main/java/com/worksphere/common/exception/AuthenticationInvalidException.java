package com.worksphere.common.exception;

public class AuthenticationInvalidException extends RuntimeException {

    public AuthenticationInvalidException() {
        super("Authentication is no longer valid");
    }
}
