package org.acme;

import com.couchbase.client.core.logging.LogRedaction;
import com.couchbase.client.core.logging.RedactionLevel;
import com.couchbase.client.java.Cluster;
import core.perf.*;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import javaperformer.JavaPerformer;
import javaperformer.JavaSdkCommandExecutor;
import javaperformer.JavaTransactionCommandExecutor;
import javaperformer.ReactiveJavaSdkCommandExecutor;
import com.couchbase.client.core.cnc.RequestSpan;
import com.couchbase.client.core.cnc.events.transaction.TransactionCleanupAttemptEvent;
import com.couchbase.client.core.io.CollectionIdentifier;
import com.couchbase.client.core.transaction.cleanup.ClientRecord;
import com.couchbase.client.core.transaction.cleanup.ClientRecordDetails;
import com.couchbase.client.core.transaction.cleanup.TransactionsCleaner;
import com.couchbase.client.core.transaction.components.ActiveTransactionRecord;
import com.couchbase.client.core.transaction.components.ActiveTransactionRecordEntry;
import com.couchbase.client.core.transaction.config.CoreMergedTransactionConfig;
import com.couchbase.client.core.transaction.forwards.Extension;
import com.couchbase.client.core.transaction.forwards.Supported;
import com.couchbase.client.core.transaction.log.CoreTransactionLogger;
import com.couchbase.client.java.transactions.config.TransactionsConfig;
import core.CorePerformer;
import core.commands.SdkCommandExecutor;
import core.commands.TransactionCommandExecutor;
import core.util.VersionUtil;
import com.couchbase.client.protocol.observability.SpanCreateRequest;
import com.couchbase.client.protocol.observability.SpanCreateResponse;
import com.couchbase.client.protocol.observability.SpanFinishRequest;
import com.couchbase.client.protocol.observability.SpanFinishResponse;
import com.couchbase.client.protocol.performer.Caps;
import com.couchbase.client.protocol.performer.PerformerCapsFetchResponse;
import com.couchbase.client.protocol.shared.API;
import com.couchbase.client.protocol.shared.ClusterConnectionCloseRequest;
import com.couchbase.client.protocol.shared.ClusterConnectionCloseResponse;
import com.couchbase.client.protocol.shared.ClusterConnectionCreateRequest;
import com.couchbase.client.protocol.shared.ClusterConnectionCreateResponse;
import com.couchbase.client.protocol.shared.Collection;
import com.couchbase.client.protocol.shared.DisconnectConnectionsRequest;
import com.couchbase.client.protocol.shared.DisconnectConnectionsResponse;
import com.couchbase.client.protocol.shared.EchoRequest;
import com.couchbase.client.protocol.shared.EchoResponse;
import com.couchbase.client.protocol.transactions.CleanupSet;
import com.couchbase.client.protocol.transactions.CleanupSetFetchRequest;
import com.couchbase.client.protocol.transactions.CleanupSetFetchResponse;
import com.couchbase.client.protocol.transactions.ClientRecordProcessRequest;
import com.couchbase.client.protocol.transactions.ClientRecordProcessResponse;
import com.couchbase.client.protocol.transactions.TransactionCleanupAttempt;
import com.couchbase.client.protocol.transactions.TransactionCleanupRequest;
import com.couchbase.client.protocol.transactions.TransactionCreateRequest;
import com.couchbase.client.protocol.transactions.TransactionResult;
import com.couchbase.client.protocol.transactions.TransactionSingleQueryRequest;
import com.couchbase.client.protocol.transactions.TransactionSingleQueryResponse;
import com.couchbase.client.protocol.transactions.TransactionStreamDriverToPerformer;
import com.couchbase.client.protocol.transactions.TransactionStreamPerformerToDriver;
import javaperformer.transactions.SingleQueryTransactionExecutor;
import javaperformer.twoway.TwoWayTransactionBlocking;
import javaperformer.twoway.TwoWayTransactionMarshaller;
import javaperformer.twoway.TwoWayTransactionReactive;
import javaperformer.utils.Capabilities;
import javaperformer.utils.ClusterConnection;
import javaperformer.utils.HooksUtil;
import javaperformer.utils.OptionsUtil;
import javaperformer.utils.ResultsUtil;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.quarkus.grpc.GrpcService;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.couchbase.client.core.io.CollectionIdentifier.DEFAULT_COLLECTION;
import static com.couchbase.client.core.io.CollectionIdentifier.DEFAULT_SCOPE;

@GrpcService
public class QuarkusPerformer extends CorePerformer {
    private static final Logger logger = LoggerFactory.getLogger(JavaPerformer.class);
    private static final ConcurrentHashMap<String, ClusterConnection> clusterConnections = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, RequestSpan> spans = new ConcurrentHashMap<>();

    @Inject
    Cluster quarkusCluster;

    static {
        LogRedaction.setRedactionLevel(RedactionLevel.PARTIAL);
    }

    // Allows capturing various errors so we can notify the driver of problems.
    public static AtomicReference<String> globalError = new AtomicReference<>();

    @Override
    @Blocking
    protected SdkCommandExecutor executor(com.couchbase.client.protocol.run.Workloads workloads, Counters counters, API api) {
        var connection = clusterConnections.get(workloads.getClusterConnectionId());
        return api == API.DEFAULT
                ? new JavaSdkCommandExecutor(connection, counters, spans)
                : new ReactiveJavaSdkCommandExecutor(connection, counters, spans);
    }

    @Override
    @Blocking
    protected TransactionCommandExecutor transactionsExecutor(com.couchbase.client.protocol.run.Workloads workloads, Counters counters) {
        // [if:3.3.0]
        var connection = clusterConnections.get(workloads.getClusterConnectionId());
        return new JavaTransactionCommandExecutor(connection, counters, spans);
        // [else]
        //? return null;
        // [end]
    }

    @Override
    @Blocking
    protected void customisePerformerCaps(PerformerCapsFetchResponse.Builder response) {
        response.addAllSdkImplementationCaps(Capabilities.sdkImplementationCaps());
        var sdkVersion = VersionUtil.introspectSDKVersionJava();
        if (sdkVersion == null) {
            // Not entirely clear why this fails sometimes on CI, return something sort of sensible as a default.
            sdkVersion = "3.5.0";
            logger.warn("Unable to introspect the sdk version, forcing it to {}", sdkVersion);
        }
        response.setLibraryVersion(sdkVersion);

        // [if:3.3.0]
        for (Extension ext : Extension.SUPPORTED) {
            try {
                var pc = com.couchbase.client.protocol.transactions.Caps.valueOf(ext.name());
                response.addTransactionImplementationsCaps(pc);
            } catch (IllegalArgumentException err) {
                // FIT and Java have used slightly different names for this
                if (ext.name().equals("EXT_CUSTOM_METADATA")) {
                    response.addTransactionImplementationsCaps(com.couchbase.client.protocol.transactions.Caps.EXT_CUSTOM_METADATA_COLLECTION);
                } else {
                    logger.warn("Could not find FIT extension for " + ext.name());
                }
            }
        }

        var supported = new Supported();
        var protocolVersion = supported.protocolMajor + "." + supported.protocolMinor;

        response.setTransactionsProtocolVersion(protocolVersion);

        logger.info("Performer implements protocol {} with caps {}",
                protocolVersion, response.getPerformerCapsList());
        response.addPerformerCaps(Caps.TRANSACTIONS_WORKLOAD_1);
        response.addPerformerCaps(Caps.TRANSACTIONS_SUPPORT_1);
        // [end]
        response.addSupportedApis(API.ASYNC);
        response.addPerformerCaps(Caps.CLUSTER_CONFIG_1);
        response.addPerformerCaps(Caps.CLUSTER_CONFIG_CERT);
        response.addPerformerCaps(Caps.CLUSTER_CONFIG_INSECURE);
        // Some observability options blocks changed name here
        // [if:3.2.0]
        //TODO: Marker for removed otel support
//        response.addPerformerCaps(Caps.OBSERVABILITY_1);
        // [end]
        response.addPerformerCaps(Caps.TIMING_ON_FAILED_OPS);
        response.setPerformerUserAgent("java-sdk");
    }

    @Override
    @Blocking
    public void clusterConnectionCreate(ClusterConnectionCreateRequest request,
                                        StreamObserver<ClusterConnectionCreateResponse> responseObserver) {
        try {
            var clusterConnectionId = request.getClusterConnectionId();
            // Need this callback as we have to configure hooks to do something with a Cluster that we haven't created yet.
            Supplier<ClusterConnection> getCluster = () -> clusterConnections.get(clusterConnectionId);
            var onClusterConnectionClose = new ArrayList<Runnable>();

            request.getTunablesMap().forEach((k, v) -> {
                logger.info("Setting cluster-level tunable {}={}", k, v);
                if (v != null) {
                    System.setProperty(k, v);
                }
            });

            onClusterConnectionClose.add(() -> {
                request.getTunablesMap().forEach((k, v) -> {
                    logger.info("Clearing cluster-level tunable {}", k);
                    if (v != null) {
                        System.clearProperty(k);
                    }
                });
            });

            var clusterEnvironment = OptionsUtil.convertClusterConfig(request, getCluster, onClusterConnectionClose);

            ClusterConnection connection;
            //TODO: Try to access config
            var qlusterEnabled = ConfigProvider.getConfig().getValue("quarformer.enableQluster", boolean.class);

            if (qlusterEnabled && clusterConnectionId.startsWith("defaultClusterConnection_")){
                logger.info("Using Quarkus Cluster for defaultConnections.");
                connection = new ClusterConnection(onClusterConnectionClose, quarkusCluster);
            } else {
                connection = new ClusterConnection(request.getClusterHostname(),
                        request.getClusterUsername(),
                        request.getClusterPassword(),
                        clusterEnvironment,
                        onClusterConnectionClose);
            }

            clusterConnections.put(clusterConnectionId, connection);
            logger.info("Created cluster connection {} for user {}, now have {}",
                    clusterConnectionId, request.getClusterUsername(), clusterConnections.size());

            // Fine to have a default and a per-test connection open, any more suggests a leak
            logger.info("Dumping {} cluster connections for resource leak troubleshooting:", clusterConnections.size());
            clusterConnections.forEach((key, value) -> logger.info("Cluster connection {} {}", key, value.username));

            responseObserver.onNext(ClusterConnectionCreateResponse.newBuilder()
                    .setClusterConnectionCount(clusterConnections.size())
                    .build());
            responseObserver.onCompleted();
        } catch (RuntimeException err) {
            logger.error("Operation failed during clusterConnectionCreate due to : " + err);
            responseObserver.onError(Status.ABORTED.withDescription(err.toString()).asException());
        }
    }

    @Override
    @Blocking
    public void clusterConnectionClose(ClusterConnectionCloseRequest request,
                                       StreamObserver<ClusterConnectionCloseResponse> responseObserver) {
        var cc = clusterConnections.get(request.getClusterConnectionId());
        cc.close();
        clusterConnections.remove(request.getClusterConnectionId());
        responseObserver.onNext(ClusterConnectionCloseResponse.newBuilder()
                .setClusterConnectionCount(clusterConnections.size())
                .build());
        responseObserver.onCompleted();
    }

    // [if:3.3.0]
    @Override
    @Blocking
    public void transactionCreate(TransactionCreateRequest request,
                                  StreamObserver<TransactionResult> responseObserver) {
        try {
            ClusterConnection connection = getClusterConnection(request.getClusterConnectionId());

            logger.info("Starting transaction on cluster connection {} created for user {}",
                    request.getClusterConnectionId(), connection.username);

            TransactionResult response;
            if (request.getApi() == API.DEFAULT) {
                response = TwoWayTransactionBlocking.run(connection, request, (TransactionCommandExecutor) null, false, spans);
            }
            else {
                response = TwoWayTransactionReactive.run(connection, request, spans);
            }

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (RuntimeException err) {
            logger.error("Operation failed during transactionCreate due to :  " + err);
            err.printStackTrace();
            responseObserver.onError(Status.ABORTED.withDescription(err.toString()).asException());
        }
    }
    // [end]

    @Override
    @Blocking
    public  void echo(EchoRequest request , StreamObserver<EchoResponse> responseObserver){
        try {
            logger.info("================ {} : {} ================ ", request.getTestName(), request.getMessage());
            responseObserver.onNext(EchoResponse.newBuilder().build());
            responseObserver.onCompleted();
        } catch (RuntimeException err) {
            logger.error("Echo of Test {} for message {} failed : {} " +request.getTestName(),request.getMessage(), err);
            responseObserver.onError(Status.ABORTED.withDescription(err.toString()).asException());
        }
    }

    @Override
    @Blocking
    public void disconnectConnections(DisconnectConnectionsRequest request, StreamObserver<DisconnectConnectionsResponse> responseObserver) {
        try {
            logger.info("Closing all {} connections from performer to cluster", clusterConnections.size());

            clusterConnections.forEach((key, value) -> value.close());
            clusterConnections.clear();

            responseObserver.onNext(DisconnectConnectionsResponse.newBuilder().build());
            responseObserver.onCompleted();
        } catch (RuntimeException err) {
            logger.error("Operation failed while closing cluster connections : " + err);
            responseObserver.onError(Status.ABORTED.withDescription(err.toString()).asException());
        }
    }

    // [if:3.3.0]
    @Override
    @Blocking
    public StreamObserver<TransactionStreamDriverToPerformer> transactionStream(
            StreamObserver<TransactionStreamPerformerToDriver> toTest) {
        var marshaller = new TwoWayTransactionMarshaller(clusterConnections, spans);

        return marshaller.run(toTest);
    }
    // [end]

    private static CollectionIdentifier collectionIdentifierFor(com.couchbase.client.protocol.transactions.DocId doc) {
        return new CollectionIdentifier(doc.getBucketName(), Optional.of(doc.getScopeName()), Optional.of(doc.getCollectionName()));
    }

    // [if:3.3.0]
    @Override
    @Blocking
    public void transactionCleanup(TransactionCleanupRequest request,
                                   StreamObserver<TransactionCleanupAttempt> responseObserver) {
        try {
            logger.info("Starting transaction cleanup attempt");
            // Only the KV timeout is used from this
            var config = TransactionsConfig.builder().build();
            var connection = getClusterConnection(request.getClusterConnectionId());
            var collection = collectionIdentifierFor(request.getAtr());
            connection.waitUntilReady(collection);
            var cleanupHooks = HooksUtil.configureCleanupHooks(request.getHookList(), () -> connection);
            var cleaner = new TransactionsCleaner(connection.core(), cleanupHooks);
            var logger = new CoreTransactionLogger(null, "");
            var merged = new CoreMergedTransactionConfig(config);

            Optional<ActiveTransactionRecordEntry> atrEntry = ActiveTransactionRecord.findEntryForTransaction(connection.core(),
                            collection,
                            request.getAtr().getDocId(),
                            request.getAttemptId(),
                            merged,
                            null,
                            logger)
                    .block();

            TransactionCleanupAttempt response;
            TransactionCleanupAttemptEvent result = null;

            if (atrEntry.isPresent()) {
                result = cleaner.cleanupATREntry(collection,
                                request.getAtrId(),
                                request.getAttemptId(),
                                atrEntry.get(),
                                false)
                        .block();
            }

            if (result != null) {
                response = ResultsUtil.mapCleanupAttempt(result, atrEntry);
            }
            else {
                // Can happen if 2+ cleanups are being executed concurrently
                response = TransactionCleanupAttempt.newBuilder()
                        .setSuccess(false)
                        .setAtr(request.getAtr())
                        .setAttemptId(request.getAttemptId())
                        .addLogs("Failed at performer to get ATR entry before running cleanupATREntry")
                        .build();
            }

            logger.info("Finished transaction cleanup attempt, success={}", response.getSuccess());

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (RuntimeException err) {
            logger.error("Operation failed during transactionCleanup due to : " + err);
            responseObserver.onError(Status.ABORTED.withDescription(err.toString()).asException());
        }
    }

    @Override
    @Blocking
    public void clientRecordProcess(ClientRecordProcessRequest request,
                                    StreamObserver<ClientRecordProcessResponse> responseObserver) {
        try {
            logger.info("Starting client record process attempt");

            var config = TransactionsConfig.builder().build();
            ClusterConnection connection = getClusterConnection(request.getClusterConnectionId());

            var collection = new CollectionIdentifier(request.getBucketName(),
                    Optional.of(request.getScopeName()),
                    Optional.of(request.getCollectionName()));

            connection.waitUntilReady(collection);

            ClientRecord cr = HooksUtil.configureClientRecordHooks(request.getHookList(), connection);

            ClientRecordProcessResponse.Builder response = ClientRecordProcessResponse.newBuilder();

            try {
                ClientRecordDetails result = cr.processClient(request.getClientUuid(),
                                collection,
                                config,
                                null)
                        .block();

                response.setSuccess(true)
                        .setNumActiveClients(result.numActiveClients())
                        .setIndexOfThisClient(result.indexOfThisClient())
                        .addAllExpiredClientIds(result.expiredClientIds())
                        .setNumExistingClients(result.numExistingClients())
                        .setNumExpiredClients(result.numExpiredClients())
                        .setOverrideActive(result.overrideActive())
                        .setOverrideEnabled(result.overrideEnabled())
                        .setOverrideExpires(result.overrideExpires())
                        .setCasNowNanos(result.casNow())
                        .setClientUuid(request.getClientUuid())
                        .build();
            }
            catch (RuntimeException err) {
                response.setSuccess(false);
            }

            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        } catch (RuntimeException err) {
            logger.error("Operation failed during clientRecordProcess due to : " + err);
            responseObserver.onError(Status.ABORTED.withDescription(err.toString()).asException());
        }
    }

    @Override
    @Blocking
    public void transactionSingleQuery(TransactionSingleQueryRequest request,
                                       StreamObserver<TransactionSingleQueryResponse> responseObserver) {
        try {
            var connection = getClusterConnection(request.getClusterConnectionId());

            logger.info("Performing single query transaction on cluster connection {} (user {})",
                    request.getClusterConnectionId(),
                    connection.username);

            TransactionSingleQueryResponse ret = SingleQueryTransactionExecutor.execute(request, connection, spans);

            responseObserver.onNext(ret);
            responseObserver.onCompleted();
        } catch (Throwable err) {
            logger.error("Operation failed during transactionSingleQuery due to : " + err.toString());
            responseObserver.onError(Status.ABORTED.withDescription(err.toString()).asException());
        }
    }

    public void cleanupSetFetch(CleanupSetFetchRequest request, StreamObserver<CleanupSetFetchResponse> responseObserver) {
        try {
            logger.info("BEV cleanupSetFetch() called");
            var connection = getClusterConnection(request.getClusterConnectionId());

            var cleanupSet = connection.core().transactionsCleanup().cleanupSet().stream()
                    .map(cs -> Collection.newBuilder()
                            .setBucketName(cs.bucket())
                            .setScopeName(cs.scope().orElse(DEFAULT_SCOPE))
                            .setCollectionName(cs.collection().orElse(DEFAULT_COLLECTION))
                            .build())
                    .collect(Collectors.toList());

            responseObserver.onNext(CleanupSetFetchResponse.newBuilder()
                    .setCleanupSet(CleanupSet.newBuilder()
                            .addAllCleanupSet(cleanupSet))
                    .build());
            responseObserver.onCompleted();
        } catch (Throwable err) {
            logger.error("Operation failed during cleanupSetFetch due to {}", err.toString());
            responseObserver.onError(Status.ABORTED.withDescription(err.toString()).asException());
        }
    }
    // [end]

    @Override
    @Blocking
    public void spanCreate(SpanCreateRequest request, StreamObserver<SpanCreateResponse> responseObserver) {
        var parent = request.hasParentSpanId()
                ? spans.get(request.getParentSpanId())
                : null;
        //TODO: Remove this
        logger.info("spanCreate with parentSpanId = {}", parent);

        var span = getClusterConnection(request.getClusterConnectionId())
                .cluster()
                .environment()
                .requestTracer()
                .requestSpan(request.getName(), parent);
        // RequestSpan interface finalised here
        // [if:3.1.6]
        request.getAttributesMap().forEach((k, v) -> {
            if (v.hasValueBoolean()) {
                span.attribute(k, v.getValueBoolean());
            }
            else if (v.hasValueLong()) {
                span.attribute(k, v.getValueLong());
            }
            else if (v.hasValueString()) {
                span.attribute(k, v.getValueString());
            }
            else throw new UnsupportedOperationException();
        });
        // [end]

        //Todo: Remove this
        logger.info("Putting entry into spans with key : {} and value : {}", request.getId(), span);
        spans.put(request.getId(), span);
        responseObserver.onNext(SpanCreateResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    @Blocking
    public void spanFinish(SpanFinishRequest request, StreamObserver<SpanFinishResponse> responseObserver) {
        // [if:3.1.6]
        spans.get(request.getId()).end();
        // [end]
        spans.remove(request.getId());
        responseObserver.onNext(SpanFinishResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    public static ClusterConnection getClusterConnection(@Nullable String clusterConnectionId) {
        //TODO: Quarkus cluster custom
//        if (clusterConnectionId.startsWith("default")){
//            logger.info("Using the Default ClusterConnection, meaning Quarkus-Injected Cluster.");
//        }
        return clusterConnections.get(clusterConnectionId);
    }

    @Override
    @Blocking
    public void run(com.couchbase.client.protocol.run.Request request,
                    StreamObserver<com.couchbase.client.protocol.run.Result> responseObserver) {
    super.run(request, responseObserver);
    }
}
