package io.javaoperatorsdk.operator.sample.multipleactivationsharinginformer;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@ControllerConfiguration(dependents = {
    @Dependent(type = ConfigMapDependentResource1.class,
        activationCondition = ActivationCondition.class),
    @Dependent(type = ConfigMapDependentResource2.class,
        activationCondition = ActivationCondition.class),
    @Dependent(type = SecretDependentResource.class)
})
public class MultipleActivationSharingInformerReconciler
    implements Reconciler<MultipleActivationSharingInformerCustomResource> {

  public static final String CONFIG_MAP_INFORMER_NAME = "ConfigMapInformer";
  private final AtomicInteger numberOfReconciliationExecution = new AtomicInteger(0);

  InformerEventSource<ConfigMap,MultipleActivationSharingInformerCustomResource> configMapES;
  
  
  public MultipleActivationSharingInformerReconciler() {
  }

  @Override
  public UpdateControl<MultipleActivationSharingInformerCustomResource> reconcile(
      MultipleActivationSharingInformerCustomResource resource,
      Context<MultipleActivationSharingInformerCustomResource> context) {

    numberOfReconciliationExecution.incrementAndGet();

    return UpdateControl.noUpdate();
  }

  public int getNumberOfReconciliationExecution() {
    return numberOfReconciliationExecution.get();
  }
}
