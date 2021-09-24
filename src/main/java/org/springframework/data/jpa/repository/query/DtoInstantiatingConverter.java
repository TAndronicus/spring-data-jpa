package org.springframework.data.jpa.repository.query;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.mapping.PersistentPropertyAccessor;
import org.springframework.data.mapping.PreferredConstructor;
import org.springframework.data.mapping.PreferredConstructor.Parameter;
import org.springframework.data.mapping.SimplePropertyHandler;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mapping.model.EntityInstantiator;
import org.springframework.data.mapping.model.EntityInstantiators;
import org.springframework.data.mapping.model.ParameterValueProvider;
import org.springframework.util.Assert;

public class DtoInstantiatingConverter implements Converter<Object, Object> {

	private final Class<?> targetType;
	private final MappingContext<? extends PersistentEntity<?, ?>, ? extends PersistentProperty<?>> context;
	private final EntityInstantiator instantiator;

	public DtoInstantiatingConverter(Class<?> dtoType,
			MappingContext<? extends PersistentEntity<?, ?>, ? extends PersistentProperty<?>> context,
			EntityInstantiators entityInstantiators) {

		Assert.notNull(dtoType, "DTO type must not be null!");
		Assert.notNull(context, "MappingContext must not be null!");
		Assert.notNull(entityInstantiators, "EntityInstantiators must not be null!");

		this.targetType = dtoType;
		this.context = context;
		PersistentEntity<?, ? extends PersistentProperty<?>> requiredPersistentEntity = context
				.getRequiredPersistentEntity(dtoType);
		this.instantiator = entityInstantiators.getInstantiatorFor(requiredPersistentEntity);
	}

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public Object convert(Object source) {

		if (targetType.isInterface()) {
			return source;
		}

		PersistentEntity<?, ?> sourceEntity = context.getRequiredPersistentEntity(source.getClass());
		PersistentPropertyAccessor<?> sourceAccessor = sourceEntity.getPropertyAccessor(source);
		PersistentEntity<?, ?> targetEntity = context.getRequiredPersistentEntity(targetType);
		PreferredConstructor<?, ? extends PersistentProperty<?>> constructor = targetEntity.getPersistenceConstructor();

		Object dto = instantiator.createInstance(targetEntity, new ParameterValueProvider() {

			@Override
			public Object getParameterValue(Parameter parameter) {
				return sourceAccessor.getProperty(sourceEntity.getPersistentProperty(parameter.getName()));
			}
		});

		PersistentPropertyAccessor<?> dtoAccessor = targetEntity.getPropertyAccessor(dto);

		targetEntity.doWithProperties((SimplePropertyHandler) property -> {

			if (constructor.isConstructorParameter(property)) {
				return;
			}

			dtoAccessor.setProperty(property,
					sourceAccessor.getProperty(sourceEntity.getPersistentProperty(property.getName())));
		});

		return dto;
	}
}
