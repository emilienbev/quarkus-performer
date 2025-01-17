package com.couchbase;


import com.couchbase.client.core.logging.LogRedaction;
import com.couchbase.client.core.logging.RedactionLevel;
import com.couchbase.client.core.transaction.forwards.CoreTransactionsExtension;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.performer.core.commands.SdkCommandExecutor;
import com.couchbase.client.performer.core.commands.TransactionCommandExecutor;
import com.couchbase.client.performer.core.metrics.MetricsReporter;
import com.couchbase.client.performer.core.perf.Counters;
import com.couchbase.client.performer.core.perf.HorizontalScalingThread;
import com.couchbase.client.performer.core.perf.PerRun;
import com.couchbase.client.performer.core.perf.WorkloadStreamingThread;
import com.couchbase.client.performer.core.perf.WorkloadsRunner;
import com.couchbase.client.protocol.observability.SpanCreateRequest;
import com.couchbase.client.protocol.observability.SpanCreateResponse;
import com.couchbase.client.protocol.observability.SpanFinishRequest;
import com.couchbase.client.protocol.observability.SpanFinishResponse;
import com.couchbase.client.protocol.performer.Caps;
import com.couchbase.client.protocol.performer.PerformerCapsFetchRequest;
import com.couchbase.client.protocol.performer.PerformerCapsFetchResponse;
import com.couchbase.client.protocol.shared.API;
import com.couchbase.client.protocol.shared.ClusterConnectionCreateRequest;
import com.couchbase.client.protocol.shared.ClusterConnectionCreateResponse;
import com.couchbase.client.protocol.shared.DisconnectConnectionsRequest;
import com.couchbase.client.protocol.shared.DisconnectConnectionsResponse;
import com.couchbase.client.protocol.shared.EchoRequest;
import com.couchbase.client.protocol.shared.EchoResponse;
import com.couchbase.client.protocol.streams.CancelRequest;
import com.couchbase.client.protocol.streams.CancelResponse;
import com.couchbase.client.protocol.streams.RequestItemsRequest;
import com.couchbase.client.protocol.streams.RequestItemsResponse;
import com.couchbase.client.protocol.transactions.ClientRecordProcessRequest;
import com.couchbase.client.protocol.transactions.ClientRecordProcessResponse;
import com.couchbase.client.protocol.transactions.TransactionCleanupAttempt;
import com.couchbase.client.protocol.transactions.TransactionCleanupRequest;
import com.couchbase.client.protocol.transactions.TransactionCreateRequest;
import com.couchbase.client.protocol.transactions.TransactionResult;
import com.couchbase.client.protocol.transactions.TransactionSingleQueryRequest;
import com.couchbase.client.protocol.transactions.TransactionSingleQueryResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.quarkus.grpc.GrpcService;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import reactor.core.publisher.Hooks;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

@GrpcService
public class QuarkusPerformer extends JavaPerformer {

    static {
        LogRedaction.setRedactionLevel(RedactionLevel.PARTIAL);
        System.setProperty("com.couchbase.transactions.debug.lock", "true");
        System.setProperty("com.couchbase.transactions.debug.monoBridge", "false");
        Hooks.onErrorDropped(err -> {
            // We intentionally don't set globalError here.
            // In a reactive chain, if one operation fails, any parallel operations are cancelled.
            // If those operations subsuquently hit an error, it has nowhere to go (as the original
            // chain has already had an error raised on it), and so it's raised on the global error
            // handle (e.g. here).  Though unfortunate, this is standard reactor UX, and shouldn't
            // be regarded as a test failure.
            logger.info("Async hook drop (probably fine, will happen if multiple concurrent operations in a reactor chain fail): {}\n\t{}",
                    err.toString(), Arrays.stream(err.getStackTrace()).map(StackTraceElement::toString).collect(Collectors.joining("\n\t")));
        });
        CoreTransactionsExtension ext = CoreTransactionsExtension.EXT_QUERY;
        System.out.println(ext.name());
    }
    @Override
    @Blocking
    protected SdkCommandExecutor executor(com.couchbase.client.protocol.run.Workloads workloads, Counters counters, API api) {
        return super.executor(workloads, counters, api);
    }

    @Override
    @Blocking
    protected TransactionCommandExecutor transactionsExecutor(com.couchbase.client.protocol.run.Workloads workloads, Counters counters) {
        return super.transactionsExecutor(workloads, counters);
    }

    @Override
    @Blocking
    protected void customisePerformerCaps(PerformerCapsFetchResponse.Builder response) {
        super.customisePerformerCaps(response);
    }

    @Override
    @Blocking
    public void clusterConnectionCreate(ClusterConnectionCreateRequest request,
                                        StreamObserver<ClusterConnectionCreateResponse> responseObserver) {
       super.clusterConnectionCreate(request, responseObserver);
    }

    @Override
    @Blocking
    public void transactionCreate(TransactionCreateRequest request,
                                  StreamObserver<TransactionResult> responseObserver) {
        super.transactionCreate(request, responseObserver);
    }

    @Override
    @Blocking
    public  void echo(EchoRequest request , StreamObserver<EchoResponse> responseObserver){
        super.echo(request, responseObserver);
    }

    @Override
    @Blocking
    public void disconnectConnections(DisconnectConnectionsRequest request, StreamObserver<DisconnectConnectionsResponse> responseObserver) {
        super.disconnectConnections(request, responseObserver);
    }

    @Override
    @Blocking
    public void transactionCleanup(TransactionCleanupRequest request,
                                   StreamObserver<TransactionCleanupAttempt> responseObserver) {
        super.transactionCleanup(request, responseObserver);
    }

    @Override
    @Blocking
    public void clientRecordProcess(ClientRecordProcessRequest request,
                                    StreamObserver<ClientRecordProcessResponse> responseObserver) {
        super.clientRecordProcess(request, responseObserver);
    }

    @Override
    @Blocking
    public void transactionSingleQuery(TransactionSingleQueryRequest request,
                                       StreamObserver<TransactionSingleQueryResponse> responseObserver) {
        super.transactionSingleQuery(request, responseObserver);
    }

    @Override
    @Blocking
    public void spanCreate(SpanCreateRequest request, StreamObserver<SpanCreateResponse> responseObserver) {
        super.spanCreate(request, responseObserver);
    }

    @Override
    @Blocking
    public void spanFinish(SpanFinishRequest request, StreamObserver<SpanFinishResponse> responseObserver) {
        super.spanFinish(request, responseObserver);
    }

    //CorePerformer methods

    @Override
    @Blocking
    public void performerCapsFetch(PerformerCapsFetchRequest request, StreamObserver<PerformerCapsFetchResponse> responseObserver) {
        super.performerCapsFetch(request, responseObserver);
    }

    @Override
    @Blocking
    public void run(com.couchbase.client.protocol.run.Request request,
                    StreamObserver<com.couchbase.client.protocol.run.Result> responseObserver) {
        super.run(request, responseObserver);
    }

    @Override
    @Blocking
    public void streamCancel(CancelRequest request, StreamObserver<CancelResponse> responseObserver) {
        super.streamCancel(request, responseObserver);
    }

    @Override
    @Blocking
    public void streamRequestItems(RequestItemsRequest request, StreamObserver<RequestItemsResponse> responseObserver) {
        super.streamRequestItems(request, responseObserver);
    }
}
