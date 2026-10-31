package framework.core;

import framework.core.annotation.Authenticated;
import framework.core.annotation.WebApi;
import java.lang.reflect.Method;

public class RouteInfo {
    private final Class<?> controllerClass;
    private final Method action;
    private final boolean api;
    private final boolean authenticated;

    public RouteInfo(Class<?> controllerClass, Method action) {
        this.controllerClass = controllerClass;
        this.action = action;
        this.api = action.isAnnotationPresent(WebApi.class)
                || controllerClass.isAnnotationPresent(WebApi.class);
        this.authenticated = action.isAnnotationPresent(Authenticated.class)
                || controllerClass.isAnnotationPresent(Authenticated.class);
    }

    public Class<?> getControllerClass() {
        return controllerClass;
    }

    public Method getAction() {
        return action;
    }

    public boolean isApi() {
        return api;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }
}
