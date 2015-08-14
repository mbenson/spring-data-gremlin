package org.springframework.data.gremlin.schema.generator;

import com.tinkerpop.blueprints.Direction;
import org.apache.commons.lang3.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.gremlin.annotation.*;
import org.springframework.data.gremlin.schema.GremlinSchema;
import org.springframework.data.gremlin.schema.property.GremlinPropertyFactory;
import org.springframework.data.gremlin.schema.property.encoder.GremlinPropertyEncoder;
import org.springframework.data.gremlin.utils.GenericsUtil;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Default {@link SchemaGenerator} using Java reflection along with annotations defined in {@link org.springframework.data.gremlin.annotation}.
 * <p>
 * This class can be extended for custom generation.
 * </p>
 *
 * @author Gman
 */
public class CoreSchemaGenerator extends DefaultSchemaGenerator implements AnnotatedSchemaGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoreSchemaGenerator.class);

    public CoreSchemaGenerator() {
        super();
    }

    public CoreSchemaGenerator(GremlinPropertyEncoder idEncoder) {
        super(idEncoder);
    }

    public CoreSchemaGenerator(GremlinPropertyEncoder idEncoder, GremlinPropertyFactory propertyFactory) {
        super(idEncoder, propertyFactory);
    }

    protected Index.IndexType getIndexType(Field field) {
        Index index = AnnotationUtils.getAnnotation(field, Index.class);
        if (index != null) {
            return index.type();
        } else {
            return Index.IndexType.NONE;
        }
    }

    protected String getIndexName(Field field) {
        return null;
    }

    protected boolean shouldProcessField(GremlinSchema schema, Field field) {
        boolean shouldProcessField = super.shouldProcessField(schema, field);

        boolean noTransientAnnotation = AnnotationUtils.getAnnotation(field, Ignore.class) == null;
        return shouldProcessField && noTransientAnnotation;
    }

    protected Field getIdField(Class<?> cls) throws SchemaGeneratorException {
        final Field[] idFields = { null };

        ReflectionUtils.doWithFields(cls, new ReflectionUtils.FieldCallback() {
            @Override
            public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {

                Id id = AnnotationUtils.getAnnotation(field, Id.class);
                if (id != null) {
                    idFields[0] = field;
                }
            }
        });
        if (idFields[0] == null) {
            idFields[0] = super.getIdField(cls);
        }

        if (idFields[0] == null) {
            throw new SchemaGeneratorException("Cannot generate schema as there is no ID field. You must have a field of type Long or String annotated with @Id or named 'id'.");
        }

        return idFields[0];

    }

    protected Class<?> getEnumType(Field field) {
        Class<?> type = String.class;
        Enumerated enumerated = AnnotationUtils.getAnnotation(field, Enumerated.class);
        if (enumerated != null) {
            return enumerated.value().getType();
        }
        return type;
    }

    protected boolean isPropertyIndexed(Field field) {
        return AnnotationUtils.getAnnotation(field, Index.class) != null;
    }

    protected boolean isSpatialLatitudeIndex(Field field) {
        Index index = AnnotationUtils.getAnnotation(field, Index.class);
        if (index != null) {
            return index.type() == Index.IndexType.SPATIAL_LATITUDE;
        }
        return false;
    }

    protected boolean isSpatialLongitudeIndex(Field field) {
        Index index = AnnotationUtils.getAnnotation(field, Index.class);
        if (index != null) {
            return index.type() == Index.IndexType.SPATIAL_LONGITUDE;
        }
        return false;
    }

    protected boolean isPropertyUnique(Field field) {
        Index index = AnnotationUtils.getAnnotation(field, Index.class);
        if (index != null) {
            return index.type() == Index.IndexType.UNIQUE;
        }
        return false;
    }

    protected String getPropertyName(Field field, Field rootEmbeddedField) {
        Property property = AnnotationUtils.getAnnotation(field, Property.class);

        if (rootEmbeddedField != null) {

            PropertyOverride override = checkPropertyOverrides(rootEmbeddedField, field);
            if (override != null) {
                property = override.property();
            }
        }

        String annotationName = null;

        if (property != null) {
            annotationName = !StringUtils.isEmpty(property.value()) ? property.value() : property.name();
        } else {
            Link relatedTo = AnnotationUtils.getAnnotation(field, Link.class);
            if (relatedTo != null) {

                annotationName = !StringUtils.isEmpty(relatedTo.value()) ? relatedTo.value() : relatedTo.name();

            } else {
                LinkVia relatedToVia = AnnotationUtils.getAnnotation(field, LinkVia.class);
                if (relatedToVia != null) {
                    if (isAdjacentField(field.getType(), field)) {
                        String adjacentName = getVertexName(field.getType());
                        if (!adjacentName.equals(field.getType().getName())) {
                            annotationName = adjacentName;
                        } else {

                            annotationName = !StringUtils.isEmpty(relatedToVia.value()) ? relatedToVia.value() : relatedToVia.name();
                        }
                    }
                }
            }
        }

        String propertyName = !StringUtils.isEmpty(annotationName) ? annotationName : super.getPropertyName(field, rootEmbeddedField);

        return propertyName;
    }

    private PropertyOverride checkPropertyOverrides(Field embeddedField, Field field) {

        PropertyOverride propertyOverride = null;
        Embed embed = AnnotationUtils.getAnnotation(embeddedField, Embed.class);
        if (embed != null) {
            for (PropertyOverride po : embed.propertyOverrides()) {
                propertyOverride = checkPropertyOverride(field, po);
                if (propertyOverride != null) {
                    break;
                }
            }
        }
        return propertyOverride;
    }

    private PropertyOverride checkPropertyOverride(Field embeddedField, Field field) {

        PropertyOverride propertyOverride = embeddedField.getAnnotation(PropertyOverride.class);
        return checkPropertyOverride(field, propertyOverride);
    }

    private PropertyOverride checkPropertyOverride(Field field, PropertyOverride propertyOverride) {
        if (propertyOverride != null && !StringUtils.isEmpty(propertyOverride.name()) && propertyOverride.property() != null) {
            if (field.getName().equals(propertyOverride.name())) {
                return propertyOverride;
            }
        }
        return null;
    }

    protected boolean isEmbeddedField(Class<?> cls, Field field) {
        return super.isEmbeddedField(cls, field) && AnnotationUtils.getAnnotation(field, Embed.class) != null;
    }

    protected boolean isLinkField(Class<?> cls, Field field) {
        return super.isLinkField(cls, field) && (AnnotationUtils.getAnnotation(field, Link.class) != null);
    }

    protected boolean isLinkViaField(Class<?> cls, Field field) {
        return super.isLinkViaField(cls, field) && (AnnotationUtils.getAnnotation(field, LinkVia.class) != null);
    }

    protected boolean isAdjacentField(Class<?> cls, Field field) {
        return isVertexClass(cls) && (AnnotationUtils.getAnnotation(field, ToVertex.class) != null || AnnotationUtils.getAnnotation(field, FromVertex.class) != null);
    }

    protected boolean isAdjacentOutward(Class<?> cls, Field field) {
        FromVertex startNode = AnnotationUtils.getAnnotation(field, FromVertex.class);
        if (startNode != null) {
            return false;
        }

        ToVertex endNode = AnnotationUtils.getAnnotation(field, ToVertex.class);
        if (endNode != null) {
            return true;
        }

        return true;
    }

    protected boolean isLinkOutward(Class<?> cls, Field field) {
        Link relatedTo = AnnotationUtils.getAnnotation(field, Link.class);
        if (relatedTo != null) {
            return relatedTo.direction() == Direction.OUT;
        }
        FromVertex startNode = AnnotationUtils.getAnnotation(field, FromVertex.class);
        if (startNode != null) {
            return true;
        }

        ToVertex endNode = AnnotationUtils.getAnnotation(field, ToVertex.class);
        if (endNode != null) {
            return false;
        }

        return true;
    }

    protected boolean isCollectionField(Class<?> cls, Field field) {
        return super.isCollectionField(cls, field) && AnnotationUtils.getAnnotation(field, Link.class) != null;
    }

    protected boolean isCollectionViaField(Class<?> cls, Field field) {
        return super.isCollectionViaField(cls, field) && AnnotationUtils.getAnnotation(field, LinkVia.class) != null;
    }

    /**
     * Returns the Vertex name. By default the Class' simple name is used.
     *
     * @param clazz The Class to find the name of
     * @return The vertex name of the class
     */
    protected String getVertexName(Class<?> clazz) {
        Vertex vertex = AnnotationUtils.getAnnotation(clazz, Vertex.class);
        String annotationName = null;
        if (vertex != null) {
            annotationName = !StringUtils.isEmpty(vertex.value()) ? vertex.value() : vertex.name();
        } else {
            Edge edge = AnnotationUtils.getAnnotation(clazz, Edge.class);
            if (edge != null) {
                annotationName = !StringUtils.isEmpty(edge.value()) ? edge.value() : edge.name();
            }
        }

        return !StringUtils.isEmpty(annotationName) ? annotationName : super.getVertexName(clazz);
    }

    @Override
    public Class<? extends Annotation> getVertexAnnotationType() {
        return Vertex.class;
    }

    @Override
    public Class<? extends Annotation> getEmbeddedAnnotationType() {
        return Embeddable.class;
    }

    @Override
    public Class<? extends Annotation> getEdgeAnnotationType() {
        return Edge.class;
    }
}