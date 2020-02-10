/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.titus.master.mesos.kubeapiserver.direct.model;

import java.util.Objects;

import io.kubernetes.client.models.V1Pod;

public class PodAddedEvent extends PodEvent {

    public PodAddedEvent(V1Pod pod) {
        super(pod);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PodAddedEvent that = (PodAddedEvent) o;
        return Objects.equals(pod, that.pod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pod);
    }

    @Override
    public String toString() {
        return "PodAddedEvent{" +
                "taskId=" + pod +
                "pod=" + pod +
                '}';
    }
}
