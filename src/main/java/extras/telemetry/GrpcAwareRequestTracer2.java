package extras.telemetry;

import com.couchbase.client.core.cnc.RequestSpan;
import io.grpc.ManagedChannelBuilder;
public interface GrpcAwareRequestTracer2 {
    /**
     * Setup GRPC instrumentation on a given GRPC channel.
     */
    void registerGrpc(ManagedChannelBuilder<?> builder);

    /**
     * Puts `span` into ThreadLocalStorage, ready to be picked up by libraries that rely on that mechanism and can't be passed the span explicitly.
     * <p>
     * We require this as asynchronous mechanisms such as reactive and CompletableFuture do not play well with ThreadLocalStorage.
     */
    AutoCloseable activateSpan(RequestSpan span);
}
