package framework.core;

import jakarta.servlet.ServletException;

public class HttpMethodNotSupportedException extends ServletException {
    public HttpMethodNotSupportedException(String message) {
        super(message);
    }
}
