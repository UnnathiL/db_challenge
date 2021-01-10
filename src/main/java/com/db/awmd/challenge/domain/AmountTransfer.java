package com.db.awmd.challenge.domain;

import java.math.BigDecimal;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class AmountTransfer {

	@NotNull
	private String accountFrom;
	
	@NotNull
	private String accountTo;
	
	@NotNull
	@Min(value = 0, message = "Initial balance must be positive.")
	private BigDecimal transferAmount;
	
	@JsonCreator
	public AmountTransfer(@JsonProperty("accountFrom") String accountFrom,
			@JsonProperty("accountTo") String accountTo,
	    @JsonProperty("transferAmount") BigDecimal transferAmount) {
		this.accountFrom = accountFrom;
		this.accountTo = accountTo;
	    this.transferAmount = transferAmount;
	}
}
