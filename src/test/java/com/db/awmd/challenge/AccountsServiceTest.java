package com.db.awmd.challenge;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.exception.AmountTransferException;
import com.db.awmd.challenge.exception.DuplicateAccountIdException;
import com.db.awmd.challenge.repository.AccountsRepository;
import com.db.awmd.challenge.service.AccountsService;
import com.db.awmd.challenge.service.NotificationService;
import com.db.awmd.challenge.transaction.AccountTransactionManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigDecimal;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class AccountsServiceTest {

  @Autowired
  private AccountsService accountsService;

  @MockBean
	private NotificationService notificationService;

  @Autowired
	AccountsRepository accountsRepository;

	@Before
	public void init() {
		doNothing().when(notificationService).notifyAboutTransfer(any(), any());
		accountsRepository.clearAccounts();
	}

	@After
	public void cleanup() {
		accountsRepository.clearAccounts();
	}

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
	    verify(notificationService, times(2)).notifyAboutTransfer(any(), anyString());
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
	    verify(notificationService, times(2)).notifyAboutTransfer(any(), any());;

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
	    verify(notificationService, times(0)).notifyAboutTransfer(any(), anyString());

  }

	@Test
	public void testTransferOnSameAccount() {
		Account accountFrom = new Account("Id-341");
		accountFrom.setBalance(new BigDecimal(1000));
		this.accountsService.createAccount(accountFrom);

		try {
			this.accountsService.amountTransfer("Id-341", "Id-341", new BigDecimal(1000));
			fail("Should have failed as both accounts in transfer are same");
		} catch (AmountTransferException e) {
			assertThat(e.getMessage()).isEqualTo("Invalid amount transfer details. From Acc: Id-341 to acc: Id-341 amount: 1000");
		}
	}

	@Test
	public void testTransferZeroAmount() {
		Account accountFrom = new Account("Id-341");
		accountFrom.setBalance(new BigDecimal(1000));
		this.accountsService.createAccount(accountFrom);
		Account accountTo = new Account("Id-342");
		accountTo.setBalance(new BigDecimal(1000));
		this.accountsService.createAccount(accountTo);
		try {
			this.accountsService.amountTransfer("Id-341", "Id-342", BigDecimal.ZERO);
			fail("Should have failed as the transfer amount is zero");
		} catch (AmountTransferException e) {
			assertThat(e.getMessage()).isEqualTo("Invalid amount transfer details. From Acc: Id-341 to acc: Id-342 amount: 0");
		}	}

	@Test
	public void amountTransfers_raceCondition() throws Exception {
		/*
		Starting 10000 threads to execute similar transfer. At the end, the amount from Id-341 should be zero,
		as all the amount is transferred to Id-342
		*/

		Account accountFrom = new Account("Id-341");
		accountFrom.setBalance(new BigDecimal(10000));
		this.accountsService.createAccount(accountFrom);

		Account accountTo = new Account("Id-342");
		accountTo.setBalance(new BigDecimal(0));
		this.accountsService.createAccount(accountTo);

		ExecutorService executorService =
						new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
										new LinkedBlockingQueue<Runnable>());

		for (int i = 0; i < 10000; i++) {
			executorService.execute(new Runnable() {
				@Override
				public void run() {
					accountsService.amountTransfer("Id-341", "Id-342", new BigDecimal(1));
				}
			});
		}

		//waiting for threads to finish execution
		TimeUnit.SECONDS.sleep(10);
		assertThat(this.accountsService.getAccount("Id-341").getBalance()).isEqualTo(BigDecimal.ZERO);
		assertThat(this.accountsService.getAccount("Id-342").getBalance()).isEqualTo(new BigDecimal(10000));
		verify(notificationService, times(20000)).notifyAboutTransfer(any(), anyString());

	}
}
