package eu.stratosphere.sopremo.expressions;

import static eu.stratosphere.sopremo.type.JsonUtil.createArrayNode;
import org.junit.Assert;

import org.junit.Test;

import eu.stratosphere.sopremo.type.BooleanNode;
import eu.stratosphere.sopremo.type.IJsonNode;
import eu.stratosphere.sopremo.type.IntNode;
import eu.stratosphere.sopremo.type.MissingNode;
import eu.stratosphere.sopremo.type.TextNode;

public class TernaryExpressionTest extends EvaluableExpressionTest<TernaryExpression> {

	@Override
	protected TernaryExpression createDefaultInstance(final int index) {
		return new TernaryExpression(new ConstantExpression(BooleanNode.TRUE), new ConstantExpression(
			IntNode.valueOf(index)), new ConstantExpression(IntNode.valueOf(index)));
	}

	@Test
	public void shouldEvaluateIfExpIfClauseIsTrue() {
		TernaryExpression ternaryExpression = new TernaryExpression(new InputSelection(0),
			new ConstantExpression(TextNode.valueOf("if")), new ConstantExpression(TextNode.valueOf("else")));
		final IJsonNode result = ternaryExpression.evaluate(
			createArrayNode(BooleanNode.TRUE, BooleanNode.FALSE));

		Assert.assertEquals(TextNode.valueOf("if"), result);
	}

	@Test
	public void shouldEvaluateThenExpIfClauseIsTrue() {
		final IJsonNode result = new TernaryExpression(new InputSelection(1),
			new ConstantExpression(IntNode.valueOf(0)), new ConstantExpression(IntNode.valueOf(1))).evaluate(
			createArrayNode(BooleanNode.TRUE, BooleanNode.FALSE));

		Assert.assertEquals(IntNode.valueOf(1), result);
	}

	@Test
	public void shouldBeMissingIfThenExprIsEmpty() {
		final IJsonNode result = new TernaryExpression(new InputSelection(1),
			new ConstantExpression(IntNode.valueOf(0))).evaluate(
			createArrayNode(BooleanNode.TRUE, BooleanNode.FALSE));

		Assert.assertEquals(MissingNode.getInstance(), result);
	}

	@Test
	public void shouldEvaluateIntNodes() {
		final TernaryExpression ternaryExpression = new TernaryExpression(new ConstantExpression(IntNode.valueOf(0)),
			new ConstantExpression(IntNode.valueOf(0)));
		final IJsonNode result = ternaryExpression.evaluate(IntNode.valueOf(42));

		Assert.assertEquals(MissingNode.getInstance(), result);
	}

	@Test
	public void shouldEvaluateTextNodes() {
		final IJsonNode result = new TernaryExpression(new ConstantExpression(TextNode.valueOf("a")),
			new ConstantExpression(IntNode.valueOf(0))).evaluate(IntNode.valueOf(42));

		Assert.assertEquals(IntNode.valueOf(0), result);
	}
}
