package eu.stratosphere.sopremo.query;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.io.File;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.antlr.runtime.BitSet;
import org.antlr.runtime.IntStream;
import org.antlr.runtime.MismatchedTokenException;
import org.antlr.runtime.MissingTokenException;
import org.antlr.runtime.Parser;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.RecognizerSharedState;
import org.antlr.runtime.Token;
import org.antlr.runtime.TokenStream;
import org.antlr.runtime.UnwantedTokenException;

import com.google.common.base.CharMatcher;

import eu.stratosphere.nephele.fs.Path;
import eu.stratosphere.sopremo.CoreFunctions;
import eu.stratosphere.sopremo.EvaluationContext;
import eu.stratosphere.sopremo.MathFunctions;
import eu.stratosphere.sopremo.SecondOrderFunctions;
import eu.stratosphere.sopremo.SopremoEnvironment;
import eu.stratosphere.sopremo.expressions.CoerceExpression;
import eu.stratosphere.sopremo.expressions.EvaluationExpression;
import eu.stratosphere.sopremo.expressions.FunctionCall;
import eu.stratosphere.sopremo.function.Callable;
import eu.stratosphere.sopremo.function.ExpressionFunction;
import eu.stratosphere.sopremo.function.MacroBase;
import eu.stratosphere.sopremo.function.SopremoFunction;
import eu.stratosphere.sopremo.io.Sink;
import eu.stratosphere.sopremo.io.SopremoFormat;
import eu.stratosphere.sopremo.operator.Operator;
import eu.stratosphere.sopremo.operator.SopremoPlan;
import eu.stratosphere.sopremo.packages.IConstantRegistry;
import eu.stratosphere.sopremo.packages.IFunctionRegistry;
import eu.stratosphere.sopremo.packages.IRegistry;
import eu.stratosphere.sopremo.packages.ITypeRegistry;
import eu.stratosphere.sopremo.pact.SopremoUtil;
import eu.stratosphere.sopremo.query.ConfObjectInfo.ConfObjectIndexedPropertyInfo;
import eu.stratosphere.sopremo.query.ConfObjectInfo.ConfObjectPropertyInfo;
import eu.stratosphere.sopremo.type.IJsonNode;

public abstract class AbstractQueryParser extends Parser implements ParsingScope {
	/**
	 * 
	 */
	public static final String DEFAULT_ERROR_MESSAGE = "Cannot parse script";

	private PackageManager packageManager = new PackageManager();

	protected InputSuggestion inputSuggestion = new InputSuggestion().withMaxSuggestions(3).withMinSimilarity(0.5);

	protected List<Sink> sinks = new ArrayList<Sink>();

	private SopremoPlan currentPlan = new SopremoPlan();

	public AbstractQueryParser(TokenStream input, RecognizerSharedState state) {
		super(input, state);
		this.init();
	}

	public AbstractQueryParser(TokenStream input) {
		super(input);
		this.init();
	}

	/**
	 * 
	 */
	private void init() {
		this.currentPlan.setContext(new EvaluationContext(this.getFunctionRegistry(), this.getConstantRegistry(),
			this.getTypeRegistry(),
			new HashMap<String, Object>()));
		this.packageManager.getFunctionRegistry().put(CoreFunctions.class);
		this.packageManager.getFunctionRegistry().put(MathFunctions.class);
		this.packageManager.getFunctionRegistry().put(SecondOrderFunctions.class);
		SopremoEnvironment.getInstance().setEvaluationContext(this.getContext());
	}

	@Override
	public IConfObjectRegistry<Operator<?>> getOperatorRegistry() {
		return this.packageManager.getOperatorRegistry();
	}

	@Override
	public IConstantRegistry getConstantRegistry() {
		return this.packageManager.getConstantRegistry();
	}

	@Override
	public IFunctionRegistry getFunctionRegistry() {
		return this.packageManager.getFunctionRegistry();
	}

	@Override
	public ITypeRegistry getTypeRegistry() {
		return this.packageManager.getTypeRegistry();
	}

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.sopremo.query.ParsingScope#getFileFormatRegistry()
	 */
	@Override
	public IConfObjectRegistry<SopremoFormat> getFileFormatRegistry() {
		return this.packageManager.getFileFormatRegistry();
	}

	/**
	 * Returns the packageManager.
	 * 
	 * @return the packageManager
	 */
	public PackageManager getPackageManager() {
		return this.packageManager;
	}

	// private BindingConstraint[] bindingContraints;

	//
	// public <T> T getBinding(Token name, Class<T> expectedType) {
	// try {
	// return this.getContext().getBindings().get(name.getText(), expectedType,
	// this.bindingContraints);
	// } catch (Exception e) {
	// throw new QueryParserException(e.getMessage(), name);
	// }
	// }
	//
	// public boolean hasBinding(Token name, Class<?> expectedType) {
	// try {
	// Object result = this.getContext().getBindings().get(name.getText(),
	// expectedType, this.bindingContraints);
	// return result != null;
	// } catch (Exception e) {
	// return false;
	// }
	// }
	//
	// public <T> T getRawBinding(Token name, Class<T> expectedType) {
	// try {
	// return this.getContext().getBindings().get(name.getText(), expectedType);
	// } catch (Exception e) {
	// throw new QueryParserException(e.getMessage(), name);
	// }
	// }
	//

	protected EvaluationContext getContext() {
		return this.currentPlan.getEvaluationContext();
	}

	//
	// public void setBinding(Token name, Object binding) {
	// this.getContext().getBindings().set(name.getText(), binding);
	// }
	//
	// public void setBinding(Token name, Object binding, int scopeLevel) {
	// this.getContext().getBindings().set(name.getText(), binding, scopeLevel);
	// }

	public EvaluationExpression createCheckedMethodCall(String packageName, Token name, EvaluationExpression[] params)
			throws RecognitionException {
		return this.createCheckedMethodCall(packageName, name, null, params);
	}

	public EvaluationExpression createCheckedMethodCall(String packageName, Token name, EvaluationExpression object,
			EvaluationExpression[] params)
			throws RecognitionException {
		final IFunctionRegistry functionRegistry = this.getScope(packageName).getFunctionRegistry();
		Callable<?, ?> callable = functionRegistry.get(name.getText());
		if (callable == null)
			throw new RecognitionExceptionWithUsageHint(name, String.format(
				"Unknown function %s; possible alternatives %s", name,
				this.inputSuggestion.suggest(name.getText(), functionRegistry.keySet())));
		if (callable instanceof MacroBase)
			return ((MacroBase) callable).call(params);
		if (!(callable instanceof SopremoFunction))
			throw new QueryParserException(String.format("Unknown callable %s", callable));

		if (object != null) {
			EvaluationExpression[] shiftedParams = new EvaluationExpression[params.length + 1];
			System.arraycopy(params, 0, shiftedParams, 1, params.length);
			params = shiftedParams;
			params[0] = object;
		}

		if (callable instanceof ExpressionFunction)
			return ((ExpressionFunction) callable).inline(params);
		return new FunctionCall(name.getText(), (SopremoFunction) callable, params);
	}

	protected SopremoFunction getSopremoFunction(String packageName, Token name) {
		Callable<?, ?> callable = this.getScope(packageName).getFunctionRegistry().get(name.getText());
		if (!(callable instanceof SopremoFunction))
			throw new QueryParserException(String.format("Unknown function %s", name));
		return (SopremoFunction) callable;
	}

	private Map<String, Class<? extends IJsonNode>> typeNameToType = new HashMap<String, Class<? extends IJsonNode>>();

	public void addTypeAlias(String alias, Class<? extends IJsonNode> type) {
		this.typeNameToType.put(alias, type);
	}

	public EvaluationExpression coerce(String type, EvaluationExpression valueExpression) {
		Class<? extends IJsonNode> targetType = this.typeNameToType.get(type);
		if (targetType == null)
			throw new IllegalArgumentException("unknown type " + type);
		return new CoerceExpression(targetType).withInputExpression(valueExpression);
	}

	@Override
	public Object recoverFromMismatchedSet(IntStream input, RecognitionException e, BitSet follow)
			throws RecognitionException {
		throw e;
	}

	protected ConfObjectInfo<? extends Operator<?>> findOperatorGreedily(String packageName, Token firstWord)
			throws RecognitionException {
		StringBuilder name = new StringBuilder(firstWord.getText());
		IntList wordBoundaries = new IntArrayList();
		wordBoundaries.add(name.length());

		// greedily concatenate as many tokens as possible
		for (int lookAhead = 1; this.input.LA(lookAhead) == firstWord.getType() ||
			CharMatcher.JAVA_LETTER.matchesAllOf(this.input.LT(lookAhead).getText()); lookAhead++) {
			Token matchedToken = this.input.LT(lookAhead);
			name.append(' ').append(matchedToken.getText());
			wordBoundaries.add(name.length());
		}

		int tokenCount = wordBoundaries.size();
		ConfObjectInfo<Operator<?>> info = null;
		ParsingScope scope = this.getScope(packageName);
		for (; info == null && tokenCount > 0;)
			info = scope.getOperatorRegistry().get(name.substring(0, wordBoundaries.getInt(--tokenCount)));

		// consume additional tokens
		for (; tokenCount > 0; tokenCount--)
			this.input.consume();

		if (info == null)
			throw new RecognitionExceptionWithUsageHint(firstWord, String.format(
				"Unknown operator %s; possible alternatives %s", name,
				this.inputSuggestion.suggest(name, scope.getOperatorRegistry().keySet())));
		/*
		 * throw new SimpleException(String.format(
		 * "Unknown operator %s; possible alternatives %s", name,
		 * this.getOperatorSuggestion().suggest(name)), firstWord);
		 */

		return info;
	}

	protected String makeFilePath(Token protocol, String filePath) {

		// no explicit protocol
		if (protocol == null) {
			// try if file is self-contained
			try {
				URI uri = new URI(filePath);
				if (uri.getScheme() == null)
					return new Path(SopremoEnvironment.getInstance().getEvaluationContext().getWorkingPath(), filePath).toString();
				return new Path(uri).toString();
			} catch (URISyntaxException e) {
				throw new IllegalArgumentException("Invalid path " + filePath, e);
			}
		}

		if (protocol.getText().equals("hdfs")) {
			if (filePath.startsWith("hdfs://"))
				// still self-contained
				return new Path(filePath).toString();

			final Path workingPath = SopremoEnvironment.getInstance().getEvaluationContext().getWorkingPath();
			if (workingPath.toUri().getScheme().equals("hdfs"))
				try {
					return new Path(workingPath, filePath).toString();
				} catch (Exception e) {
					throw new IllegalArgumentException("Cannot use current workingPath to form a valid file path for " +
						filePath, e);
				}

			try {
				// if only hdfs is missing, add it
				return new Path(new URI("hdfs://" + filePath)).toString();
			} catch (Exception e) {
				throw new IllegalArgumentException("The path is not a valid URI " + filePath, e);
			}
		}

		if (protocol.getText().equals("file")) {
			if (filePath.startsWith("file://"))
				// still self-contained
				return new Path(filePath).toString();

			if (new File(filePath).isAbsolute())
				return new Path("file://" + filePath).toString();

			final Path workingPath = SopremoEnvironment.getInstance().getEvaluationContext().getWorkingPath();
			// else prepend working directory if it specifies an hdfs path
			if (workingPath.toUri().getScheme().equals("file"))
				throw new IllegalArgumentException(
					"To use shortened local path, a valid local path must be set as the working path");

			return new Path(SopremoEnvironment.getInstance().getEvaluationContext().getWorkingPath(), filePath).toString();
		}

		throw new IllegalArgumentException("Unknown protocol");
	}

	protected ConfObjectInfo<? extends SopremoFormat> findFormat(String packageName, Token name, String pathName)
			throws RecognitionExceptionWithUsageHint {
		if (name == null) {
			URI path;
			try {
				path = new URI(pathName);
			} catch (URISyntaxException e) {
				final RecognitionExceptionWithUsageHint hint =
					new RecognitionExceptionWithUsageHint(name, "invalid path URI");
				hint.initCause(e);
				throw hint;
			}
			final IConfObjectRegistry<SopremoFormat> fileFormatRegistry = this.getFileFormatRegistry();
			for (String fileFormat : fileFormatRegistry.keySet()) {
				final ConfObjectInfo<SopremoFormat> info = fileFormatRegistry.get(fileFormat);
				if (info.newInstance().canHandleFormat(path))
					return info;
			}

			SopremoUtil.LOG.warn("Cannot find file format for " + pathName + " using default " +
				this.getDefaultFileFormat());
			return fileFormatRegistry.get(fileFormatRegistry.getName(this.getDefaultFileFormat()));
		}
		ParsingScope scope = this.getScope(packageName);
		final ConfObjectInfo<SopremoFormat> format = scope.getFileFormatRegistry().get(name.getText());
		if (format == null)
			throw new RecognitionExceptionWithUsageHint(name, String.format(
				"Unknown file format %s; possible alternatives %s", name,
				this.inputSuggestion.suggest(name.getText(), scope.getFileFormatRegistry().keySet())));
		return format;
	}

	/**
	 * @return
	 */
	protected abstract Class<? extends SopremoFormat> getDefaultFileFormat();

	protected ParsingScope getScope(String packageName) {
		if (packageName == null)
			return this;
		ParsingScope scope = this.packageManager.getPackageInfo(packageName);
		if (scope == null)
			throw new QueryParserException("Unknown package " + packageName);
		return scope;
	}

	protected ConfObjectInfo.ConfObjectPropertyInfo findPropertyRelunctantly(ConfObjectInfo<?> info, Token firstWord)
			throws RecognitionException {
		String name = firstWord.getText();
		ConfObjectInfo.ConfObjectPropertyInfo property;

		int lookAhead = 1;
		// Reluctantly concatenate tokens
		final IRegistry<ConfObjectPropertyInfo> propertyRegistry = info.getOperatorPropertyRegistry();
		for (; (property = propertyRegistry.get(name)) == null && this.input.LA(lookAhead) == firstWord.getType(); lookAhead++) {
			Token matchedToken = this.input.LT(lookAhead);
			name = String.format("%s %s", name, matchedToken.getText());
		}

		if (property == null)
			throw new RecognitionExceptionWithUsageHint(firstWord, String.format(
				"Unknown property %s; possible alternatives %s", name,
				this.inputSuggestion.suggest(name, propertyRegistry.keySet())));

		// consume additional tokens
		for (; lookAhead > 1; lookAhead--)
			this.input.consume();

		return property;
	}

	public ConfObjectInfo.ConfObjectIndexedPropertyInfo findInputPropertyRelunctantly(ConfObjectInfo<?> info,
			Token firstWord, boolean consume) {
		String name = firstWord.getText();
		ConfObjectInfo.ConfObjectIndexedPropertyInfo property;

		int lookAhead = 1;
		// Reluctantly concatenate tokens
		final IRegistry<ConfObjectIndexedPropertyInfo> inputPropertyRegistry = info.getInputPropertyRegistry();
		for (; (property = inputPropertyRegistry.get(name)) == null && this.input.LA(lookAhead) == firstWord.getType(); lookAhead++) {
			Token matchedToken = this.input.LT(lookAhead);
			name = String.format("%s %s", name, matchedToken.getText());
		}

		if (property == null)
			return null;
		// throw new FailedPredicateException();
		// throw new
		// QueryParserException(String.format("Unknown property %s; possible alternatives %s",
		// name,
		// this.inputSuggestion.suggest(name, inputPropertyRegistry)),
		// firstWord);

		// consume additional tokens
		if (consume)
			for (; lookAhead > 0; lookAhead--)
				this.input.consume();

		return property;
	}

	@Override
	protected Object recoverFromMismatchedToken(IntStream input, int ttype, BitSet follow) throws RecognitionException {
		// if next token is what we are looking for then "delete" this token
		if (this.mismatchIsUnwantedToken(input, ttype))
			throw new UnwantedTokenException(ttype, input);

		// can't recover with single token deletion, try insertion
		if (this.mismatchIsMissingToken(input, follow)) {
			Object inserted = this.getMissingSymbol(input, null, ttype, follow);
			throw new MissingTokenException(ttype, input, inserted);
		}

		throw new MismatchedTokenException(ttype, input);
	}

	protected void explainUsage(String usage, RecognitionException e) throws RecognitionException {
		final RecognitionExceptionWithUsageHint sre = new RecognitionExceptionWithUsageHint(this.input, usage);
		sre.initCause(e);
		throw sre;
	}

	protected abstract void parseSinks() throws RecognitionException;

	public SopremoPlan parse() throws QueryParserException {
		this.currentPlan = new SopremoPlan();
		try {
			// this.setupParser();
			this.parseSinks();
		} catch (RecognitionExceptionWithUsageHint e) {
			throw new QueryParserException(e.getMessage());
		} catch (RecognitionException e) {
			throw new QueryParserException(DEFAULT_ERROR_MESSAGE, e);
		}
		this.currentPlan.setSinks(this.sinks);

		for (PackageInfo info : this.packageManager.getImportedPackages()) {
			for (File packages : info.getRequiredJarPaths())
				this.currentPlan.addRequiredPackage(packages.getAbsolutePath());
		}
		System.out.println(this.currentPlan.getRequiredPackages());

		return this.currentPlan;
	}

	// /**
	// *
	// */
	// protected void setupParser() {
	// if (this.hasParserFlag(ParserFlag.FUNCTION_OBJECTS))
	// this.bindingContraints = new BindingConstraint[] {
	// BindingConstraint.AUTO_FUNCTION_POINTER,
	// BindingConstraint.NON_NULL };
	// else
	// this.bindingContraints = new BindingConstraint[] {
	// BindingConstraint.NON_NULL };
	// }
	//
	public void addFunction(String name, ExpressionFunction function) {
		this.getFunctionRegistry().put(name, function);
	}

	public void addFunction(String name, String udfPath) {
		int delim = udfPath.lastIndexOf('.');
		if (delim == -1)
			throw new IllegalArgumentException("Invalid path");
		String className = udfPath.substring(0, delim), methodName = udfPath.substring(delim + 1);

		try {
			Class<?> clazz = Class.forName(className);
			this.getFunctionRegistry().put(name, clazz, methodName);
		} catch (ClassNotFoundException e) {
			throw new IllegalArgumentException("Unknown class " + className);
		}
	}

	protected Number parseInt(String text) {
		BigInteger result = new BigInteger(text);
		if (result.bitLength() <= 31)
			return result.intValue();
		if (result.bitLength() <= 63)
			return result.longValue();
		return result;
	}
}
