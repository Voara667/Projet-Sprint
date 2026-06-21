package framework.core;

import framework.core.annotation.Controller;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ClassScanner {

    public static List<Class<?>> findAnnotatedControllers(String classesRootPath, ClassLoader loader) {
        List<Class<?>> result = new ArrayList<>();
        File root = new File(classesRootPath);
        if (!root.exists() || !root.isDirectory()) {
            return result;
        }
        walkDirectory(root, "", result, loader);
        return result;
    }

    private static void walkDirectory(File dir, String currentPackage, List<Class<?>> result, ClassLoader loader) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                String nextPackage = currentPackage.isEmpty() ? file.getName() : currentPackage + "." + file.getName();
                walkDirectory(file, nextPackage, result, loader);
            } else if (file.isFile() && file.getName().endsWith(".class")) {
                String className = file.getName().substring(0, file.getName().length() - 6);
                String fullName = currentPackage.isEmpty() ? className : currentPackage + "." + className;
                try {
                    Class<?> clazz = loader.loadClass(fullName);
                    if (clazz.isAnnotationPresent(Controller.class)) {
                        result.add(clazz);
                    }
                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                    // ignore classes that cannot be loaded during scan
                }
            }
        }
    }
}
