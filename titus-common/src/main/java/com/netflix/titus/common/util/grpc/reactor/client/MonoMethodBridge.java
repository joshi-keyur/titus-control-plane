/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.titus.common.util.grpc.reactor.client;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.google.protobuf.Empty;
import com.netflix.titus.common.util.grpc.GrpcToReactUtil;
import io.grpc.Deadline;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.grpc.stub.StreamObserver;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

class MonoMethodBridge<GRPC_STUB extends AbstractStub<GRPC_STUB>, CONTEXT> implements Function<Object[], Publisher> {

    private final Method grpcMethod;
    private final int grpcArgPos;
    private final int contextPos;
    private final BiFunction<GRPC_STUB, Optional<CONTEXT>, GRPC_STUB> grpcStubDecorator;
    private final GRPC_STUB grpcStub;
    private final boolean emptyToVoidReply;
    private final Duration timeout;
    private final Duration reactorTimeout;

    /**
     * If grpcArgPos is less then zero, it means no GRPC argument is provided, and instead {@link Empty} value should be used.
     * If contextPos is less then zero, it means the context value should be resolved as it is not passed directly by
     * the client.
     */
    MonoMethodBridge(Method reactMethod,
                     Method grpcMethod,
                     int grpcArgPos,
                     int contextPos,
                     BiFunction<GRPC_STUB, Optional<CONTEXT>, GRPC_STUB> grpcStubDecorator,
                     GRPC_STUB grpcStub,
                     Duration timeout) {
        this.grpcMethod = grpcMethod;
        this.grpcArgPos = grpcArgPos;
        this.contextPos = contextPos;
        this.grpcStubDecorator = grpcStubDecorator;
        this.grpcStub = grpcStub;
        this.emptyToVoidReply = GrpcToReactUtil.isEmptyToVoidResult(reactMethod, grpcMethod);
        this.timeout = timeout;
        this.reactorTimeout = Duration.ofMillis((long) (timeout.toMillis() * GrpcToReactUtil.RX_CLIENT_TIMEOUT_FACTOR));
    }

    @Override
    public Publisher apply(Object[] args) {
        return Mono.create(sink -> new MonoInvocation(sink, args)).timeout(reactorTimeout);
    }

    private class MonoInvocation {

        private MonoInvocation(MonoSink<Object> sink, Object[] args) {
            StreamObserver<Object> grpcStreamObserver = new ClientResponseObserver<Object, Object>() {
                @Override
                public void beforeStart(ClientCallStreamObserver requestStream) {
                    sink.onCancel(() -> requestStream.cancel("React subscription cancelled", null));
                }

                @Override
                public void onNext(Object value) {
                    if (emptyToVoidReply) {
                        sink.success();
                    } else {
                        sink.success(value);
                    }
                }

                @Override
                public void onError(Throwable error) {
                    sink.error(error);
                }

                @Override
                public void onCompleted() {
                    sink.success();
                }
            };

            Object[] grpcArgs = new Object[]{
                    grpcArgPos < 0 ? Empty.getDefaultInstance() : args[grpcArgPos],
                    grpcStreamObserver
            };

            GRPC_STUB invocationStub = handleCallMetadata(args)
                    .withDeadline(Deadline.after(timeout.toMillis(), TimeUnit.MILLISECONDS));

            try {
                grpcMethod.invoke(invocationStub, grpcArgs);
            } catch (Exception e) {
                sink.error(e);
            }
        }

        private GRPC_STUB handleCallMetadata(Object[] args) {
            Optional<CONTEXT> contextOptional = contextPos >= 0 ? (Optional<CONTEXT>) Optional.of(args[contextPos]) : Optional.empty();
            return grpcStubDecorator.apply(grpcStub, contextOptional);
        }
    }
}
