package org.zalava.modules.playwright;

final class BrowserOperationException extends RuntimeException {
    BrowserOperationException(String operation, Throwable cause) {
        super(operation, cause);
    }
}
