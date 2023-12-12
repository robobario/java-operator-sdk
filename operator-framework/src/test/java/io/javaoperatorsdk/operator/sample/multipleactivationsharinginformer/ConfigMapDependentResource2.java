package io.javaoperatorsdk.operator.sample.multipleactivationsharinginformer;

public class ConfigMapDependentResource2 extends AbstractConfigMapDependentResource {

    public static final String VALUE_SUFFIX = "_2";

    @Override
    String value(MultipleActivationSharingInformerCustomResource primary) {
        return primary.getSpec().getValue()+ VALUE_SUFFIX;
    }
}
