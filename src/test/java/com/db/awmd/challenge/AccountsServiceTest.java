package com.db.awmd.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.exception.DuplicateAccountIdException;
import com.db.awmd.challenge.service.AccountsService;
import java.math.BigDecimal;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class AccountsServiceTest {

  @Autowired
  private AccountsService accountsService;

  @Test
  public void addAccount() throws Exception {
    Account account = new Account("Id-123");
    account.setBalance(new BigDecimal(1000));
    this.accountsService.createAccount(account);

    assertThat(this.accountsService.getAccount("Id-123")).isEqualTo(account);
  }

  @Test
  public void addAccount_failsOnDuplicateId() throws Exception {
    String uniqueId = "Id-" + System.currentTimeMillis();
    Account account = new Account(uniqueId);
    this.accountsService.createAccount(account);

    try {
      this.accountsService.createAccount(account);
      fail("Should have failed when adding duplicate account");
    } catch (DuplicateAccountIdException ex) {
      assertThat(ex.getMessage()).isEqualTo("Account id " + uniqueId + " already exists!");
    }

  }
  
  @Test
  public void amountTransfer_TransactionCommit() throws Exception {
	  	Account accountFrom = new Account("Id-341");
	  	accountFrom.setBalance(new BigDecimal(1000));
	    this.accountsService.createAccount(accountFrom);
	    Account accountTo = new Account("Id-342");
	    accountTo.setBalance(new BigDecimal(1000));
	    this.accountsService.createAccount(accountTo);
	    this.accountsService.amountTransfer("Id-341", "Id-342", new BigDecimal(1000));
	    assertThat(this.accountsService.getAccount("Id-341").getBalance()).isEqualTo(BigDecimal.ZERO);
	    assertThat(this.accountsService.getAccount("Id-342").getBalance()).isEqualTo(new BigDecimal(2000));

  }
  
  @Test
  public void amountTransfer_TransactionRollBack() throws Exception {
		Account accountFrom = new Account("Id-350");
	  	accountFrom.setBalance(new BigDecimal(1000));
	    this.accountsService.createAccount(accountFrom);
	    Account accountTo = new Account("Id-351");
	    accountTo.setBalance(new BigDecimal(1000));
	    this.accountsService.createAccount(accountTo);
	    this.accountsService.amountTransfer("Id-350", "Id-351", new BigDecimal(1000));
	  	
	    try {
	    	//make transfer when balance insufficient 
	    	this.accountsService.amountTransfer("Id-350", "Id-351", new BigDecimal(500));
	    }catch(Exception e) {
	  		assertThat(e.getMessage()).isEqualTo("Insufficient balance in account");
	  	}
	  	//Transaction will be rollBack and no account will be updated
	    assertThat(this.accountsService.getAccount("Id-350").getBalance()).isEqualTo(BigDecimal.ZERO);
	    assertThat(this.accountsService.getAccount("Id-351").getBalance()).isEqualTo(new BigDecimal(2000));

  }
  
  @Test
  public void amountTransfer_TransactionRollBackOnNonExistingAccount() throws Exception {
	  	//make transfer To an Account which do not exist
	  	Account accountFrom = new Account("Id-360");
	  	accountFrom.setBalance(new BigDecimal(1000));
	    this.accountsService.createAccount(accountFrom);
	    try {
	    	this.accountsService.amountTransfer("Id-360", "Id-361", new BigDecimal(500));
	    }catch(Exception e) {
	    	assertThat(e.getMessage()).isEqualTo("Account does not exist");
	    }
	  	//Transaction will be rollBack and no debit will happen
	    assertThat(this.accountsService.getAccount("Id-360").getBalance()).isEqualTo(new BigDecimal(1000));

  }
}
