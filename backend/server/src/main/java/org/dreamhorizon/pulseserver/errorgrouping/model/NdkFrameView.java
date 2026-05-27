package org.dreamhorizon.pulseserver.errorgrouping.model;

/** Kotlin-facing view of {@link NdkFrame} stack metadata (explicit getters for mixed compile). */
public interface NdkFrameView {

  String getNdkLib();

  String getNdkPc();

  String getRawLine();
}
