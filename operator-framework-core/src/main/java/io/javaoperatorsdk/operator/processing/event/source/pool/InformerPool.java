package io.javaoperatorsdk.operator.processing.event.source.pool;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;

public class InformerPool {


    public <T> SharedIndexInformer<T> initOrGetInformer(String ownerName,
                                                        KubernetesClient client, InformerIdentifier settings) {
        return null;
    }

    public <T> void informerRemoved(String ownerName, Class<T> resourceType, String namespace) {

    }

}
