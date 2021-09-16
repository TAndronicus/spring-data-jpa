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
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.lang.Nullable;

import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.JPQLQuery;

/**
 * Immutable implementation of {@link FetchableFluentQuery} based on a Querydsl {@link Predicate}. All methods that
 * return a {@link FetchableFluentQuery} will return a new instance, not the original.
 *
 * @param <S> Domain type
 * @param <R> Result type
 * @author Greg Turnquist
 * @author Michael J. Simons
 * @since 2.6
 */
public class FetchableFluentQueryByPredicate<S, R> extends FluentQuerySupport<R> implements FetchableFluentQuery<R> {

	private Predicate predicate;
	private Function<Sort, JPQLQuery<S>> finder;
	private BiFunction<Sort, Pageable, JPQLQuery<S>> pagedFinder;
	private Function<Predicate, Long> countOperation;
	private Function<Predicate, Boolean> existsOperation;

	public FetchableFluentQueryByPredicate(Predicate predicate, Class<R> resultType, Function<Sort, JPQLQuery<S>> finder,
			BiFunction<Sort, Pageable, JPQLQuery<S>> pagedFinder, Function<Predicate, Long> countOperation,
			Function<Predicate, Boolean> existsOperation) {
		this(predicate, resultType, Sort.unsorted(), null, finder, pagedFinder, countOperation, existsOperation);
	}

	private FetchableFluentQueryByPredicate(Predicate predicate, Class<R> resultType, Sort sort,
			@Nullable Collection<String> properties, Function<Sort, JPQLQuery<S>> finder,
			BiFunction<Sort, Pageable, JPQLQuery<S>> pagedFinder, Function<Predicate, Long> countOperation,
			Function<Predicate, Boolean> existsOperation) {

		super(resultType, sort, properties);
		this.predicate = predicate;
		this.finder = finder;
		this.pagedFinder = pagedFinder;
		this.countOperation = countOperation;
		this.existsOperation = existsOperation;
	}

	@Override
	public FetchableFluentQuery<R> sortBy(Sort sort) {

		return new FetchableFluentQueryByPredicate<>(this.predicate, this.resultType, this.sort.and(sort), this.properties,
				this.finder, this.pagedFinder, this.countOperation, this.existsOperation);
	}

	@Override
	public <NR> FetchableFluentQuery<NR> as(Class<NR> resultType) {

		return new FetchableFluentQueryByPredicate<>(this.predicate, resultType, this.sort, this.properties, this.finder,
				this.pagedFinder, this.countOperation, this.existsOperation);
	}

	@Override
	public FetchableFluentQuery<R> project(Collection<String> properties) {

		return new FetchableFluentQueryByPredicate<>(this.predicate, this.resultType, this.sort,
				mergeProperties(properties), this.finder, this.pagedFinder, this.countOperation, this.existsOperation);
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
		return (List<R>) this.finder.apply(this.sort).fetchResults().getResults();
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
		return this.countOperation.apply(this.predicate);
	}

	@Override
	public boolean exists() {
		return this.existsOperation.apply(this.predicate);
	}

	private Page<R> readPage(Pageable pageable) {

		JPQLQuery<S> query = this.pagedFinder.apply(this.sort, pageable);

		return (Page<R>) PageableExecutionUtils.getPage(query.fetchResults().getResults(), pageable,
				() -> this.countOperation.apply(this.predicate));
	}
}
