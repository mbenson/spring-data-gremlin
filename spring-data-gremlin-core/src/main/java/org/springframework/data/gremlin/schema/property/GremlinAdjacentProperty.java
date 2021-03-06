package org.springframework.data.gremlin.schema.property;

import com.tinkerpop.blueprints.Direction;
import org.springframework.data.gremlin.schema.property.mapper.GremlinAdjacentPropertyMapper;

/**
 * A {@link GremlinRelatedProperty} accessor for linked properties (one-to-one relationships).
 *
 * @author Gman
 */
public class GremlinAdjacentProperty<C> extends GremlinRelatedProperty<C> {

    public GremlinAdjacentProperty(Class<C> cls, String name, Direction direction) {
        super(cls, name, direction, new GremlinAdjacentPropertyMapper(), CARDINALITY.ONE_TO_ONE);
    }
}
