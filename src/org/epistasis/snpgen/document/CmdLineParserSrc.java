package org.epistasis.snpgen.document;

/**
 * Copyright (c) 2001-2012 Steve Purcell.
 * Copyright (c) 2002      Vidar Holen.
 * Copyright (c) 2002      Michal Ceresna.
 * Copyright (c) 2005      Ewan Mellor.
 * Copyright (c) 2010-2012 penSec.IT UG (haftungsbeschränkt).
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer. Redistributions in binary
 * form must reproduce the above copyright notice, this list of conditions and
 * the following disclaimer in the documentation and/or other materials provided
 * with the distribution. Neither the name of the copyright holder nor the names
 * of its contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

/**
 * Largely GNU-compatible command-line options parser. Has short (-v) and
 * long-form (--verbose) option support, and also allows options with associated
 * values (-d 2, --debug 2, --debug=2). Option processing can be explicitly
 * terminated by the argument '--'.
 *
 * @author Steve Purcell
 * @author penSec.IT UG (haftungsbeschränkt)
 *
 * @version 2.0
 * @see com.sanityinc.jargs.examples.OptionTest
 */
public class CmdLineParserSrc {

	private String[] remainingArgs = null;

	private final Map<String, Option<?>> options = new LinkedHashMap<>(10);

	private Map<String, List<?>> values = new HashMap<String, List<?>>(10);

	public CmdLineParserSrc() {
	}

	/**
	 * make a copy of the passed in parser with all the same options but no
	 * values
	 *
	 * @param templateParser
	 */
	private CmdLineParserSrc(final CmdLineParserSrc templateParser) {
		options.putAll(templateParser.options);
	}

	/**
	 * Convenience method for adding a boolean option.
	 *
	 * @param helpText
	 * @return the new Option
	 */
	public final Option<Boolean> addBooleanOption(final char shortForm, final String longForm, final String helpText) {
		return addOption(new Option.BooleanOption(shortForm, longForm, helpText));
	}
	/**
	 * Convenience method for adding a boolean option.
	 * @return the new Option
	 */
	public final Option<Boolean> addBooleanOption(final String longForm, final String helpText) {
		return addOption(new Option.BooleanOption(longForm, helpText));
	}

	/**
	 * Convenience method for adding a double option.
	 *
	 * @param helpText
	 * @return the new Option
	 */
	public final Option<Double> addDoubleOption(final char shortForm, final String longForm, final String helpText) {
		return addOption(new Option.DoubleOption(shortForm, longForm, helpText));
	}

	/**
	 * Convenience method for adding a double option.
	 * @return the new Option
	 */
	public final Option<Double> addDoubleOption(final String longForm, final String helpText) {
		return addOption(new Option.DoubleOption(longForm, helpText));
	}

	/**
	 * Convenience method for adding an integer option.
	 *
	 * @param string
	 * @return the new Option
	 */
	public final Option<Integer> addIntegerOption(final char shortForm, final String longForm, final String helpText) {
		return addOption(new Option.IntegerOption(shortForm, longForm, helpText));
	}

	/**
	 * Convenience method for adding an integer option.
	 * @return the new Option
	 */
	public final Option<Integer> addIntegerOption(final String longForm, final String helpText) {
		return addOption(new Option.IntegerOption(longForm, helpText));
	}

	/**
	 * Convenience method for adding a long integer option.
	 * @return the new Option
	 */
	public final Option<Long> addLongOption(final char shortForm, final String longForm, final String helpText) {
		return addOption(new Option.LongOption(shortForm, longForm, helpText));
	}

	/**
	 * Convenience method for adding a long integer option.
	 * @return the new Option
	 */
	public final Option<Long> addLongOption(final String longForm, final String helpText) {
		return addOption(new Option.LongOption(longForm, helpText));
	}

	/**
	 * Add the specified Option to the list of accepted options
	 */
	public final <T> Option<T> addOption( final Option<T> opt ) {
		if ( opt.shortForm() != null ) {
			final Option<?> preExistingOption = options.put("-" + opt.shortForm(), opt);
			if (preExistingOption != null) {
				throw new IllegalArgumentException("Option short form duplicates an existing option. First option: " + preExistingOption
						+ " conflicting option: " + opt);
			}
		}
		final Option<?> preExistingOption = options.put("--" + opt.longForm(), opt);
		if (preExistingOption != null) {
			throw new IllegalArgumentException("Option long form duplicates an existing option. First option: " + preExistingOption
					+ " conflicting option: " + opt);
		}
		return opt;
	}

	/**
	 * Convenience method for adding a string option.
	 *
	 * @param helpText
	 * @return the new Option
	 */
	public final Option<String> addStringOption(final char shortForm, final String longForm, final String helpText) {
		return addOption(new Option.StringOption(shortForm, longForm, helpText));
	}

	/**
	 * Convenience method for adding a string option.
	 * @return the new Option
	 */
	public final Option<String> addStringOption(final String longForm, final String helpText) {
		return addOption(new Option.StringOption(longForm, helpText));
	}

	/**
	 * @return the parsed value of the given Option, or the given default 'def'
	 *         if the option was not set NOTE: REMOVES THE VALUE
	 */
	public final <T> T getOptionValue(final Option<T> o) {
		final List<?> v = values.get(o.longForm());

		if ((v == null) || v.isEmpty()) {
			return null;
		} else {

			/*
			 * Cast should be safe because Option.parseValue has to return an
			 * instance of type T or null
			 */
			@SuppressWarnings("unchecked")
			final T result = (T) v.remove(0);
			return result;
		}
	}

	/**
	 * @return A Collection giving the parsed values of all the occurrences of
	 * the given Option, or an empty Collection if the option was not set.
	 */
	public final <T> Vector<T> getOptionValues(final Option<T> option) {
		final Vector<T> result = new Vector<T>();

		while (true) {
			final T o = getOptionValue(option);

			if (o == null) {
				return result;
			} else {
				result.add(o);
			}
		}
	}

	/**
	 * @return the non-option arguments
	 */
	public final String[] getRemainingArgs() {
		return remainingArgs;
	}

	public String getUsage(final String optionSpacer) {

		final StringBuilder sb = new StringBuilder();
		final Set<Option<?>> uniqueOptions = new LinkedHashSet<>(options.values());
		for (final Option<?> opt : uniqueOptions) {
			if (sb.length() > 0) {
				sb.append(optionSpacer);
			}
			sb.append(opt.toString());
			final List<?> optionValues = values.get(opt.longForm);
			if ((optionValues != null) && (optionValues.size() > 0)) {
				sb.append("\n" + opt.longForm + " passed in values: " + optionValues.toString());
			}
		}
		return sb.toString();
	}

	public final boolean isOptionSet(final Option<?> o) {
		return values.containsKey(o.longForm());
	}


	/**
	 * Extract the options and non-option arguments from the given
	 * list of command-line arguments. The default locale is used for
	 * parsing options whose values might be locale-specific.
	 */
	public final void parse( final String[] argv ) throws OptionException {
		parse(argv, Locale.getDefault());
	}


	/**
	 * Extract the options and non-option arguments from the given
	 * list of command-line arguments. The specified locale is used for
	 * parsing options whose values might be locale-specific.
	 */
	public final void parse( final String[] argv, final Locale locale )
			throws OptionException {

		final ArrayList<Object> otherArgs = new ArrayList<Object>();
		int position = 0;
		values = new HashMap<String, List<?>>(10);
		while ( position < argv.length ) {
			String curArg = argv[position];
			if ( curArg.startsWith("-") ) {
				if ( curArg.equals("--") ) { // end of options
					position += 1;
					break;
				}
				String valueArg = null;
				if ( curArg.startsWith("--") ) { // handle --arg=value
					final int equalsPos = curArg.indexOf("=");
					if ( equalsPos != -1 ) {
						valueArg = curArg.substring(equalsPos+1);
						curArg = curArg.substring(0,equalsPos);
					}
				} else if(curArg.length() > 2) {  // handle -abcd
					for(int i=1; i<curArg.length(); i++) {
						final Option<?> opt=options.get("-"+curArg.charAt(i));
						if(opt==null) {
							throw new UnknownSuboptionException(curArg,curArg.charAt(i));
						}
						if(opt.wantsValue()) {
							throw new NotFlagException(curArg,curArg.charAt(i));
						}
						addValue(opt, null, locale);

					}
					position++;
					continue;
				}

				final Option<?> opt = options.get(curArg);
				if ( opt == null ) {
					throw new UnknownOptionException(curArg);
				}

				if ( opt.wantsValue() ) {
					if ( valueArg == null ) {
						position += 1;
						if ( position < argv.length ) {
							valueArg = argv[position];
						}
					}
					addValue(opt, valueArg, locale);
				} else {
					addValue(opt, null, locale);
				}

				position += 1;
			}
			else {
				if (curArg.trim().length() > 0) {
					otherArgs.add(curArg);
				}
				position += 1;
			}
		}
		for ( ; position < argv.length; ++position ) {
			otherArgs.add(argv[position]);
		}

		remainingArgs = new String[otherArgs.size()];
		remainingArgs = otherArgs.toArray(remainingArgs);
	}

	@Override
	public String toString() {
		//		final StringBuilder sb = new StringBuilder();
		//		final Set<Option<?>> uniqueOptions = new LinkedHashSet<>(options.values());
		//		for (final Option<?> opt : uniqueOptions) {
		//			if (sb.length() > 0) {
		//				sb.append(' ');
		//			}
		//			sb.append(opt.toString());
		//			final Object optionValue = getOptionValue(opt);
		//			if (optionValue != null) {
		//				sb.append("=" + optionValue);
		//			}
		//		}
		//		return sb.toString();
		return getUsage("\n");
	}


	private <T> void addValue(final Option<T> opt, final String valueArg, final Locale locale)
			throws OptionException {

		final T value = opt.getValue(valueArg, locale);
		final String lf = opt.longForm();

		/* Cast is typesafe because the only location we add elements to the
		 * values map is in this method.
		 */
		@SuppressWarnings("unchecked")
		List<T> v = (List<T>) values.get(lf);

		if (v == null) {
			v = new ArrayList<T>();
			values.put(lf, v);
		}

		v.add(value);
	}

	/**
	 * Thrown when an illegal or missing value is given by the user for
	 * an option that takes a value. <code>getMessage()</code> returns
	 * an error string suitable for reporting the error to the user (in
	 * English).
	 *
	 * No generic class can ever extend <code>java.lang.Throwable</code>, so we
	 * have to return <code>Option&lt;?&gt;</code> instead of
	 * <code>Option&lt;T&gt;</code>.
	 */
	public static class IllegalOptionValueException extends OptionException {
		private static final long serialVersionUID = 1L;

		private final Option<?> option;

		private final String value;

		public <T> IllegalOptionValueException( final Option<T> opt, final String value ) {
			super("Illegal value '" + value + "' for option " +
					(opt.shortForm() != null ? "-" + opt.shortForm() + "/" : "") +
					"--" + opt.longForm());
			option = opt;
			this.value = value;
		}
		/**
		 * @return the name of the option whose value was illegal (e.g. "-u")
		 */
		public Option<?> getOption() {
			return option;
		}
		/**
		 * @return the illegal value
		 */
		public String getValue() {
			return value;
		}
	}

	/**
	 * Thrown when the parsed commandline contains multiple concatenated
	 * short options, such as -abcd, where one or more requires a value.
	 * <code>getMessage()</code> returns an english human-readable error
	 * string.
	 * @author Vidar Holen
	 */
	public static class NotFlagException extends UnknownOptionException {
		private static final long serialVersionUID = 1L;
		private final char notflag;

		NotFlagException( final String option, final char unflaggish ) {
			super(option, "Illegal option: '"+option+"', '"+
					unflaggish+"' requires a value");
			notflag=unflaggish;
		}

		/**
		 * @return the first character which wasn't a boolean (e.g 'c')
		 */
		public char getOptionChar() {
			return notflag;
		}
	}


	/**
	 * Representation of a command-line option
	 *
	 * @param T Type of data configured by this option
	 */
	public static abstract class Option<T> {

		protected final String shortForm;

		protected final String longForm;

		private final boolean wantsValue;

		private final String helpText;

		protected Option( final char shortForm, final String longForm,
				final String helpText, final boolean wantsValue) {
			this(new String(new char[] { shortForm }), longForm, helpText, wantsValue);
		}

		protected Option( final String longForm, final String helpText,final boolean wantsValue ) {
			this(/* shortForm */ null, longForm, helpText, wantsValue);
		}

		private Option(final String shortForm, final String longForm, final String helpText, final boolean wantsValue) {
			if ( longForm == null ) {
				throw new IllegalArgumentException("Null longForm not allowed");
			}
			this.shortForm = shortForm;
			this.longForm = longForm;
			this.wantsValue = wantsValue;
			this.helpText = helpText;
		}

		public final T getValue( final String arg, final Locale locale )
				throws OptionException {
			if ( this.wantsValue ) {
				if ( arg == null ) {
					throw new IllegalOptionValueException(this, "");
				}
				return this.parseValue(arg, locale);
			} else {
				return this.getDefaultValue();
			}
		}

		public String helpText() {
			return getHelpText();
		}

		public String longForm() {
			return this.longForm;
		}

		public String shortForm() {
			return this.shortForm;
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder("{");
			if (this.shortForm != null) {
				sb.append("-" + this.shortForm + ",");
			}

			sb.append("--" + longForm + "}");

			final String expectedTypeString = getExpectedTypeString();
			if ((expectedTypeString != null) && !expectedTypeString.isEmpty()) {
				sb.append(" " + expectedTypeString);
			}
			final String localHelpText = getHelpText();
			if ((localHelpText != null) && !localHelpText.isEmpty()) {
				sb.append(" " + localHelpText);
			}
			return sb.toString();
		}

		/**
		 * Tells whether or not this option wants a value
		 */
		public boolean wantsValue() {
			return this.wantsValue;
		}

		/**
		 * Override to define default value returned by getValue if option does
		 * not want a value
		 */
		protected T getDefaultValue() {
			return null;
		}

		protected abstract String getExpectedTypeString();

		protected String getHelpText() {
			return helpText;
		}
		/**
		 * Override to extract and convert an option value passed on the
		 * command-line
		 *
		 * @throws OptionException
		 */
		protected T parseValue(final String arg, final Locale locale)
				throws IllegalOptionValueException, OptionException {

			return null;
		}


		/**
		 * An option that expects a boolean value
		 */
		public static class BooleanOption extends Option<Boolean> {
			public BooleanOption(final char shortForm, final String longForm, final String helpText) {
				this(String.valueOf(shortForm), longForm, helpText);
			}
			public BooleanOption( final String longForm, final String helpText) {
				this(/* shortForm */(String) null, longForm, helpText);
			}

			public BooleanOption(final String shortForm, final String longForm, final String helpText) {
				super(shortForm, longForm, helpText, /* wantsValue */false);
			}
			@Override
			public Boolean getDefaultValue() {
				return Boolean.TRUE;
			}

			@Override
			public Boolean parseValue(final String arg, final Locale locale) {
				return Boolean.TRUE;
			}

			@Override
			protected String getExpectedTypeString() {
				// return Boolean.class.getSimpleName().toLowerCase();
				return "<true if present, false otherwise>";
			}
		}

		public static class CmdLineParserOption extends Option<CmdLineParserSrc> {
			private final CmdLineParserSrc templateCmdLineParser;

			public CmdLineParserOption(final char shortForm, final String longForm, final String helpText,
					final CmdLineParserSrc templateCmdLineParser) {
				super(shortForm, longForm, helpText, true);
				this.templateCmdLineParser = templateCmdLineParser;
			}

			@Override
			public CmdLineParserSrc getDefaultValue() {
				return null;
			}

			@Override
			public CmdLineParserSrc parseValue(final String arg, final Locale locale) throws OptionException {
				// because there may be multiple instances of the parser
				// created, we must create a new one for each arg we parse so
				// that the values do not get appended together
				final CmdLineParserSrc cmdLineParser = new CmdLineParserSrc(templateCmdLineParser);
				final String[] args = arg.split(" ");
				cmdLineParser.parse(args);
				return cmdLineParser;
			}

			@Override
			public String toString() {
				final StringBuilder sb = new StringBuilder("{");
				if (shortForm != null) {
					sb.append("-" + shortForm + ",");
				}

				sb.append("--" + longForm + "}");

				final String localHelpText = getHelpText();
				if ((localHelpText != null) && !localHelpText.isEmpty()) {
					sb.append(" " + localHelpText);
				}

				sb.append("\n\t" + templateCmdLineParser.getUsage("\n\t"));
				sb.append("\n");
				return sb.toString();
			}

			@Override
			protected String getExpectedTypeString() {
				throw new RuntimeException("Not expected to be called");
			}

		}

		/**
		 * An option that expects a doubleing-point value
		 */
		public static class DoubleOption extends Option<Double> {
			public DoubleOption(final char shortForm, final String longForm, final String helpText) {
				super(shortForm, longForm, helpText, true);
			}

			public DoubleOption( final String longForm, final String helpText) {
				super(/* shortForm */null, longForm, helpText, true);
			}
			@Override
			protected String getExpectedTypeString() {
				return Double.class.getSimpleName().toLowerCase();
			}

			@Override
			protected Double parseValue( final String arg, final Locale locale )
					throws IllegalOptionValueException {
				try {
					final NumberFormat format = NumberFormat.getNumberInstance(locale);
					final Number num = format.parse(arg);
					return new Double(num.doubleValue());
				} catch (final ParseException e) {
					throw new IllegalOptionValueException(this, arg);
				}
			}
		}

		public static class EnumParserOption<E extends Enum<E>> extends Option<E> {
			private final Class<E> emu;

			public EnumParserOption(final char shortForm, final String longForm, final String helpText, final Class<E> emu) {
				super(shortForm, longForm, helpText, true);
				this.emu = emu;
			}

			@Override
			public E getDefaultValue() {
				return null;
			}

			@Override
			public E parseValue(final String arg, final Locale locale) throws OptionException {
				// because there may be multiple instances of the parser
				// created, we must create a new one for each arg we parse so
				// that the values do not get appended together
				final E localEmu = Enum.valueOf(emu, arg);
				return localEmu;
			}

			@Override
			protected String getExpectedTypeString() {
				return Arrays.toString(emu.getEnumConstants());
			}

		}

		/**
		 * An option that expects an integer value
		 */
		public static class IntegerOption extends Option<Integer> {
			public IntegerOption(final char shortForm, final String longForm, final String helpText) {
				super(shortForm, longForm, helpText, true);
			}
			public IntegerOption( final String longForm, final String helpText) {
				super(/* shortForm */null, longForm, helpText, true);
			}

			@Override
			protected String getExpectedTypeString() {
				return Integer.class.getSimpleName().toLowerCase();
			}

			@Override
			protected Integer parseValue( final String arg, final Locale locale )
					throws IllegalOptionValueException {
				try {
					return new Integer(arg);
				} catch (final NumberFormatException e) {
					throw new IllegalOptionValueException(this, arg);
				}
			}
		}

		/**
		 * An option that expects a long integer value
		 */
		public static class LongOption extends Option<Long> {
			public LongOption(final char shortForm, final String longForm, final String helpText) {
				super(shortForm, longForm, helpText, true);
			}

			public LongOption(final String longForm, final String helpText) {
				super(/* shortForm */null, longForm, helpText, true);
			}

			@Override
			protected String getExpectedTypeString() {
				return Long.class.getSimpleName().toLowerCase();
			}

			@Override
			protected Long parseValue( final String arg, final Locale locale )
					throws IllegalOptionValueException {
				try {
					return new Long(arg);
				} catch (final NumberFormatException e) {
					throw new IllegalOptionValueException(this, arg);
				}
			}
		}

		/**
		 * An option that expects a string value
		 */
		public static class StringOption extends Option<String> {
			public StringOption(final char shortForm, final String longForm, final String helpText) {
				super(shortForm, longForm, helpText, true);
			}

			public StringOption( final String longForm, final String helpText) {
				super(/* shortForm */null, longForm, helpText, true);
			}
			@Override
			protected String getExpectedTypeString() {
				return String.class.getSimpleName().toLowerCase();
			}

			@Override
			protected String parseValue( final String arg, final Locale locale ) {
				return arg;
			}
		}
	}


	/**
	 * Base class for exceptions that may be thrown when options are parsed
	 */
	public static abstract class OptionException extends Exception {
		private static final long serialVersionUID = 1L;

		OptionException(final String msg) {
			super(msg);
		}
	}

	/**
	 * Thrown when the parsed command-line contains an option that is not
	 * recognised. <code>getMessage()</code> returns an error string suitable
	 * for reporting the error to the user (in English).
	 */
	public static class UnknownOptionException extends OptionException {
		private static final long serialVersionUID = 1L;
		private final String optionName;

		UnknownOptionException(final String optionName) {
			this(optionName, "Unknown option '" + optionName + "'");
		}

		UnknownOptionException(final String optionName, final String msg) {
			super(msg);
			this.optionName = optionName;
		}

		/**
		 * @return the name of the option that was unknown (e.g. "-u")
		 */
		public String getOptionName() {
			return optionName;
		}
	}

	/**
	 * Thrown when the parsed commandline contains multiple concatenated
	 * short options, such as -abcd, where one is unknown.
	 * <code>getMessage()</code> returns an english human-readable error
	 * string.
	 * @author Vidar Holen
	 */
	public static class UnknownSuboptionException
	extends UnknownOptionException {
		private static final long serialVersionUID = 1L;
		private final char suboption;

		UnknownSuboptionException( final String option, final char suboption ) {
			super(option, "Illegal option: '"+suboption+"' in '"+option+"'");
			this.suboption=suboption;
		}
		public char getSuboption() {
			return suboption;
		}
	}
}
