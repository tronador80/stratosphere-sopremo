package eu.stratosphere.sopremo.base;

import java.util.ArrayList;
import java.util.List;

import eu.stratosphere.sopremo.expressions.EvaluationExpression;
import eu.stratosphere.sopremo.operator.ElementaryOperator;
import eu.stratosphere.sopremo.pact.JsonCollector;
import eu.stratosphere.sopremo.pact.SopremoMap;
import eu.stratosphere.sopremo.type.IJsonNode;

/**
 * Splits a tuple explicitly into multiple outgoing tuples.<br>
 * This operator provides a means to emit more than one tuple in contrast to most other base operators.
 * 
 * @author Arvid Heise
 */
public class ValueSplit extends ElementaryOperator<ValueSplit> {
	private List<EvaluationExpression> projections = new ArrayList<EvaluationExpression>();

	public ValueSplit addProjection(EvaluationExpression... projections) {
		for (EvaluationExpression evaluationExpression : projections)
			this.projections.add(evaluationExpression);
		return this;
	}

	public static class Implementation extends SopremoMap {
		private List<EvaluationExpression> projections = new ArrayList<EvaluationExpression>();

		@Override
		protected void map(IJsonNode value, JsonCollector<IJsonNode> out) {
			for (EvaluationExpression projection : this.projections)
				out.collect(projection.evaluate(value));
		}
	}
}
