package io.javaoperatorsdk.operator.sample.multipleactivationsharinginformer;

import io.fabric8.openshift.api.model.Route;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class ActivationCondition
    implements Condition<Route, MultipleActivationSharingInformerCustomResource> {

  public final static boolean MET = true;

  @Override
  public boolean isMet(
      DependentResource<Route, MultipleActivationSharingInformerCustomResource> dependentResource,
      MultipleActivationSharingInformerCustomResource primary,
      Context<MultipleActivationSharingInformerCustomResource> context) {
    return MET;
  }
}
