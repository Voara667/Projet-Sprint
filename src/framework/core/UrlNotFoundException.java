package framework.core;

import jakarta.servlet.ServletException;

public class UrlNotFoundException extends ServletException {
    public UrlNotFoundException(String message) {
        super(message);
    }
}
