package org.epistasis.snpgen.exception;

public class InputException extends Exception {

	private static final long serialVersionUID = 1L;

	public InputException() {
		super();
	}

	public InputException(final String message) {
		super(message);
	}

	public InputException(final String message, final Throwable cause) {
		super(message, cause);
	}

	public InputException(final Throwable cause) {
		super(cause);
	}
}
