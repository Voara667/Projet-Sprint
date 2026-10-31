package framework.core;

import framework.core.annotation.Authenticated;
import framework.core.annotation.Roles;
import framework.core.annotation.WebApi;
import java.lang.reflect.Method;

public class RouteInfo {
    private final Class<?> controllerClass;
    private final Method action;
    private final boolean api;
    private final boolean authenticated;
    private final boolean roleRestricted;
    private final String[] roles;

    public RouteInfo(Class<?> controllerClass, Method action) {
        this.controllerClass = controllerClass;
        this.action = action;
        this.api = action.isAnnotationPresent(WebApi.class)
                || controllerClass.isAnnotationPresent(WebApi.class);
        Roles methodRoles = action.getAnnotation(Roles.class);
        Roles classRoles = controllerClass.getAnnotation(Roles.class);
        Roles effectiveRoles = methodRoles != null ? methodRoles : classRoles;
        this.roleRestricted = effectiveRoles != null;
        this.roles = effectiveRoles == null ? new String[0] : effectiveRoles.value().clone();
        this.authenticated = action.isAnnotationPresent(Authenticated.class)
                || controllerClass.isAnnotationPresent(Authenticated.class)
                || roleRestricted;
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

    public boolean isRoleRestricted() {
        return roleRestricted;
    }

    public String[] getRoles() {
        return roles.clone();
    }
}
