/*
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
package org.jdbi.v3.core.array;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.Set;
import org.jdbi.v3.core.collector.JdbiCollectors;
import org.jdbi.v3.core.config.ConfigRegistry;
import org.jdbi.v3.core.generic.GenericTypes;
import org.jdbi.v3.core.mapper.ColumnMapper;
import org.jdbi.v3.core.mapper.ColumnMappers;
import org.jdbi.v3.core.mapper.QualifiedColumnMapperFactory;
import org.jdbi.v3.core.qualifier.QualifiedType;
import org.jdbi.v3.meta.Beta;

/**
 * Maps SQL array columns into Java arrays or other Java container types.
 * Supports any Java array type for which a {@link ColumnMapper} is registered
 * for the array element type. Supports any other container type for which a
 * {@link org.jdbi.v3.core.collector.CollectorFactory} is registered, and for which
 * a {@link ColumnMapper} is registered for the container element type.
 */
@Beta
public class QualifiedSqlArrayMapperFactory implements QualifiedColumnMapperFactory {
    @Override
    @SuppressWarnings("unchecked")
    public Optional<ColumnMapper<?>> build(QualifiedType qualifiedType, ConfigRegistry config) {
        Type type = qualifiedType.getType();
        Set<Annotation> qualifiers = qualifiedType.getQualifiers();
        final Class<?> erasedType = GenericTypes.getErasedType(type);

        if (erasedType.isArray()) {
            Class<?> elementType = erasedType.getComponentType();
            QualifiedType qualifiedElementType = QualifiedType.of(elementType, qualifiers);

            return elementTypeMapper(qualifiedElementType, config)
                    .map(elementMapper -> new ArrayColumnMapper(elementMapper, elementType));
        }

        JdbiCollectors collectorRegistry = config.get(JdbiCollectors.class);
        return (Optional) collectorRegistry.findFor(type)
                .flatMap(collector -> collectorRegistry.findElementTypeFor(type)
                        .flatMap(elementType -> elementTypeMapper(QualifiedType.of(elementType, qualifiers), config))
                        .map(elementMapper -> new CollectorColumnMapper(elementMapper, collector)));
    }

    private Optional<ColumnMapper<?>> elementTypeMapper(QualifiedType elementType, ConfigRegistry config) {
        Optional<ColumnMapper<?>> mapper = config.get(ColumnMappers.class).findFor(elementType);

        if (!mapper.isPresent() && elementType.getType() == Object.class) {
            return Optional.of((rs, num, context) -> rs.getObject(num));
        }

        return mapper;
    }
}
