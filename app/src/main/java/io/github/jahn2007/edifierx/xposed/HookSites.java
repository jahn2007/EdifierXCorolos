package io.github.jahn2007.edifierx.xposed;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedInterface;
@FunctionalInterface
public interface HookSites {
    void bind(String id,Method executable,XposedInterface.Hooker interceptor);
}
