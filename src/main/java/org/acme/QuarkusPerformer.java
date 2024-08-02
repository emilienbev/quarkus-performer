package org.acme;

import com.couchbase.client.protocol.performer.PerformerCapsFetchResponse;
import com.couchbase.client.protocol.shared.API;
import core.CorePerformer;
import core.commands.SdkCommandExecutor;
import core.commands.TransactionCommandExecutor;
import core.perf.Counters;

import javax.annotation.Nullable;

public class QuarkusPerformer extends CorePerformer {
    @Nullable
    @Override
    protected SdkCommandExecutor executor(com.couchbase.client.protocol.run.Workloads workloads, Counters counters, API api) {
        return null;
    }

    @Nullable
    @Override
    protected TransactionCommandExecutor transactionsExecutor(com.couchbase.client.protocol.run.Workloads workloads, Counters counters) {
        return null;
    }

    @Override
    protected void customisePerformerCaps(PerformerCapsFetchResponse.Builder response) {

    }
}
