/***********************************************************************************************************************
 *
 * Copyright (C) 2010 by the Stratosphere project (http://stratosphere.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 **********************************************************************************************************************/
package eu.stratosphere.sopremo.aggregation;

import eu.stratosphere.sopremo.type.IJsonNode;

/**
 * @author Arvid Heise
 */
public abstract class FixedTypeAssociativeAggregation<ElementType extends IJsonNode> extends
		AssociativeAggregation<ElementType> {
	public FixedTypeAssociativeAggregation(String name, ElementType initialAggregate) {
		super(name, initialAggregate);
	}

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.sopremo.aggregation.Aggregation#aggregate(eu.stratosphere.sopremo.type.IJsonNode)
	 */
	@Override
	public void aggregate(IJsonNode element) {
		this.aggregateInto(this.aggregator, element);
	}

	@Override
	public void initialize() {
		this.aggregator.copyValueFrom(this.initialAggregate);
	}

	protected abstract void aggregateInto(ElementType aggregator, IJsonNode element);

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.sopremo.aggregation.AssociativeAggregation#aggregate(eu.stratosphere.sopremo.type.IJsonNode,
	 * eu.stratosphere.sopremo.type.IJsonNode)
	 */
	@Override
	protected ElementType aggregate(ElementType aggregator, IJsonNode element) {
		return null;
	}
}