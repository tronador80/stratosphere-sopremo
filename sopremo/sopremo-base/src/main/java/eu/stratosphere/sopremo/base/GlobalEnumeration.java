package eu.stratosphere.sopremo.base;

import java.io.IOException;

import eu.stratosphere.nephele.configuration.Configuration;
import eu.stratosphere.pact.generic.contract.Contract;
import eu.stratosphere.sopremo.AbstractSopremoType;
import eu.stratosphere.sopremo.EvaluationContext;
import eu.stratosphere.sopremo.expressions.EvaluationExpression;
import eu.stratosphere.sopremo.expressions.ObjectAccess;
import eu.stratosphere.sopremo.expressions.PathSegmentExpression;
import eu.stratosphere.sopremo.operator.ElementaryOperator;
import eu.stratosphere.sopremo.operator.InputCardinality;
import eu.stratosphere.sopremo.operator.Name;
import eu.stratosphere.sopremo.operator.Property;
import eu.stratosphere.sopremo.pact.JsonCollector;
import eu.stratosphere.sopremo.pact.SopremoMap;
import eu.stratosphere.sopremo.serialization.SopremoRecordLayout;
import eu.stratosphere.sopremo.type.IJsonNode;
import eu.stratosphere.sopremo.type.IObjectNode;
import eu.stratosphere.sopremo.type.IntNode;
import eu.stratosphere.sopremo.type.LongNode;
import eu.stratosphere.sopremo.type.ObjectNode;
import eu.stratosphere.sopremo.type.TextNode;

@Name(verb = "enumerate")
@InputCardinality(1)
public class GlobalEnumeration extends ElementaryOperator<GlobalEnumeration> {
	public static final EvaluationExpression AUTO_ENUMERATION = null;

	private EvaluationExpression enumerationExpression = AUTO_ENUMERATION;

	private IdGenerator idGenerator = IdGeneration.LONG.getGenerator();

	private String idFieldName = "_ID", valueFieldName = "value";

	public EvaluationExpression getEnumerationExpression() {
		return this.enumerationExpression;
	}

	public String getIdFieldName() {
		return this.idFieldName;
	}

	public IdGeneration getIdGeneration() {
		for (IdGeneration idGeneration : IdGeneration.values())
			if (idGeneration.getGenerator().equals(this.getIdGenerator()))
				return idGeneration;

		return null;
	}

	/**
	 * Returns the idGenerator.
	 * 
	 * @return the idGenerator
	 */
	public IdGenerator getIdGenerator() {
		return this.idGenerator;
	}

	public ObjectAccess getIdAccess() {
		return new ObjectAccess(this.idFieldName);
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * eu.stratosphere.sopremo.operator.ElementaryOperator#configureContract(eu.stratosphere.pact.generic.contract.Contract
	 * , eu.stratosphere.nephele.configuration.Configuration, eu.stratosphere.sopremo.EvaluationContext,
	 * eu.stratosphere.sopremo.serialization.SopremoRecordLayout)
	 */
	@Override
	protected void configureContract(Contract contract, Configuration stubConfiguration, EvaluationContext context,
			SopremoRecordLayout layout) {
		if (this.enumerationExpression == AUTO_ENUMERATION)
			this.enumerationExpression = new AutoProjection(this.idFieldName, this.valueFieldName);
		super.configureContract(contract, stubConfiguration, context, layout);
		if (this.enumerationExpression instanceof AutoProjection)
			this.enumerationExpression = AUTO_ENUMERATION;
	}

	@Property
	@Name(preposition = "by")
	public void setEnumerationExpression(final EvaluationExpression enumerationExpression) {
		if (enumerationExpression == null)
			throw new NullPointerException();

		this.enumerationExpression = enumerationExpression;
	}

	@Property
	@Name(preposition = "with key")
	public void setIdFieldName(final String enumerationFieldName) {
		if (enumerationFieldName == null)
			throw new NullPointerException();

		this.idFieldName = enumerationFieldName;
	}

	public GlobalEnumeration withIdFieldName(String enumerationFieldName) {
		this.setIdFieldName(enumerationFieldName);
		return this;
	}

	public GlobalEnumeration withValueFieldName(String valueFieldName) {
		this.setValueFieldName(valueFieldName);
		return this;
	}

	public GlobalEnumeration withEnumerationExpression(EvaluationExpression enumerationExpression) {
		this.setEnumerationExpression(enumerationExpression);
		return this;
	}

	public GlobalEnumeration withIdGeneration(IdGeneration idGeneration) {
		this.setIdGeneration(idGeneration);
		return this;
	}

	public enum IdGeneration {
		LONG(new LongGenerator()), STRING(new StringGenerator()), MAPPER(new MapperNumberGenerator());

		private final IdGenerator generator;

		private IdGeneration(IdGenerator generator) {
			this.generator = generator;
		}

		/**
		 * Returns the generator.
		 * 
		 * @return the generator
		 */
		public IdGenerator getGenerator() {
			return this.generator;
		}
	}

	public static interface IdGenerator {
		public void setup(int taskId, int numTasks);

		public IJsonNode generate(long localId);
	}

	public static abstract class AbstractIdGenerator extends AbstractSopremoType implements IdGenerator {
		/*
		 * (non-Javadoc)
		 * @see eu.stratosphere.util.IAppending#appendAsString(java.lang.Appendable)
		 */
		@Override
		public void appendAsString(Appendable appendable) throws IOException {
			appendable.append(getClass().getSimpleName());
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {
			return getClass().equals(obj.getClass());
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			return getClass().getSimpleName().hashCode();
		}
	}

	public static class LongGenerator extends AbstractIdGenerator {
		private final LongNode result = new LongNode();

		private long prefix;

		/*
		 * (non-Javadoc)
		 * @see eu.stratosphere.sopremo.base.GlobalEnumeration.IdGenerator#generate(long)
		 */
		@Override
		public IJsonNode generate(long localId) {
			this.result.setValue(localId + this.prefix);
			return this.result;
		}

		/*
		 * (non-Javadoc)
		 * @see eu.stratosphere.sopremo.base.GlobalEnumeration.IdGenerator#setup(int, int)
		 */
		@Override
		public void setup(int taskId, int numTasks) {
			int freeBits = Long.numberOfLeadingZeros(numTasks);
			this.prefix = taskId << (64 - freeBits);
		}
	}

	public static class MapperNumberGenerator extends AbstractIdGenerator {
		private final IntNode result = new IntNode();

		/*
		 * (non-Javadoc)
		 * @see eu.stratosphere.sopremo.base.GlobalEnumeration.IdGenerator#generate(long)
		 */
		@Override
		public IJsonNode generate(long localId) {
			return this.result;
		}

		/*
		 * (non-Javadoc)
		 * @see eu.stratosphere.sopremo.base.GlobalEnumeration.IdGenerator#setup(int, int)
		 */
		@Override
		public void setup(int taskId, int numTasks) {
			this.result.setValue(taskId);
		}
	}

	@Property
	@Name(preposition = "by")
	public void setIdGeneration(final IdGeneration idGeneration) {
		if (idGeneration == null)
			throw new NullPointerException("idGeneration must not be null");

		this.idGenerator = idGeneration.getGenerator();
	}

	/**
	 * Sets the idGenerator to the specified value.
	 * 
	 * @param idGenerator
	 *        the idGenerator to set
	 */
	public void setIdGenerator(IdGenerator idGenerator) {
		if (idGenerator == null)
			throw new NullPointerException("idGenerator must not be null");

		this.idGenerator = idGenerator;
	}

	public String getValueFieldName() {
		return this.valueFieldName;
	}

	@Property
	@Name(verb = "retain value in")
	public void setValueFieldName(String valueFieldName) {
		if (valueFieldName == null)
			throw new NullPointerException("valueFieldName must not be null");

		this.valueFieldName = valueFieldName;
	}

	/**
	 * Adds the id field if object; wraps the value into an object otherwise.
	 */
	static final class AutoProjection extends PathSegmentExpression {
		private final String idFieldName, valueFieldName;

		AutoProjection(String idFieldName, String valueFieldName) {
			this.idFieldName = idFieldName;
			this.valueFieldName = valueFieldName;
		}

		/**
		 * Initializes GlobalEnumeration.AutoProjection.
		 */
		AutoProjection() {
			this(null, null);
		}

		/*
		 * (non-Javadoc)
		 * @see
		 * eu.stratosphere.sopremo.expressions.PathSegmentExpression#setSegment(eu.stratosphere.sopremo.type.IJsonNode,
		 * eu.stratosphere.sopremo.type.IJsonNode)
		 */
		@Override
		protected IJsonNode setSegment(IJsonNode node, IJsonNode value) {
			if (node instanceof IObjectNode) {
				((IObjectNode) node).put(this.idFieldName, value);
				return node;
			}
			ObjectNode objectNode = new ObjectNode();
			objectNode.put(this.idFieldName, value);
			objectNode.put(this.valueFieldName, node);
			return objectNode;
		}

		/*
		 * (non-Javadoc)
		 * @see
		 * eu.stratosphere.sopremo.expressions.PathSegmentExpression#evaluateSegment(eu.stratosphere.sopremo.type.IJsonNode
		 * )
		 */
		@Override
		protected IJsonNode evaluateSegment(IJsonNode node) {
			return node;
		}

		/*
		 * (non-Javadoc)
		 * @see eu.stratosphere.sopremo.expressions.PathSegmentExpression#segmentHashCode()
		 */
		@Override
		protected int segmentHashCode() {
			return 0;
		}

		/*
		 * (non-Javadoc)
		 * @see
		 * eu.stratosphere.sopremo.expressions.PathSegmentExpression#equalsSameClass(eu.stratosphere.sopremo.expressions
		 * .PathSegmentExpression)
		 */
		@Override
		protected boolean equalsSameClass(PathSegmentExpression other) {
			return true;
		}
	}

	/**
	 * @author Arvid Heise
	 */
	public static final class StringGenerator extends AbstractIdGenerator {

		private final transient TextNode result = new TextNode();

		private int prefixLength;

		/*
		 * (non-Javadoc)
		 * @see eu.stratosphere.sopremo.base.GlobalEnumeration.IdGenerator#setup(int, int)
		 */
		@Override
		public void setup(int taskId, int numTasks) {
			this.result.setLength(0);
			this.result.append(taskId);
			this.result.append('_');
			this.prefixLength = this.result.length();
		}

		/*
		 * (non-Javadoc)
		 * @see eu.stratosphere.sopremo.base.GlobalEnumeration.IdGenerator#generate(long)
		 */
		@Override
		public IJsonNode generate(long localId) {
			this.result.setLength(this.prefixLength);
			this.result.append(localId);
			return this.result;
		}
	}

	public static class Implementation extends SopremoMap {
		private PathSegmentExpression enumerationExpression;

		private IdGenerator idGenerator;

		private long counter;

		@Override
		public void open(Configuration parameters) {
			super.open(parameters);
			this.counter = 0;
			this.idGenerator.setup(getRuntimeContext().getIndexOfThisSubtask(),
				getRuntimeContext().getNumberOfParallelSubtasks());
		}

		@Override
		protected void map(final IJsonNode value, final JsonCollector<IJsonNode> out) {
			final IJsonNode id = this.idGenerator.generate(this.counter++);
			out.collect(this.enumerationExpression.set(value, id));
		}
	}

}
