package org.dreamhorizon.pulseserver.util.serialization;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@JsonDeserialize(using = TrimmedStringDeserializer.class)
public @interface Trimmed {
}
