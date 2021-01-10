package com.db.awmd.challenge.transaction;

@FunctionalInterface
public interface TransactionCallback {
	
	public void process();
}
