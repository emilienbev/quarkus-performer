package com.couchbase;

import com.couchbase.client.core.logging.LogRedaction;
import com.couchbase.client.core.logging.RedactionLevel;
import io.quarkus.grpc.GrpcService;
import reactor.core.publisher.Hooks;

import java.util.Arrays;
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
    }
}
