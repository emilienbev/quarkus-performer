/*
 * Copyright 2022 Couchbase, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// [skip:<3.3.0]
package javaperformer.transaction;

import com.couchbase.client.core.transaction.CoreTransactionAttemptContext;
import javaperformer.utils.HooksUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * These methods need to access package-private methods.
 */
public class ExpiryUtil {
    private ExpiryUtil() {}
    static Logger logger = LoggerFactory.getLogger(HooksUtil.class);

    public static boolean hasExpired(CoreTransactionAttemptContext ctx, String stage, Optional<String> docId) {
        //Method is private in the java client, so we use reflection to get it
        try {
            Method method = CoreTransactionAttemptContext.class.getDeclaredMethod("hasExpiredClientSide", String.class, Optional.class);
            method.setAccessible(true);
            return (boolean) method.invoke(ctx, stage, docId);
        } catch (Exception e) {
            logger.warn("Couldn't get private hasExpiredClientSide method from CoreTransactionAttemptContext with reflection. Exception: {}", e.toString());
            return false; //This will lead to false positives/negatives but won't break the performer
        }
    }
}