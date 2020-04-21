package com.fox2code.udk.build;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Make method not usable by mods that put your mod as library
 * (Still accessible via reflection)
 */
@Target({ElementType.CONSTRUCTOR,ElementType.FIELD,ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface Internal {
}
