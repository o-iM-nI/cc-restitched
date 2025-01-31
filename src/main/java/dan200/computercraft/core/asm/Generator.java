/*
 * This file is part of ComputerCraft - http://www.computercraft.info
 * Copyright Daniel Ratcliffe, 2011-2021. Do not distribute without permission.
 * Send enquiries to dratcliffe@gmail.com
 */
package dan200.computercraft.core.asm;

import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_8;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.primitives.Primitives;
import com.google.common.reflect.TypeToken;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public final class Generator<T> {
    private static final AtomicInteger METHOD_ID = new AtomicInteger();

    private static final String METHOD_NAME = "apply";
    private static final String[] EXCEPTIONS = new String[] {Type.getInternalName(LuaException.class)};

    private static final String INTERNAL_METHOD_RESULT = Type.getInternalName(MethodResult.class);
    private static final String DESC_METHOD_RESULT = Type.getDescriptor(MethodResult.class);

    private static final String INTERNAL_ARGUMENTS = Type.getInternalName(IArguments.class);
    private static final String DESC_ARGUMENTS = Type.getDescriptor(IArguments.class);

    private final Class<T> base;
    private final List<Class<?>> context;

    private final String[] interfaces;
    private final String methodDesc;

    private final Function<T, T> wrap;
    private final LoadingCache<Method, Optional<T>> methodCache = CacheBuilder.newBuilder()
                                                                              .build(CacheLoader.from(catching(this::build, Optional.empty())));
    private final LoadingCache<Class<?>, List<NamedMethod<T>>> classCache = CacheBuilder.newBuilder()
                                                                                        .build(CacheLoader.from(catching(this::build, Collections.emptyList())));

    Generator(Class<T> base, List<Class<?>> context, Function<T, T> wrap) {
        this.base = base;
        this.context = context;
        this.interfaces = new String[] {Type.getInternalName(base)};
        this.wrap = wrap;

        StringBuilder methodDesc = new StringBuilder().append("(Ljava/lang/Object;");
        for (Class<?> klass : context) {
            methodDesc.append(Type.getDescriptor(klass));
        }
        methodDesc.append(DESC_ARGUMENTS)
                  .append(")")
                  .append(DESC_METHOD_RESULT);
        this.methodDesc = methodDesc.toString();
    }

    @Nonnull
    public List<NamedMethod<T>> getMethods(@Nonnull Class<?> klass) {
        try {
            return this.classCache.get(klass);
        } catch (ExecutionException e) {
            ComputerCraft.log.error("Error getting methods for {}.", klass.getName(), e.getCause());
            return Collections.emptyList();
        }
    }

    @Nonnull
    private List<NamedMethod<T>> build(Class<?> klass) {
        ArrayList<NamedMethod<T>> methods = null;
        for (Method method : klass.getMethods()) {
            LuaFunction annotation = method.getAnnotation(LuaFunction.class);
            if (annotation == null) {
                continue;
            }

            if (Modifier.isStatic(method.getModifiers())) {
                ComputerCraft.log.warn("LuaFunction method {}.{} should be an instance method.", method.getDeclaringClass(), method.getName());
                continue;
            }

            T instance = this.methodCache.getUnchecked(method)
                                         .orElse(null);
            if (instance == null) {
                continue;
            }

            if (methods == null) {
                methods = new ArrayList<>();
            }
            this.addMethod(methods, method, annotation, instance);
        }

        for (GenericSource.GenericMethod method : GenericSource.GenericMethod.all()) {
            if (!method.target.isAssignableFrom(klass)) {
                continue;
            }

            T instance = this.methodCache.getUnchecked(method.method)
                                         .orElse(null);
            if (instance == null) {
                continue;
            }

            if (methods == null) {
                methods = new ArrayList<>();
            }
            this.addMethod(methods, method.method, method.annotation, instance);
        }

        if (methods == null) {
            return Collections.emptyList();
        }
        methods.trimToSize();
        return Collections.unmodifiableList(methods);
    }

    private void addMethod(List<NamedMethod<T>> methods, Method method, LuaFunction annotation, T instance) {
        if (annotation.mainThread()) {
            instance = this.wrap.apply(instance);
        }

        String[] names = annotation.value();
        boolean isSimple = method.getReturnType() != MethodResult.class && !annotation.mainThread();
        if (names.length == 0) {
            methods.add(new NamedMethod<>(method.getName(), instance, isSimple));
        } else {
            for (String name : names) {
                methods.add(new NamedMethod<>(name, instance, isSimple));
            }
        }
    }

    @Nonnull
    private Optional<T> build(Method method) {
        String name = method.getDeclaringClass()
                            .getName() + "." + method.getName();
        int modifiers = method.getModifiers();

        // Instance methods must be final - this prevents them being overridden and potentially exposed twice.
        if (!Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers)) {
            ComputerCraft.log.warn("Lua Method {} should be final.", name);
        }

        if (!Modifier.isPublic(modifiers)) {
            ComputerCraft.log.error("Lua Method {} should be a public method.", name);
            return Optional.empty();
        }

        if (!Modifier.isPublic(method.getDeclaringClass()
                                     .getModifiers())) {
            ComputerCraft.log.error("Lua Method {} should be on a public class.", name);
            return Optional.empty();
        }

        ComputerCraft.log.debug("Generating method wrapper for {}.", name);

        Class<?>[] exceptions = method.getExceptionTypes();
        for (Class<?> exception : exceptions) {
            if (exception != LuaException.class) {
                ComputerCraft.log.error("Lua Method {} cannot throw {}.", name, exception.getName());
                return Optional.empty();
            }
        }

        // We have some rather ugly handling of static methods in both here and the main generate function. Static methods
        // only come from generic sources, so this should be safe.
        Class<?> target = Modifier.isStatic(modifiers) ? method.getParameterTypes()[0] : method.getDeclaringClass();

        try {
            String className = method.getDeclaringClass()
                                     .getName() + "$cc$" + method.getName() + METHOD_ID.getAndIncrement();
            byte[] bytes = this.generate(className, target, method);
            if (bytes == null) {
                return Optional.empty();
            }

            Class<?> klass = DeclaringClassLoader.INSTANCE.define(className,
                                                                  bytes,
                                                                  method.getDeclaringClass()
                                                                        .getProtectionDomain());
            return Optional.of(klass.asSubclass(this.base)
                                    .getDeclaredConstructor()
                                    .newInstance());
        } catch (ReflectiveOperationException | ClassFormatError | RuntimeException e) {
            ComputerCraft.log.error("Error generating wrapper for {}.", name, e);
            return Optional.empty();
        }

    }

    @Nullable
    private byte[] generate(String className, Class<?> target, Method method) {
        String internalName = className.replace(".", "/");

        // Construct a public final class which extends Object and implements MethodInstance.Delegate
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_8, ACC_PUBLIC | ACC_FINAL, internalName, null, "java/lang/Object", this.interfaces);
        cw.visitSource("CC generated method", null);

        { // Constructor just invokes super.
            MethodVisitor mw = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mw.visitCode();
            mw.visitVarInsn(ALOAD, 0);
            mw.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mw.visitInsn(RETURN);
            mw.visitMaxs(0, 0);
            mw.visitEnd();
        }

        {
            MethodVisitor mw = cw.visitMethod(ACC_PUBLIC, METHOD_NAME, this.methodDesc, null, EXCEPTIONS);
            mw.visitCode();

            // If we're an instance method, load the this parameter.
            if (!Modifier.isStatic(method.getModifiers())) {
                mw.visitVarInsn(ALOAD, 1);
                mw.visitTypeInsn(CHECKCAST, Type.getInternalName(target));
            }

            int argIndex = 0;
            for (java.lang.reflect.Type genericArg : method.getGenericParameterTypes()) {
                Boolean loadedArg = this.loadArg(mw, target, method, genericArg, argIndex);
                if (loadedArg == null) {
                    return null;
                }
                if (loadedArg) {
                    argIndex++;
                }
            }

            mw.visitMethodInsn(Modifier.isStatic(method.getModifiers()) ? INVOKESTATIC : INVOKEVIRTUAL,
                               Type.getInternalName(method.getDeclaringClass()),
                               method.getName(),
                               Type.getMethodDescriptor(method),
                               false);

            // We allow a reasonable amount of flexibility on the return value's type. Alongside the obvious MethodResult,
            // we convert basic types into an immediate result.
            Class<?> ret = method.getReturnType();
            if (ret != MethodResult.class) {
                if (ret == void.class) {
                    mw.visitMethodInsn(INVOKESTATIC, INTERNAL_METHOD_RESULT, "of", "()" + DESC_METHOD_RESULT, false);
                } else if (ret.isPrimitive()) {
                    Class<?> boxed = Primitives.wrap(ret);
                    mw.visitMethodInsn(INVOKESTATIC,
                                       Type.getInternalName(boxed),
                                       "valueOf",
                                       "(" + Type.getDescriptor(ret) + ")" + Type.getDescriptor(boxed),
                                       false);
                    mw.visitMethodInsn(INVOKESTATIC, INTERNAL_METHOD_RESULT, "of", "(Ljava/lang/Object;)" + DESC_METHOD_RESULT, false);
                } else if (ret == Object[].class) {
                    mw.visitMethodInsn(INVOKESTATIC, INTERNAL_METHOD_RESULT, "of", "([Ljava/lang/Object;)" + DESC_METHOD_RESULT, false);
                } else {
                    mw.visitMethodInsn(INVOKESTATIC, INTERNAL_METHOD_RESULT, "of", "(Ljava/lang/Object;)" + DESC_METHOD_RESULT, false);
                }
            }

            mw.visitInsn(ARETURN);

            mw.visitMaxs(0, 0);
            mw.visitEnd();
        }

        cw.visitEnd();

        return cw.toByteArray();
    }

    private Boolean loadArg(MethodVisitor mw, Class<?> target, Method method, java.lang.reflect.Type genericArg, int argIndex) {
        if (genericArg == target) {
            mw.visitVarInsn(ALOAD, 1);
            mw.visitTypeInsn(CHECKCAST, Type.getInternalName(target));
            return false;
        }

        Class<?> arg = Reflect.getRawType(method, genericArg, true);
        if (arg == null) {
            return null;
        }

        if (arg == IArguments.class) {
            mw.visitVarInsn(ALOAD, 2 + this.context.size());
            return false;
        }

        int idx = this.context.indexOf(arg);
        if (idx >= 0) {
            mw.visitVarInsn(ALOAD, 2 + idx);
            return false;
        }

        if (arg == Optional.class) {
            Class<?> klass = Reflect.getRawType(method,
                                                TypeToken.of(genericArg)
                                                         .resolveType(Reflect.OPTIONAL_IN)
                                                         .getType(),
                                                false);
            if (klass == null) {
                return null;
            }

            if (Enum.class.isAssignableFrom(klass) && klass != Enum.class) {
                mw.visitVarInsn(ALOAD, 2 + this.context.size());
                Reflect.loadInt(mw, argIndex);
                mw.visitLdcInsn(Type.getType(klass));
                mw.visitMethodInsn(INVOKEINTERFACE, INTERNAL_ARGUMENTS, "optEnum", "(ILjava/lang/Class;)Ljava/util/Optional;", true);
                return true;
            }

            String name = Reflect.getLuaName(Primitives.unwrap(klass));
            if (name != null) {
                mw.visitVarInsn(ALOAD, 2 + this.context.size());
                Reflect.loadInt(mw, argIndex);
                mw.visitMethodInsn(INVOKEINTERFACE, INTERNAL_ARGUMENTS, "opt" + name, "(I)Ljava/util/Optional;", true);
                return true;
            }
        }

        if (Enum.class.isAssignableFrom(arg) && arg != Enum.class) {
            mw.visitVarInsn(ALOAD, 2 + this.context.size());
            Reflect.loadInt(mw, argIndex);
            mw.visitLdcInsn(Type.getType(arg));
            mw.visitMethodInsn(INVOKEINTERFACE, INTERNAL_ARGUMENTS, "getEnum", "(ILjava/lang/Class;)Ljava/lang/Enum;", true);
            mw.visitTypeInsn(CHECKCAST, Type.getInternalName(arg));
            return true;
        }

        String name = arg == Object.class ? "" : Reflect.getLuaName(arg);
        if (name != null) {
            if (Reflect.getRawType(method, genericArg, false) == null) {
                return null;
            }

            mw.visitVarInsn(ALOAD, 2 + this.context.size());
            Reflect.loadInt(mw, argIndex);
            mw.visitMethodInsn(INVOKEINTERFACE, INTERNAL_ARGUMENTS, "get" + name, "(I)" + Type.getDescriptor(arg), true);
            return true;
        }

        ComputerCraft.log.error("Unknown parameter type {} for method {}.{}.",
                                arg.getName(),
                                method.getDeclaringClass()
                                      .getName(),
                                method.getName());
        return null;
    }

    @SuppressWarnings( "Guava" )
    private static <T, U> com.google.common.base.Function<T, U> catching( Function<T, U> function, U def )
    {
        return x -> {
            try
            {
                return function.apply( x );
            }
            catch( Exception | LinkageError e )
            {
                // LinkageError due to possible codegen bugs and NoClassDefFoundError. The latter occurs when fetching
                // methods on a class which references non-existent (i.e. client-only) types.
                ComputerCraft.log.error( "Error generating @LuaFunctions", e );
                return def;
            }
        };
    }
}
