package io.javaoperatorsdk.operator.sample.multipleactivationsharinginformer;

public class ConfigMapDependentResource1 extends AbstractConfigMapDependentResource {

    public static final String VALUE_SUFFIX = "_1";

    @Override
    String value(MultipleActivationSharingInformerCustomResource primary) {
        return primary.getSpec().getValue()+ VALUE_SUFFIX;
    }
}
