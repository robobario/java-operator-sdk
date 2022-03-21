package io.javaoperatorsdk.operator.processing.event;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.api.config.Cloner;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.RetryConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.ReconciliationDispatcher.CustomResourceFacade;
import io.javaoperatorsdk.operator.sample.observedgeneration.ObservedGenCustomResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class ReconciliationDispatcherTest {

  private static final String DEFAULT_FINALIZER = "javaoperatorsdk.io/finalizer";
  public static final String ERROR_MESSAGE = "ErrorMessage";
  public static final long RECONCILIATION_MAX_INTERVAL = 10L;
  private TestCustomResource testCustomResource;
  private ReconciliationDispatcher<TestCustomResource> reconciliationDispatcher;
  private TestReconciler reconciler;
  private final CustomResourceFacade<TestCustomResource> customResourceFacade =
      mock(ReconciliationDispatcher.CustomResourceFacade.class);

  @BeforeAll
  static void classSetup() {
    /*
     * We need this for mock reconcilers to properly generate the expected UpdateControl: without
     * this, calls such as `when(reconciler.reconcile(eq(testCustomResource),
     * any())).thenReturn(UpdateControl.updateStatus(testCustomResource))` will return null because
     * equals will fail on the two equal but NOT identical TestCustomResources because equals is not
     * implemented on TestCustomResourceSpec or TestCustomResourceStatus
     */
    ConfigurationServiceProvider.overrideCurrent(overrider -> {
      overrider.withResourceCloner(new Cloner() {
        @Override
        public <R extends HasMetadata> R clone(R object) {
          return object;
        }
      });
    });
  }

  @AfterAll
  static void tearDown() {
    ConfigurationServiceProvider.reset();
  }

  @BeforeEach
  void setup() {
    testCustomResource = TestUtils.testCustomResource();
    reconciler = spy(new TestReconciler());
    reconciliationDispatcher =
        init(testCustomResource, reconciler, null, customResourceFacade, true);
  }

  private <R extends HasMetadata> ReconciliationDispatcher<R> init(R customResource,
      Reconciler<R> reconciler, ControllerConfiguration<R> configuration,
      CustomResourceFacade<R> customResourceFacade, boolean useFinalizer) {

    configuration = configuration == null ? mock(ControllerConfiguration.class) : configuration;

    when(configuration.getFinalizerName()).thenReturn(DEFAULT_FINALIZER);
    when(configuration.getName()).thenReturn("EventDispatcherTestController");
    when(configuration.getResourceClass()).thenReturn((Class<R>) customResource.getClass());
    when(configuration.getRetryConfiguration()).thenReturn(RetryConfiguration.DEFAULT);
    when(configuration.reconciliationMaxInterval())
        .thenReturn(Optional.of(Duration.ofHours(RECONCILIATION_MAX_INTERVAL)));

    Controller<R> controller = new Controller<>(reconciler, configuration,
        MockKubernetesClient.client(customResource.getClass())) {
      @Override
      public boolean useFinalizer() {
        return useFinalizer;
      }
    };
    controller.start();

    return new ReconciliationDispatcher<>(controller, customResourceFacade);
  }

  @Test
  void addFinalizerOnNewResource() throws Exception {
    assertFalse(testCustomResource.hasFinalizer(DEFAULT_FINALIZER));
    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));
    verify(reconciler, never())
        .reconcile(ArgumentMatchers.eq(testCustomResource), any());
    verify(customResourceFacade, times(1))
        .replaceWithLock(
            argThat(testCustomResource -> testCustomResource.hasFinalizer(DEFAULT_FINALIZER)));
    assertThat(testCustomResource.hasFinalizer(DEFAULT_FINALIZER)).isTrue();
  }

  @Test
  void callCreateOrUpdateOnNewResourceIfFinalizerSet() throws Exception {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);
    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));
    verify(reconciler, times(1))
        .reconcile(ArgumentMatchers.eq(testCustomResource), any());
  }

  @Test
  void updatesOnlyStatusSubResourceIfFinalizerSet() throws Exception {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    reconciler.reconcile = (r, c) -> UpdateControl.updateStatus(testCustomResource);

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    verify(customResourceFacade, times(1)).updateStatus(testCustomResource);
    verify(customResourceFacade, never()).replaceWithLock(any());
  }

  @Test
  void updatesBothResourceAndStatusIfFinalizerSet() throws Exception {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    reconciler.reconcile = (r, c) -> UpdateControl.updateResourceAndStatus(testCustomResource);
    when(customResourceFacade.replaceWithLock(testCustomResource)).thenReturn(testCustomResource);

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    verify(customResourceFacade, times(1)).replaceWithLock(testCustomResource);
    verify(customResourceFacade, times(1)).updateStatus(testCustomResource);
  }

  @Test
  void callCreateOrUpdateOnModifiedResourceIfFinalizerSet() throws Exception {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));
    verify(reconciler, times(1))
        .reconcile(ArgumentMatchers.eq(testCustomResource), any());
  }

  @Test
  void callsDeleteIfObjectHasFinalizerAndMarkedForDelete() {
    // we need to add the finalizer before marking it for deletion, as otherwise it won't get added
    assertTrue(testCustomResource.addFinalizer(DEFAULT_FINALIZER));
    markForDeletion(testCustomResource);

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    verify(reconciler, times(1)).cleanup(eq(testCustomResource), any());
  }

  @Test
  void doesNotCallDeleteOnControllerIfMarkedForDeletionWhenNoFinalizerIsConfigured() {
    final ReconciliationDispatcher<TestCustomResource> dispatcher =
        init(testCustomResource, reconciler, null, customResourceFacade, false);
    markForDeletion(testCustomResource);

    dispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    verify(reconciler, times(0)).cleanup(eq(testCustomResource), any());
  }

  @Test
  void doNotCallDeleteIfMarkedForDeletionWhenFinalizerHasAlreadyBeenRemoved() {
    markForDeletion(testCustomResource);

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    verify(reconciler, never()).cleanup(eq(testCustomResource), any());
  }

  @Test
  void doesNotAddFinalizerIfConfiguredNotTo() {
    final ReconciliationDispatcher<TestCustomResource> dispatcher =
        init(testCustomResource, reconciler, null, customResourceFacade, false);

    dispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    assertEquals(0, testCustomResource.getMetadata().getFinalizers().size());
  }

  @Test
  void removesDefaultFinalizerOnDeleteIfSet() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);
    markForDeletion(testCustomResource);

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    assertEquals(0, testCustomResource.getMetadata().getFinalizers().size());
    verify(customResourceFacade, times(1)).replaceWithLock(any());
  }

  @Test
  void doesNotRemovesTheSetFinalizerIfTheDeleteNotMethodInstructsIt() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    reconciler.cleanup = (r, c) -> DeleteControl.noFinalizerRemoval();
    markForDeletion(testCustomResource);

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    assertEquals(1, testCustomResource.getMetadata().getFinalizers().size());
    verify(customResourceFacade, never()).replaceWithLock(any());
  }

  @Test
  void doesNotUpdateTheResourceIfNoUpdateUpdateControlIfFinalizerSet() throws Exception {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    reconciler.reconcile = (r, c) -> UpdateControl.noUpdate();

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));
    verify(customResourceFacade, never()).replaceWithLock(any());
    verify(customResourceFacade, never()).updateStatus(testCustomResource);
  }

  @Test
  void addsFinalizerIfNotMarkedForDeletionAndEmptyCustomResourceReturned() throws Exception {
    removeFinalizers(testCustomResource);

    reconciler.reconcile = (r, c) -> UpdateControl.noUpdate();

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    assertEquals(1, testCustomResource.getMetadata().getFinalizers().size());
    verify(customResourceFacade, times(1)).replaceWithLock(any());
  }

  @Test
  void doesNotCallDeleteIfMarkedForDeletionButNotOurFinalizer() {
    removeFinalizers(testCustomResource);
    markForDeletion(testCustomResource);

    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    verify(customResourceFacade, never()).replaceWithLock(any());
    verify(reconciler, never()).cleanup(eq(testCustomResource), any());
  }

  @Test
  void executeControllerRegardlessGenerationInNonGenerationAwareModeIfFinalizerSet()
      throws Exception {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);
    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));
    reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    verify(reconciler, times(2)).reconcile(eq(testCustomResource), any());
  }

  @Test
  void propagatesRetryInfoToContextIfFinalizerSet() throws Exception {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    reconciliationDispatcher.handleExecution(
        new ExecutionScope(
            testCustomResource,
            new RetryInfo() {
              @Override
              public int getAttemptCount() {
                return 2;
              }

              @Override
              public boolean isLastAttempt() {
                return true;
              }
            }));

    ArgumentCaptor<Context> contextArgumentCaptor =
        ArgumentCaptor.forClass(Context.class);
    verify(reconciler, times(1))
        .reconcile(any(), contextArgumentCaptor.capture());
    Context<?> context = contextArgumentCaptor.getValue();
    final var retryInfo = context.getRetryInfo().orElseGet(() -> fail("Missing optional"));
    assertThat(retryInfo.getAttemptCount()).isEqualTo(2);
    assertThat(retryInfo.isLastAttempt()).isEqualTo(true);
  }

  @Test
  void setReScheduleToPostExecutionControlFromUpdateControl() throws Exception {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    reconciler.reconcile =
        (r, c) -> UpdateControl.updateStatus(testCustomResource).rescheduleAfter(1000L);

    PostExecutionControl control =
        reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    assertThat(control.getReScheduleDelay().orElseGet(() -> fail("Missing optional")))
        .isEqualTo(1000L);
  }

  @Test
  void reScheduleOnDeleteWithoutFinalizerRemoval() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);
    markForDeletion(testCustomResource);

    reconciler.cleanup =
        (r, c) -> DeleteControl.noFinalizerRemoval().rescheduleAfter(1, TimeUnit.SECONDS);

    PostExecutionControl control =
        reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    assertThat(control.getReScheduleDelay().orElseGet(() -> fail("Missing optional")))
        .isEqualTo(1000L);
  }

  @Test
  void setObservedGenerationForStatusIfNeeded() throws Exception {
    var observedGenResource = createObservedGenCustomResource();

    Reconciler<ObservedGenCustomResource> reconciler = mock(Reconciler.class);
    ControllerConfiguration<ObservedGenCustomResource> config =
        mock(ControllerConfiguration.class);
    CustomResourceFacade<ObservedGenCustomResource> facade = mock(CustomResourceFacade.class);
    var dispatcher = init(observedGenResource, reconciler, config, facade, true);

    when(config.isGenerationAware()).thenReturn(true);
    when(reconciler.reconcile(any(), any()))
        .thenReturn(UpdateControl.updateStatus(observedGenResource));
    when(facade.updateStatus(observedGenResource)).thenReturn(observedGenResource);

    PostExecutionControl<ObservedGenCustomResource> control = dispatcher.handleExecution(
        executionScopeWithCREvent(observedGenResource));
    assertThat(control.getUpdatedCustomResource().orElseGet(() -> fail("Missing optional"))
        .getStatus().getObservedGeneration())
            .isEqualTo(1L);
  }

  @Test
  void updatesObservedGenerationOnNoUpdateUpdateControl() throws Exception {
    var observedGenResource = createObservedGenCustomResource();

    Reconciler<ObservedGenCustomResource> reconciler = mock(Reconciler.class);
    ControllerConfiguration<ObservedGenCustomResource> config =
        mock(ControllerConfiguration.class);
    CustomResourceFacade<ObservedGenCustomResource> facade = mock(CustomResourceFacade.class);
    when(config.isGenerationAware()).thenReturn(true);
    when(reconciler.reconcile(any(), any()))
        .thenReturn(UpdateControl.noUpdate());
    when(facade.updateStatus(observedGenResource)).thenReturn(observedGenResource);
    var dispatcher = init(observedGenResource, reconciler, config, facade, true);

    PostExecutionControl<ObservedGenCustomResource> control = dispatcher.handleExecution(
        executionScopeWithCREvent(observedGenResource));
    assertThat(control.getUpdatedCustomResource().orElseGet(() -> fail("Missing optional"))
        .getStatus().getObservedGeneration())
            .isEqualTo(1L);
  }

  @Test
  void updateObservedGenerationOnCustomResourceUpdate() throws Exception {
    var observedGenResource = createObservedGenCustomResource();

    Reconciler<ObservedGenCustomResource> reconciler = mock(Reconciler.class);
    ControllerConfiguration<ObservedGenCustomResource> config =
        mock(ControllerConfiguration.class);
    CustomResourceFacade<ObservedGenCustomResource> facade = mock(CustomResourceFacade.class);
    when(config.isGenerationAware()).thenReturn(true);
    when(reconciler.reconcile(any(), any()))
        .thenReturn(UpdateControl.updateResource(observedGenResource));
    when(facade.replaceWithLock(any())).thenReturn(observedGenResource);
    when(facade.updateStatus(observedGenResource)).thenReturn(observedGenResource);
    var dispatcher = init(observedGenResource, reconciler, config, facade, true);

    PostExecutionControl<ObservedGenCustomResource> control = dispatcher.handleExecution(
        executionScopeWithCREvent(observedGenResource));
    assertThat(control.getUpdatedCustomResource().orElseGet(() -> fail("Missing optional"))
        .getStatus().getObservedGeneration())
            .isEqualTo(1L);
  }

  @Test
  void callErrorStatusHandlerIfImplemented() throws Exception {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    reconciler.reconcile = (r, c) -> {
      throw new IllegalStateException("Error Status Test");
    };
    reconciler.errorHandler = (r, ri, e) -> {
      testCustomResource.getStatus().setConfigMapStatus(ERROR_MESSAGE);
      return ErrorStatusUpdateControl.updateStatus(testCustomResource);
    };

    reconciliationDispatcher.handleExecution(
        new ExecutionScope(
            testCustomResource,
            new RetryInfo() {
              @Override
              public int getAttemptCount() {
                return 2;
              }

              @Override
              public boolean isLastAttempt() {
                return true;
              }
            }));

    verify(customResourceFacade, times(1)).updateStatus(testCustomResource);
    verify(((ErrorStatusHandler) reconciler), times(1)).updateErrorStatus(eq(testCustomResource),
        any(), any());
  }

  @Test
  void callErrorStatusHandlerEvenOnFirstError() throws Exception {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    reconciler.reconcile = (r, c) -> {
      throw new IllegalStateException("Error Status Test");
    };
    reconciler.errorHandler = (r, ri, e) -> {
      testCustomResource.getStatus().setConfigMapStatus(ERROR_MESSAGE);
      return ErrorStatusUpdateControl.updateStatus(testCustomResource);
    };

    var postExecControl = reconciliationDispatcher.handleExecution(
        new ExecutionScope(
            testCustomResource, null));
    verify(customResourceFacade, times(1)).updateStatus(testCustomResource);
    verify(((ErrorStatusHandler) reconciler), times(1)).updateErrorStatus(eq(testCustomResource),
        any(), any());
    assertThat(postExecControl.exceptionDuringExecution()).isTrue();
  }

  @Test
  void errorHandlerCanInstructNoRetryWithUpdate() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);
    reconciler.reconcile = (r, c) -> {
      throw new IllegalStateException("Error Status Test");
    };
    reconciler.errorHandler = (r, ri, e) -> {
      testCustomResource.getStatus().setConfigMapStatus(ERROR_MESSAGE);
      return ErrorStatusUpdateControl.updateStatus(testCustomResource).withNoRetry();
    };

    var postExecControl = reconciliationDispatcher.handleExecution(
        new ExecutionScope(
            testCustomResource, null));

    verify(((ErrorStatusHandler) reconciler), times(1)).updateErrorStatus(eq(testCustomResource),
        any(), any());
    verify(customResourceFacade, times(1)).updateStatus(testCustomResource);
    assertThat(postExecControl.exceptionDuringExecution()).isFalse();
  }

  @Test
  void errorHandlerCanInstructNoRetryNoUpdate() {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);
    reconciler.reconcile = (r, c) -> {
      throw new IllegalStateException("Error Status Test");
    };
    reconciler.errorHandler = (r, ri, e) -> {
      testCustomResource.getStatus().setConfigMapStatus(ERROR_MESSAGE);
      return ErrorStatusUpdateControl.<TestCustomResource>noStatusUpdate().withNoRetry();
    };

    var postExecControl = reconciliationDispatcher.handleExecution(
        new ExecutionScope(
            testCustomResource, null));

    verify(((ErrorStatusHandler) reconciler), times(1)).updateErrorStatus(eq(testCustomResource),
        any(), any());
    verify(customResourceFacade, times(0)).updateStatus(testCustomResource);
    assertThat(postExecControl.exceptionDuringExecution()).isFalse();
  }

  @Test
  void schedulesReconciliationIfMaxDelayIsSet() throws Exception {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    reconciler.reconcile = (r, c) -> UpdateControl.noUpdate();

    PostExecutionControl control =
        reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    assertThat(control.getReScheduleDelay()).isPresent()
        .hasValue(TimeUnit.HOURS.toMillis(RECONCILIATION_MAX_INTERVAL));
  }

  @Test
  void canSkipSchedulingMaxDelayIf() throws Exception {
    testCustomResource.addFinalizer(DEFAULT_FINALIZER);

    reconciler.reconcile = (r, c) -> UpdateControl.noUpdate();
    when(reconciliationDispatcher.configuration().reconciliationMaxInterval())
        .thenReturn(Optional.empty());

    PostExecutionControl control =
        reconciliationDispatcher.handleExecution(executionScopeWithCREvent(testCustomResource));

    assertThat(control.getReScheduleDelay()).isNotPresent();
  }

  private ObservedGenCustomResource createObservedGenCustomResource() {
    ObservedGenCustomResource observedGenCustomResource = new ObservedGenCustomResource();
    observedGenCustomResource.setMetadata(new ObjectMeta());
    observedGenCustomResource.getMetadata().setGeneration(1L);
    observedGenCustomResource.getMetadata().setFinalizers(new ArrayList<>());
    observedGenCustomResource.getMetadata().getFinalizers().add(DEFAULT_FINALIZER);
    return observedGenCustomResource;
  }

  private void markForDeletion(CustomResource customResource) {
    customResource.getMetadata().setDeletionTimestamp("2019-8-10");
  }

  private void removeFinalizers(CustomResource customResource) {
    customResource.getMetadata().getFinalizers().clear();
  }

  public <T extends HasMetadata> ExecutionScope<T> executionScopeWithCREvent(T resource) {
    return new ExecutionScope<>(resource, null);
  }

  private class TestReconciler
      implements Reconciler<TestCustomResource>, Cleaner<TestCustomResource>,
      ErrorStatusHandler<TestCustomResource> {
    private BiFunction<TestCustomResource, Context, UpdateControl<TestCustomResource>> reconcile;
    private BiFunction<TestCustomResource, Context, DeleteControl> cleanup;
    private ErrorStatusHandler<TestCustomResource> errorHandler;

    @Override
    public UpdateControl<TestCustomResource> reconcile(TestCustomResource resource,
        Context context) {
      if (reconcile != null && resource.equals(testCustomResource)) {
        return reconcile.apply(resource, context);
      }
      return UpdateControl.noUpdate();
    }

    @Override
    public DeleteControl cleanup(TestCustomResource resource, Context context) {
      if (cleanup != null && resource.equals(testCustomResource)) {
        return cleanup.apply(resource, context);
      }
      return DeleteControl.defaultDelete();
    }

    @Override
    public ErrorStatusUpdateControl<TestCustomResource> updateErrorStatus(
        TestCustomResource resource,
        Context<TestCustomResource> context, Exception e) {
      return errorHandler != null ? errorHandler.updateErrorStatus(resource, context, e)
          : ErrorStatusUpdateControl.noStatusUpdate();
    }
  }
}
