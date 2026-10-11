package framework.core;

import jakarta.servlet.ServletException;

public class ParameterBindingException extends ServletException {
    public ParameterBindingException(String message) {
        super(message);
    }
}
