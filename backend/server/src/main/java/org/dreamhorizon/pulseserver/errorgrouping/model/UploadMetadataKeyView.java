package org.dreamhorizon.pulseserver.errorgrouping.model;

/** Cache-key fields on {@link UploadMetadata} exposed for Kotlin callers. */
public interface UploadMetadataKeyView {

  String getProjectId();

  String getPlatform();

  String getAppVersion();

  String getVersionCode();

  String getBundleId();
}
