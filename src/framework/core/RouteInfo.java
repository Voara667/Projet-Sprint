package framework.core;

import java.lang.reflect.Method;

public class RouteInfo {
    private final Class<?> controllerClass;
    private final Method action;

    public RouteInfo(Class<?> controllerClass, Method action) {
        this.controllerClass = controllerClass;
        this.action = action;
    }

    public Class<?> getControllerClass() {
        return controllerClass;
    }

    public Method getAction() {
        return action;
    }
}
