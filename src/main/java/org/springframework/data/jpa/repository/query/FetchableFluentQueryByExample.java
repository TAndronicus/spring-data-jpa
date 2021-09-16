/*
 * Copyright 2013-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.jpa.repository.query;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.persistence.TypedQuery;

import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.lang.Nullable;

/**
 * Immutable implementation of {@link FetchableFluentQuery} based on Query by {@link Example}. All methods that return a
 * {@link FetchableFluentQuery} will return a new instance, not the original.
 *
 * @param <S> Domain type
 * @param <R> Result type
 * @author Greg Turnquist
 * @author Michael J. Simons
 * @since 2.6
 */
public class FetchableFluentQueryByExample<S, R> extends FluentQuerySupport<R> implements FetchableFluentQuery<R> {

	private Example<S> example;
	private Function<Sort, TypedQuery<S>> finder;
	private Function<Example<S>, Long> countOperation;
	private Function<Example<S>, Boolean> existsOperation;

	public FetchableFluentQueryByExample(Example<S> example, Class<R> resultType, Function<Sort, TypedQuery<S>> finder,
			Function<Example<S>, Long> countOperation, Function<Example<S>, Boolean> existsOperation) {
		this(example, resultType, Sort.unsorted(), null, finder, countOperation, existsOperation);
	}

	private FetchableFluentQueryByExample(Example<S> example, Class<R> resultType, Sort sort,
			@Nullable Collection<String> properties, Function<Sort, TypedQuery<S>> finder,
			Function<Example<S>, Long> countOperation, Function<Example<S>, Boolean> existsOperation) {

		super(resultType, sort, properties);
		this.example = example;
		this.finder = finder;
		this.countOperation = countOperation;
		this.existsOperation = existsOperation;
	}

	@Override
	public FetchableFluentQuery<R> sortBy(Sort sort) {

		return new FetchableFluentQueryByExample<>(this.example, this.resultType, this.sort.and(sort), this.properties,
				this.finder, this.countOperation, this.existsOperation);
	}

	@Override
	public <NR> FetchableFluentQuery<NR> as(Class<NR> resultType) {

		return new FetchableFluentQueryByExample<>(this.example, resultType, this.sort, this.properties, this.finder,
				this.countOperation, this.existsOperation);
	}

	@Override
	public FetchableFluentQuery<R> project(Collection<String> properties) {

		return new FetchableFluentQueryByExample<>(this.example, this.resultType, this.sort, mergeProperties(properties),
				this.finder, this.countOperation, this.existsOperation);
	}

	@Override
	public R oneValue() {
		return firstValue();
	}

	@Override
	public R firstValue() {

		List<R> all = all();
		return all.isEmpty() ? null : all.get(0);
	}

	@Override
	public List<R> all() {
		return (List<R>) this.finder.apply(this.sort).getResultList();
	}

	@Override
	public Page<R> page(Pageable pageable) {
		return pageable.isUnpaged() ? new PageImpl<>(all()) : readPage(pageable);
	}

	@Override
	public Stream<R> stream() {
		return all().stream();
	}

	@Override
	public long count() {
		return this.countOperation.apply(example);
	}

	@Override
	public boolean exists() {
		return this.existsOperation.apply(example);
	}

	private Page<R> readPage(Pageable pageable) {

		TypedQuery<S> query = this.finder.apply(this.sort);

		if (pageable.isPaged()) {
			query.setFirstResult((int) pageable.getOffset());
			query.setMaxResults(pageable.getPageSize());
		}

		return (Page<R>) PageableExecutionUtils.getPage(query.getResultList(), pageable,
				() -> this.countOperation.apply(this.example));
	}
}
