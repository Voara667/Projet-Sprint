package framework.core;

import jakarta.servlet.ServletException;

public class DuplicateRouteException extends ServletException {
    public DuplicateRouteException(String message) {
        super(message);
    }
}
