# The Asset Management Digital Challenge

### Description
This is a Restful Web service with SpringBoot. The service is responsible for creating accounts and transferring amounts from on account to another.
Api endpoints are exposed for creating account, getting the account, and initiating transfer between the accounts.

### Prerequisites
* Java 1.8
* Gradle latest version
* Lombok plugin, if using IDE

## Endpoints exposed

### /v1/accounts
*Implementation Notes:*
Creates accounts for the account details provided in the request body. 
While on-boarding an account via this endpoint, the account balance cannot be less than Zero.

*Request*

Method | Request Param | Request Body|
--- | --- | --- | 
POST |     | {"accountId":"accountName", "balance": 1000}|

*Response*

Http Status Code | Reason | 
--- | --- |
201 | Created |
400 | Invalid initial balance |
400 | Account id {} already exists!|

### /v1/accounts/{accountId}
*Implementation Notes:*
Gets account details for the account id provided as the path variable.

*Request*

Method | Request Param | Request Body|
--- | --- | --- | 
GET |     |     |

*Response*

Http Status Code | Reason | 
--- | --- |
200 | Returns empty response if account not found, else the account details. |

### /v1/accounts/transfer
*Implementation Notes:*
Endpoint accepts request to transfer amounts from one account to another.
It rejects requests when account does not exist, or the there is insufficient balance to transfer.
The implementation of this feature uses semaphore to acquire locks on accounts in transaction. 
It ensures no deadlocks and race conditions are created while multiple transfers.

*Request*

Method | Request Param | Request Body|
--- | --- | --- | 
POST |     | {"accountFrom":"acc1","accountTo": "acc2", "transferAmount": 600}|

*Responses*

Http Status Code | Reason | 
--- | --- |
202 | Transfer Completed |
400 | Insufficient balance in account |
400 | Account does not exist {acc}|

## Implementation logic to solve the deadlock and race conditions possible.
Without using the locks on accounts(semaphore used) there is a possibility of encountering a race condition. 
Example:

| Account id | balance |
--- | --- |
| Acc1 | 1000|
| Acc2 | 1000 |

Case: Two transfers from acc1 to acc2 for an amount: 600 is started simultaneously.

There is a chance that before one of the transfers is committed, other thread reads the account balances 
and commits the values based on the values read.
i.e. both the transaction commit same values to the accounts at the end. The state of account balances in this case will imply
only one transaction was completed.

* **Introduced Semaphore for every account created. If any transfer is happening on an account semaphore is acquired, and release only when that thread completes.**


* **Reason to choose Semaphore** over synchronising the accountTransfer method:
All the transfers need not wait for a previous transfer to complete. 


* **For example** if a transfer is initiated from acc1 to acc2, another transfer for 2 different accounts, say acc3 and acc4 need not wait for the previous transaction.
In case of a third transaction involving acc1 and acc3, it needs to wait for both the transactions to complete.

### Deadlock possibility
Deadlocks can happen when using locks on accounts.
Example:
Transfer from acc1 to acc 2 and acc2 to acc1.
If first thread acquires lock on acc1 and the other on acc2, both the threads will wait for the other thread to release locks acquired to finish the transaction.

**Solution**

Acquiring semaphore in lexicographical order for every transfer.

Using the same example above:
Both the threads, involve getting locks on accounts: acc1 and acc2. 
Now, the locks are to be acquired in lexicographical order, so the transfer which acquires the lock first on acc1 gets completed first
as the second thread has to wait till it can acquire lock on the acc1.
