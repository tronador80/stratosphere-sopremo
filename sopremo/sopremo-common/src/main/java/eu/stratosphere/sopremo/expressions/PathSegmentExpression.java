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
package eu.stratosphere.sopremo.expressions;

import java.io.IOException;

import eu.stratosphere.sopremo.expressions.tree.ChildIterator;
import eu.stratosphere.sopremo.expressions.tree.NamedChildIterator;
import eu.stratosphere.sopremo.type.IJsonNode;

/**
 * @author Arvid Heise
 */
public abstract class PathSegmentExpression extends EvaluationExpression {
	private EvaluationExpression inputExpression = EvaluationExpression.VALUE;

	/**
	 * Returns the inputExpression.
	 * 
	 * @return the inputExpression
	 */
	public EvaluationExpression getInputExpression() {
		return this.inputExpression;
	}

	/**
	 * Sets the inputExpression to the specified value.
	 * 
	 * @param inputExpression
	 *        the inputExpression to set
	 */
	public void setInputExpression(EvaluationExpression inputExpression) {
		if (inputExpression == null)
			throw new NullPointerException("inputExpression must not be null");

		this.inputExpression = inputExpression;
	}

	/**
	 * Sets the inputExpression to the specified value.
	 * 
	 * @param inputExpression
	 *        the inputExpression to set
	 */
	public PathSegmentExpression withInputExpression(EvaluationExpression inputExpression) {
		this.setInputExpression(inputExpression);
		return this;
	}

	public PathSegmentExpression getLast() {
		PathSegmentExpression segment = this;
		while (segment.inputExpression != EvaluationExpression.VALUE && this.inputExpression instanceof PathSegmentExpression)
			segment = (PathSegmentExpression) this.inputExpression;
		return segment;
	}


	/**
	 * Sets the value of the node specified by this expression.
	 * 
	 * @param node
	 *        the node to change
	 * @param value
	 *        the value to set
	 * @return the node or a new node if the expression directly accesses the node
	 */
	public IJsonNode set(IJsonNode node, IJsonNode value) {
		if(getInputExpression() == EvaluationExpression.VALUE)
			return setSegment(node, value);
		setSegment(this.getInputExpression().evaluate(node), value);
		return node;
	}

	protected IJsonNode setSegment(IJsonNode node, IJsonNode value) {
		throw new UnsupportedOperationException(String.format(
			"Cannot change the value with expression %s of node %s to %s", this, node, value));
	}

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.sopremo.expressions.EvaluationExpression#clone()
	 */
	@Override
	public PathSegmentExpression clone() {
		return (PathSegmentExpression) super.clone();
	}

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.sopremo.expressions.EvaluationExpression#clone()
	 */
	public PathSegmentExpression cloneSegment() {
		EvaluationExpression originalInput = this.inputExpression;
		this.inputExpression = EvaluationExpression.VALUE;
		final PathSegmentExpression partialClone = clone();
		this.inputExpression = originalInput;
		return partialClone;
	}

	public PathSegmentExpression withTail(EvaluationExpression tail) {
		getLast().setInputExpression(tail);
		return this;
	}

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.sopremo.expressions.EvaluationExpression#evaluate(eu.stratosphere.sopremo.type.IJsonNode)
	 */
	@Override
	public IJsonNode evaluate(IJsonNode node) {
		return this.evaluateSegment(this.getInputExpression().evaluate(node));
	}

	protected abstract IJsonNode evaluateSegment(IJsonNode node);

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + this.inputExpression.hashCode();
		result = prime * result + this.segmentHashCode();
		return result;
	}

	protected abstract int segmentHashCode();

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (this.getClass() != obj.getClass())
			return false;
		PathSegmentExpression other = (PathSegmentExpression) obj;
		return this.equalsSameClass(other) && this.inputExpression.equals(other.inputExpression);
	}

	public boolean equalsThisSeqment(PathSegmentExpression obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (this.getClass() != obj.getClass())
			return false;
		return this.equalsSameClass(obj);
	}

	protected abstract boolean equalsSameClass(PathSegmentExpression other);

	protected void appendInputAsString(Appendable appendable) throws IOException {
		if (this.inputExpression != EvaluationExpression.VALUE)
			this.inputExpression.appendAsString(appendable);
	}

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.sopremo.expressions.ExpressionParent#iterator()
	 */
	@Override
	public ChildIterator iterator() {
		return this.namedChildIterator();
	}

	protected NamedChildIterator namedChildIterator() {
		return new NamedChildIterator("inputExpression") {
			@Override
			protected void set(int index, EvaluationExpression childExpression) {
				PathSegmentExpression.this.inputExpression = childExpression;
			}

			@Override
			protected EvaluationExpression get(int index) {
				return PathSegmentExpression.this.inputExpression;
			}
		};
	}
}
