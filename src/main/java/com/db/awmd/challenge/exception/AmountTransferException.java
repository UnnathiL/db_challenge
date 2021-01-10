package com.db.awmd.challenge.exception;

public class AmountTransferException  extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public AmountTransferException(String message) {
	   super(message);
	}
}
