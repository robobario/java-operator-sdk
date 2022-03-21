package io.javaoperatorsdk.operator.api.config;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import io.fabric8.kubernetes.client.Config;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;

@SuppressWarnings("unused")
public class ConfigurationServiceOverrider {
  private final ConfigurationService original;
  private Metrics metrics;
  private Config clientConfig;
  private int threadNumber;
  private Cloner cloner;
  private int timeoutSeconds;
  private boolean closeClientOnStop;
  private ExecutorService executorService = null;

  ConfigurationServiceOverrider(ConfigurationService original) {
    this.original = original;
    this.clientConfig = original.getClientConfiguration();
    this.threadNumber = original.concurrentReconciliationThreads();
    this.cloner = original.getResourceCloner();
    this.timeoutSeconds = original.getTerminationTimeoutSeconds();
    this.metrics = original.getMetrics();
    this.closeClientOnStop = original.closeClientOnStop();
  }


  public ConfigurationServiceOverrider withClientConfiguration(Config configuration) {
    this.clientConfig = configuration;
    return this;
  }

  public ConfigurationServiceOverrider withConcurrentReconciliationThreads(int threadNumber) {
    this.threadNumber = threadNumber;
    return this;
  }

  public ConfigurationServiceOverrider withResourceCloner(Cloner cloner) {
    this.cloner = cloner;
    return this;
  }

  public ConfigurationServiceOverrider withTerminationTimeoutSeconds(int timeoutSeconds) {
    this.timeoutSeconds = timeoutSeconds;
    return this;
  }

  public ConfigurationServiceOverrider withMetrics(Metrics metrics) {
    this.metrics = metrics;
    return this;
  }

  public ConfigurationServiceOverrider withCloseClientOnStop(boolean close) {
    this.closeClientOnStop = close;
    return this;
  }

  public ConfigurationServiceOverrider withExecutorService(ExecutorService executorService) {
    this.executorService = executorService;
    return this;
  }

  public ConfigurationService build() {
    return new BaseConfigurationService(original.getVersion()) {
      @Override
      public Set<String> getKnownReconcilerNames() {
        return original.getKnownReconcilerNames();
      }

      @Override
      public Config getClientConfiguration() {
        return clientConfig;
      }

      @Override
      public int concurrentReconciliationThreads() {
        return threadNumber;
      }

      @Override
      public Cloner getResourceCloner() {
        return cloner;
      }

      @Override
      public int getTerminationTimeoutSeconds() {
        return timeoutSeconds;
      }

      @Override
      public Metrics getMetrics() {
        return metrics;
      }

      @Override
      public boolean closeClientOnStop() {
        return closeClientOnStop;
      }

      @Override
      public ExecutorService getExecutorService() {
        if (executorService != null) {
          return executorService;
        } else {
          return super.getExecutorService();
        }
      }
    };
  }

  /**
   * @deprecated Use {@link ConfigurationServiceProvider#overrideCurrent(Consumer)} instead
   */
  @Deprecated(since = "2.2.0")
  public static ConfigurationServiceOverrider override(ConfigurationService original) {
    return new ConfigurationServiceOverrider(original);
  }
}
