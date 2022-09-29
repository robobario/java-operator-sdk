package io.javaoperatorsdk.operator.processing.dependent.external;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.ExternalIDMapper;

public interface ProvidedResourceIDHandler<P extends HasMetadata, R, T> {

  void storeExternalID(P primary, R resource, T id);

  void cleanupExternalIDState(P primary, R resource, T id);

  ExternalIDMapper<P, R, T> externalIDMapper();

}
