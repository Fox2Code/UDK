package com.fox2code.udk.build;

@SuppressWarnings({"ConstantConditions", "unchecked"})
public final class ptr<T> extends Object {
    public ptr() {}

    public ptr(T value) {
        this.value = value;
    }

    public T value;

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }

    public Object[] asArray() {
        return (Object[]) ((Object) this);
    }

    public ptr<T> copy() {
        return new ptr<>(this.value);
    }

    public void clear() {
        this.value = null;
    }

    public boolean isPtr() {
        return true;
    }

    public static <T> ptr<T> from(Object[] object) {
        return new ptr<>((T) object[0]);
    }

    public static <T> ptr<T> from(ptr<?> ptr) {
        return (ptr<T>) ptr;
    }

    public static <T> T valueOf(Object[] object) {
        return (T) from(object).value;
    }

    public static <T> T valueOf(ptr<T> ptr) {
        return ptr.value;
    }
}
