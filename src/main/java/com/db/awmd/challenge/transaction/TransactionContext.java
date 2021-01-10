package com.db.awmd.challenge.transaction;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;

public class TransactionContext<K, V> {
	@Getter
	private Map<K, V> savePoints = new HashMap<>();	
}
