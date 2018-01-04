package io.netflix.titus.master.scheduler;

import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.fenzo.AsSoftConstraint;
import com.netflix.fenzo.ConstraintEvaluator;
import com.netflix.fenzo.VMTaskFitnessCalculator;
import com.netflix.fenzo.plugins.BalancedHostAttrConstraint;
import com.netflix.fenzo.plugins.ExclusiveHostConstraint;
import com.netflix.fenzo.plugins.UniqueHostAttrConstraint;
import io.netflix.titus.common.util.tuple.Pair;
import io.netflix.titus.master.config.MasterConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps V3 API constraint definitions into Fenzo model. The following constraint are available:
 * <ul>
 * <li>host</li>
 * <li>exclusiveHost</li>
 * <li>serverGroup</li>
 * <li>uniqueHost</li>
 * <li>zoneBalance</li>
 * </ul>
 */
@Singleton
public class V3ConstraintEvaluatorTransformer implements ConstraintEvaluatorTransformer<Pair<String, String>> {

    private static final Logger logger = LoggerFactory.getLogger(V3ConstraintEvaluatorTransformer.class);

    private static final int EXPECTED_NUM_ZONES = 3;

    private static final ExclusiveHostConstraint EXCLUSIVE_HOST_CONSTRAINT = new ExclusiveHostConstraint();

    private final MasterConfiguration config;

    @Inject
    public V3ConstraintEvaluatorTransformer(MasterConfiguration config) {
        this.config = config;
    }

    @Override
    public Optional<ConstraintEvaluator> hardConstraint(Pair<String, String> hardConstraint, Supplier<Set<String>> activeTasksGetter) {
        String name = hardConstraint.getLeft();
        String value = hardConstraint.getRight();
        switch (name.toLowerCase()) {
            case "exclusivehost":
                return "true".equals(value) ? Optional.of(EXCLUSIVE_HOST_CONSTRAINT) : Optional.empty();
            case "uniquehost":
                return "true".equals(value) ? Optional.of(new UniqueHostAttrConstraint(s -> activeTasksGetter.get())) : Optional.empty();
            case "zonebalance":
                return "true".equals(value)
                        ? Optional.of(new BalancedHostAttrConstraint(s -> activeTasksGetter.get(), config.getHostZoneAttributeName(), EXPECTED_NUM_ZONES))
                        : Optional.empty();
            case "host":
            case "servergroup":
        }
        logger.error("Unknown or not supported job hard constraint: %s", name);
        return Optional.empty();
    }

    @Override
    public Optional<VMTaskFitnessCalculator> softConstraint(Pair<String, String> softConstraints, Supplier<Set<String>> activeTasksGetter) {
        String name = softConstraints.getLeft();
        String value = softConstraints.getRight();
        switch (name.toLowerCase()) {
            case "exclusivehost":
                return "true".equals(value) ? Optional.of(AsSoftConstraint.get(EXCLUSIVE_HOST_CONSTRAINT)) : Optional.empty();
            case "uniquehost":
                return "true".equals(value) ? Optional.of(AsSoftConstraint.get(new UniqueHostAttrConstraint(s -> activeTasksGetter.get()))) : Optional.empty();
            case "zonebalance":
                return "true".equals(value)
                        ? Optional.of(new BalancedHostAttrConstraint(s -> activeTasksGetter.get(), config.getHostZoneAttributeName(), EXPECTED_NUM_ZONES).asSoftConstraint())
                        : Optional.empty();
            case "host":
            case "servergroup":
        }
        logger.error("Unknown or not supported job hard constraint: %s", name);
        return Optional.empty();
    }
}