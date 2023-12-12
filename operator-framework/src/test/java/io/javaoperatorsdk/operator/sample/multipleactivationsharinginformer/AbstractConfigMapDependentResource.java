package io.javaoperatorsdk.operator.sample.multipleactivationsharinginformer;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDNoGCKubernetesDependentResource;

import java.util.Map;

public abstract class AbstractConfigMapDependentResource
    extends
    CRUDNoGCKubernetesDependentResource<ConfigMap, MultipleActivationSharingInformerCustomResource> {

  public static final String DATA_KEY = "data";

  public AbstractConfigMapDependentResource() {
    super(ConfigMap.class);
  }

  @Override
  protected ConfigMap desired(MultipleActivationSharingInformerCustomResource primary,
                              Context<MultipleActivationSharingInformerCustomResource> context) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMetaBuilder()
        .withName(primary.getMetadata().getName())
        .withNamespace(primary.getMetadata().getNamespace())
        .build());
    configMap.setData(Map.of(DATA_KEY, value(primary)));
    return configMap;
  }

  abstract String value(MultipleActivationSharingInformerCustomResource primary);

}
