package com.db.awmd.challenge.service;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.exception.AmountTransferException;
import com.db.awmd.challenge.repository.AccountsRepository;
import com.db.awmd.challenge.transaction.AccountTransactionManager;

import lombok.Getter;

@Service
@Slf4j
public class AccountsService {

  @Getter
  private final AccountsRepository accountsRepository;

  @Autowired
	private NotificationService notificationService;
  
  private AccountTransactionManager transactionManager;
  
  @Autowired
  public AccountsService(AccountsRepository accountsRepository) {
    this.accountsRepository = accountsRepository;
    this.transactionManager = new AccountTransactionManager(accountsRepository);
  }

  public void createAccount(Account account) {
    this.accountsRepository.createAccount(account);
  }

  public Account getAccount(String accountId) {
    return this.accountsRepository.getAccount(accountId);
  }
  
 // @Transactional(propagation=Propagation.REQUIRED, readOnly=false, rollbackFor=AmountTransferException.class)
  public void amountTransfer(final String fromAccount,	
		  final String toAccount, final BigDecimal transferAmount) throws AmountTransferException {

  	if(fromAccount .compareTo(toAccount) == 0 || transferAmount.compareTo(BigDecimal.ZERO) == 0) {
			throw new AmountTransferException("Invalid amount transfer details. From Acc: " + fromAccount+ " to acc: "
							+ toAccount + " amount: " + transferAmount);
		}

		transactionManager.doInTransaction(() -> {
			/*
			Locks on the accounts(to and from) are obtained inside the code path of debit and credit methods.
			To ensure deadlocks do not occur, calling the respective debit and credit methods
			for the given accounts in lexicographical order.
			If from account is lexicographically smaller than to account, debit method followed by credit method
			will be called and vice versa.
			*/

			if (fromAccount.compareTo(toAccount) < 0) {
				this.debit(fromAccount, transferAmount);
			} else {
				this.credit(toAccount, transferAmount);
			}

			if (fromAccount.compareTo(toAccount) < 0) {
				this.credit(toAccount, transferAmount);
			} else {
				this.debit(fromAccount, transferAmount);
			}
		});

		transactionManager.commit();
		// initiating notifications for transfer
		notifyTransferDetails(fromAccount, toAccount, transferAmount);
	}
  
	private Account debit(String accountId, BigDecimal amount) throws AmountTransferException{
  		// take repository from transaction manager in order to manage transactions and rollBack.
  		//But, This method will only be transactional only if this is called within "transactionManager.doInTransaction()
  		// OR method annotated with @AccountTransaction.
		final Account account = transactionManager.getRepoProxy().getAccount(accountId);
		if(account == null) {
			throw new AmountTransferException("Account does not exist");
		}
		if(account.getBalance().compareTo(amount) == -1) {
			throw new AmountTransferException("Insufficient balance in account");
		}
		BigDecimal bal = account.getBalance().subtract(amount);
		account.setBalance(bal);
		return account;
	}
	
	private Account credit(String accountId, BigDecimal amount) throws AmountTransferException{
		// take repository from transaction manager in order to manage transactions and rollBack.
  		//But, This method will only be transactional only if this is called within "transactionManager.doInTransaction()
  		// OR method annotated with @AccountTransaction.
		final Account account = transactionManager.getRepoProxy().getAccount(accountId);
		if(account == null) {
			throw new AmountTransferException("Account does not exist");
		}
		BigDecimal bal = account.getBalance().add(amount);
		account.setBalance(bal);
		return account;
	}

	private boolean notifyTransferDetails(String fromAccount,String toAccount, BigDecimal transferAmount) {
		// Sending amount transfer details to both accounts
		try {
			notificationService.notifyAboutTransfer(accountsRepository.getAccount(fromAccount),
			     "Amount : " + transferAmount + " debited from your account : " + fromAccount
			        + " to account : " + toAccount + " . ");
			notificationService.notifyAboutTransfer(accountsRepository.getAccount(toAccount),
			      "Amount : " + transferAmount + " credited to your account : " + toAccount
			        + " from account : " + fromAccount + " . ");
		} catch (Exception e) {
			log.error("Error occurred while sending notifications of transfer details", e);
			return false;
		}
		return true;
	}
}
