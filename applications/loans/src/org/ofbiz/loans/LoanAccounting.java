package org.ofbiz.loans;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import javolution.util.FastMap;

import org.apache.log4j.Logger;
import org.ofbiz.accountholdertransactions.AccHolderTransactionServices;
import org.ofbiz.accountholdertransactions.LoanUtilities;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.DelegatorFactoryImpl;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.condition.EntityConditionList;
import org.ofbiz.entity.condition.EntityExpr;
import org.ofbiz.entity.condition.EntityOperator;
import org.ofbiz.entity.transaction.GenericTransactionException;
import org.ofbiz.entity.transaction.TransactionUtil;

/**
 * @author Japheth Odonya @when Aug 22, 2014 2:21:39 PM
 * 
 *         Loan Accounting - Posting Loan Related Stuff Disbursement and
 *         Repayment will be posted here
 * 
 * **/
public class LoanAccounting {
	
	public static Logger log = Logger.getLogger(LoanAccounting.class);

	public static String postDisbursement(GenericValue loanApplication,
			Map<String, String> userLogin) {
		Map<String, Object> result = FastMap.newInstance();
		String loanApplicationId = loanApplication
				.getString("loanApplicationId");// (String)context.get("loanApplicationId");
		log.info("What we got is ############ " + loanApplicationId);
		Delegator delegator;
		delegator = loanApplication.getDelegator();
		// GenericValue accountTransaction = null;
		loanApplicationId = loanApplicationId.replaceFirst(",", "");
		try {
			loanApplication = delegator.findOne("LoanApplication",
					UtilMisc.toMap("loanApplicationId", Long.valueOf(loanApplicationId)),
					false);
		} catch (GenericEntityException e2) {
			e2.printStackTrace();
		}

		// Creates a record in AcctgTrans
		String acctgTransType = "LOAN_RECEIVABLE";
		String glAcctTypeIdMemberDepo = "CURRENT_LIABILITY";
		String glAcctTypeIdLoans = "CURRENT_ASSET";
		String glAcctTypeIdcharges = "OTHER_INCOME";
		String acctgTransId = createAccountingTransaction(loanApplication,
				acctgTransType, userLogin);
		
		log.info("### Transaction ID## "+acctgTransId);
		// Creates a record in AcctgTransEntry for Member Deposit Account
		createMemberDepositEntry(loanApplication, acctgTransId, userLogin,
				glAcctTypeIdMemberDepo);

		// Creates a record in AcctgTransEntry for Loans Receivable
		createLoanReceivableEntry(loanApplication, acctgTransId, userLogin,
				glAcctTypeIdLoans);

		String accountTransactionParentId = getAccountTransactionParentId(loanApplication, userLogin);
		// Creates Charge entry for each charge on loan application
		// Also creates a

		acctgTransType = "SERVICE_CHARGES";
		try {
			TransactionUtil.begin();
		} catch (GenericTransactionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		createChargesEntries(loanApplication, userLogin, glAcctTypeIdcharges, accountTransactionParentId);
		try {
			TransactionUtil.commit();
		} catch (GenericTransactionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			TransactionUtil.begin();
		} catch (GenericTransactionException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		createLoanDisbursementAccountingTransaction(loanApplication, userLogin, accountTransactionParentId);
		try {
			
			TransactionUtil.commit();
		} catch (GenericTransactionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		result.put("disbursementResult", "posted");
		return "";
	}


	private static String getAccountTransactionParentId(GenericValue loanApplication, Map<String, String> userLogin) {
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		GenericValue accountTransactionParent;
		String accountTransactionParentId = delegator
				.getNextSeqId("AccountTransactionParent");
		String createdBy = (String) userLogin.get("userLoginId");
		String updatedBy = (String) userLogin.get("userLoginId");
		String branchId = (String) userLogin.get("partyId");
		
		Long partyId =  loanApplication.getLong("partyId");
		
		String memberAccountId = getMemberAccountId(loanApplication);
		memberAccountId = memberAccountId.replaceAll(",", "");
		accountTransactionParent = delegator.makeValidValue("AccountTransactionParent",
				UtilMisc.toMap("accountTransactionParentId", accountTransactionParentId,
						"isActive", "Y", "createdBy", createdBy, "updatedBy",
						updatedBy, "branchId", branchId,
						"partyId", partyId,
						"memberAccountId", Long.valueOf(memberAccountId)));
		try {
			delegator.createOrStore(accountTransactionParent);
		} catch (GenericEntityException e) {
			e.printStackTrace();
			log.error("Could not create Transaction");
		}
		
		return accountTransactionParentId;
	}


	/****
	 * @author Japheth Odonya @when Aug 22, 2014 2:59:33 PM Create Disbursement
	 *         Transactions in the source document
	 * */
	private static void createLoanDisbursementAccountingTransaction(
			GenericValue loanApplication, Map<String, String> userLogin, String accountTransactionParentId) {
		// Create an Account Holder Transaction for this disbursement
		
		BigDecimal transactionAmount = loanApplication.getBigDecimal("loanAmt");
		
		Long savingsAccountProductId = getAccountProductGivenCodeId("999");
		
		String memberAccountId = getMemberAccountId(loanApplication, savingsAccountProductId);
		String transactionType = "LOANDISBURSEMENT";

		//Retention
		
		//Check if the Loan Retains BOSA 
		if ((loanApplication.getString("retainBOSADeposit") != null) && (loanApplication.getString("retainBOSADeposit").equals("Yes"))){
			//This loan retains for BOSA deposit e.g JEKI LOAN, lets get the percentage retained and add it to the
			// member deposits
			//BigDecimal percentageOfMemberNetSalaryAmt = 
			GenericValue loanProduct = LoanUtilities.getLoanProduct(loanApplication.getLong("loanProductId"));
			BigDecimal percentageDepositRetained = loanProduct.getBigDecimal("percentageDepositRetained");
			if (percentageDepositRetained != null){
				//Process the Retention
				BigDecimal bdBosaRetainedAmount = (percentageDepositRetained.divide(new BigDecimal(100), 4, RoundingMode.HALF_UP)).multiply(transactionAmount).setScale(4, RoundingMode.HALF_UP);
				
				Long memberDepositAccountId = getAccountProductGivenCodeId("901");
				
				String retentionTransactionType = "RETAINEDASDEPOSIT";
				
				createTransaction(loanApplication, retentionTransactionType, userLogin, memberDepositAccountId.toString(), bdBosaRetainedAmount, null, accountTransactionParentId);
				
				transactionAmount = transactionAmount.subtract(bdBosaRetainedAmount);
				
				
			} else{
				log.error(" percentageDepositRetained ########### The Retention Value is not specified #####333");
			}
			
			
		//	tt
		}

		createTransaction(loanApplication, transactionType, userLogin, memberAccountId, transactionAmount, null, accountTransactionParentId);
	}

	//Get Account Product Id given Code, for instance, Savings Account is code 999
	
	private static Long getAccountProductGivenCodeId(String code) {
		Long accountProductId = null;

		List<GenericValue> accountProductELI = new ArrayList<GenericValue>();
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		EntityConditionList<EntityExpr> accountProductConditions = EntityCondition
				.makeCondition(UtilMisc.toList(EntityCondition.makeCondition(
						"code", EntityOperator.EQUALS,
						code)),
						EntityOperator.AND);
		try {
			accountProductELI = delegator.findList("AccountProduct",
					accountProductConditions, null,
					null, null, false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}
		GenericValue accountProduct = null;
		for (GenericValue genericValue : accountProductELI) {
			accountProduct = genericValue;
		}
		
		if (accountProduct != null){
			accountProductId = accountProduct.getLong("accountProductId");
		}

		return accountProductId;
	}


	private static void createTransaction(GenericValue loanApplication, String transactionType, Map<String, String> userLogin, String memberAccountId,
			BigDecimal transactionAmount, String productChargeId, String accountTransactionParentId) {
		Delegator delegator = loanApplication.getDelegator();
		GenericValue accountTransaction;
		String accountTransactionId = delegator
				.getNextSeqId("AccountTransaction");
		String createdBy = (String) userLogin.get("userLoginId");
		String updatedBy = (String) userLogin.get("userLoginId");
		String branchId = (String) userLogin.get("partyId");
		Long partyId =  loanApplication.getLong("partyId");
		String increaseDecrease;
		if (productChargeId == null){
			increaseDecrease = "I";
		} else{
			increaseDecrease = "D";
			productChargeId = productChargeId.replaceAll(",", "");
		}
		Long productChargeIdLong;
		if (productChargeId != null){
			 productChargeIdLong = Long.valueOf(productChargeId);
		} else{
			productChargeIdLong = null;
		}
		 
		memberAccountId = memberAccountId.replaceAll(",", "");
		accountTransaction = delegator.makeValidValue("AccountTransaction",
				UtilMisc.toMap("accountTransactionId", accountTransactionId, 
						"accountTransactionParentId", accountTransactionParentId,
						"isActive", "Y", "createdBy", createdBy, "updatedBy",
						updatedBy, "branchId", branchId,
						"partyId", partyId,
						"increaseDecrease", increaseDecrease,
						"slipNumber", AccHolderTransactionServices.getNextSlipNumber(),
						"memberAccountId", Long.valueOf(memberAccountId),
						"productChargeId", productChargeIdLong,
						"transactionAmount", transactionAmount,
						"transactionType", transactionType));
		try {
			delegator.createOrStore(accountTransaction);
		} catch (GenericEntityException e) {
			e.printStackTrace();
			log.error("Could not create Transaction");
		}
	}

	/***
	 * @author Japheth Odonya @when Aug 22, 2014 5:54:48 PM Get a Member Account
	 *         Id given a loan application
	 * 
	 *         Pick out the Member and then look for a single Account in the
	 *         MemberAccount
	 * */
	private static String getMemberAccountId(GenericValue loanApplication) {
		String memberId = loanApplication.getString("partyId");

		List<GenericValue> memberAccountELI = new ArrayList<GenericValue>();
		Delegator delegator = loanApplication.getDelegator();
		memberId = memberId.replaceAll(",", "");
		EntityConditionList<EntityExpr> memberAccountConditions = EntityCondition
				.makeCondition(UtilMisc.toList(EntityCondition.makeCondition(
						"partyId", EntityOperator.EQUALS,
						Long.valueOf(memberId)), EntityCondition
						.makeCondition("withdrawable",
								EntityOperator.EQUALS, "Yes")),
						EntityOperator.AND);
		try {
			memberAccountELI = delegator.findList("MemberAccount",
					memberAccountConditions, null,
					null, null, false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}
		GenericValue memberAccount = null;
		for (GenericValue genericValue : memberAccountELI) {
			memberAccount = genericValue;
		}
		
		String memberAccountId = "";
		
		if (memberAccount != null){
			memberAccountId = memberAccount.getString("memberAccountId");
		}

		return memberAccountId;
	}
	
	
	private static String getMemberAccountId(GenericValue loanApplication, Long accountProductId) {
		String memberId = loanApplication.getString("partyId");

		List<GenericValue> memberAccountELI = new ArrayList<GenericValue>();
		Delegator delegator = loanApplication.getDelegator();
		memberId = memberId.replaceAll(",", "");
		EntityConditionList<EntityExpr> memberAccountConditions = EntityCondition
				.makeCondition(UtilMisc.toList(EntityCondition.makeCondition(
						"partyId", EntityOperator.EQUALS,
						Long.valueOf(memberId)),

						EntityCondition
									.makeCondition("accountProductId",
											EntityOperator.EQUALS, accountProductId)
								
						),
						EntityOperator.AND);
		try {
			memberAccountELI = delegator.findList("MemberAccount",
					memberAccountConditions, null,
					null, null, false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}
		GenericValue memberAccount = null;
		for (GenericValue genericValue : memberAccountELI) {
			memberAccount = genericValue;
		}
		
		String memberAccountId = "";
		
		if (memberAccount != null){
			memberAccountId = memberAccount.getString("memberAccountId");
		}

		return memberAccountId;
	}

	/***
	 * @author Japheth Odonya @when Aug 22, 2014 2:58:17 PM Creates Charge entry
	 *         for each charge on loan application
	 * **/
	private static void createChargesEntries(GenericValue loanApplication,
			Map<String, String> userLogin, String acctgTransType, String accountTransactionParentId) {
		// Give Loan Application get the charges and post to each
		Delegator delegator = loanApplication.getDelegator();
		List<GenericValue> loanApplicationChargeELI = null;
		String loanApplicationId = loanApplication
				.getString("loanApplicationId");
		
		//Pick only upfront payable charges like negotiation or appraisal fee
		// TODO - Add filter for upfront
		//chargedUpfront
		loanApplicationId = loanApplicationId.replaceAll(",", "");
		EntityConditionList<EntityExpr> chargesConditions = EntityCondition
				.makeCondition(UtilMisc.toList(EntityCondition.makeCondition(
						"loanApplicationId", EntityOperator.EQUALS,
						Long.valueOf(loanApplicationId)), EntityCondition
						.makeCondition("chargedUpfront",
								EntityOperator.EQUALS, "Y")),
						EntityOperator.AND);
		try {
			loanApplicationChargeELI = delegator.findList(
					"LoanApplicationCharge", chargesConditions, null,
					null, null, false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}

		BigDecimal transactionAmount;
		String memberAccountId = getMemberAccountId(loanApplication);
		// for each charge make a post
		for (GenericValue loanApplicationCharge : loanApplicationChargeELI) {
			// Make entries for this charge
			processCharge(loanApplication, userLogin, acctgTransType,
					loanApplicationCharge);
			
			//transactionAmount = loanApplicationCharge.getBigDecimal("fixedAmount");
			BigDecimal dbONEHUNDRED = new BigDecimal(100);
			transactionAmount = loanApplicationCharge.getBigDecimal("rateAmount").multiply(loanApplication.getBigDecimal("loanAmt")).divide(dbONEHUNDRED, 4, RoundingMode.HALF_UP);
			
			
			String transactionType = getChargeName(loanApplicationCharge);
			String productChargeId = loanApplicationCharge.getString("productChargeId");
			createTransaction(loanApplication, transactionType, userLogin, memberAccountId, transactionAmount, productChargeId, accountTransactionParentId);
		}

	}

	/**
	 * Make a double entry post for each charge
	 * */
	private static void processCharge(GenericValue loanApplication,
			Map<String, String> userLogin, String acctgTransType,
			GenericValue loanApplicationCharge) {
		String acctgTransId = createAccountingTransaction(loanApplication,
				acctgTransType, userLogin);
		
		log.info(" ### Transaction ID Charge ##### "+acctgTransId);
		
		//System.exit(0);
		// Get Service Charge Account (To Credit)
		String chargeAccountId = getChargeAccount(loanApplicationCharge);
		// Get Member Deposits Account (to Credit)
		String memberDepositsAccountId = getMemberDepositsAccountToCharge(loanApplication);
		BigDecimal dbChargeAmount = loanApplicationCharge
				.getBigDecimal("fixedAmount");

		Delegator delegator = loanApplication.getDelegator();
		String partyId = (String) userLogin.get("partyId");
		String postingType = "C";
		String entrySequenceId = "00001";
		// Post to charge service (Cr)
		postTransactionEntry(delegator, dbChargeAmount, partyId,
				chargeAccountId, postingType, acctgTransId, acctgTransType,
				entrySequenceId);
		// Debit the Member Deposit
		postingType = "D";
		entrySequenceId = "00002";
		postTransactionEntry(delegator, dbChargeAmount, partyId,
				memberDepositsAccountId, postingType, acctgTransId,
				acctgTransType, entrySequenceId);
	}

	private static String getMemberDepositsAccountToCharge(
			GenericValue loanApplication) {
		GenericValue accountHolderTransactionSetup = null;
		Delegator delegator = loanApplication.getDelegator();
		try {
			accountHolderTransactionSetup = delegator.findOne(
					"AccountHolderTransactionSetup", UtilMisc.toMap(
							"accountHolderTransactionSetupId",
							"MEMBERTRANSACTIONCHARGE"), false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
			log.error("######## Cannot Get Member Deposit Account in Member Transaction Charge, make sure there is a record in Account Holder Transaction Setup with ID MEMBERTRANSACTIONCHARGE and accounts configured ");
		}

		String memberDepositAccountId = "";
		if (accountHolderTransactionSetup != null) {
			memberDepositAccountId = accountHolderTransactionSetup
					.getString("memberDepositAccId");
		} else {
			log.error("######## Cannot Get Member Deposit Account in Member Transaction Charge, make sure there is a record in Account Holder Transaction Setup with ID MEMBERTRANSACTIONCHARGE and accounts configured ");
		}
		return memberDepositAccountId;
	}

	/***
	 * @author Japheth Odonya @when Aug 22, 2014 5:13:27 PM Get Charge Account
	 *         for the charge in the application
	 * */
	private static String getChargeAccount(GenericValue loanApplicationCharge) {
		String productChargeId = loanApplicationCharge
				.getString("productChargeId");

		Delegator delegator = loanApplicationCharge.getDelegator();
		GenericValue productCharge = null;

		try {
			productCharge = delegator.findOne("ProductCharge",
					UtilMisc.toMap("productChargeId", Long.valueOf(productChargeId)), false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
			log.error("######## Cannot get product charge ");
		}

		String chargeAccountId = "";
		if (productCharge != null) {
			chargeAccountId = productCharge.getString("chargeAccountId");
		} else {
			log.error("######## Cannot get product charge !! ");
		}
		return chargeAccountId;
	}
	
	private static String getChargeName(GenericValue loanApplicationCharge) {
		String productChargeId = loanApplicationCharge
				.getString("productChargeId");

		Delegator delegator = loanApplicationCharge.getDelegator();
		GenericValue productCharge = null;

		try {
			productCharge = delegator.findOne("ProductCharge",
					UtilMisc.toMap("productChargeId", Long.valueOf(productChargeId)), false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
			log.error("######## Cannot get product charge ");
		}

		String name = "";
		if (productCharge != null) {
			name = productCharge.getString("name");
		} else {
			log.error("######## Cannot get product charge !! ");
		}
		return name;
	}


	/**
	 * @author Japheth Odonya @when Aug 22, 2014 2:58:55 PM Creates a record in
	 *         AcctgTransEntry for Loans Receivable
	 * */
	private static void createLoanReceivableEntry(GenericValue loanApplication,
			String acctgTransId, Map<String, String> userLogin,
			String acctgTransType) {
		Delegator delegator = loanApplication.getDelegator();
		// Credit Member Deposit Account
		String loanReceivableAccount = getLoanReceivableAccount(delegator);
		String partyId = (String) userLogin.get("partyId");
		BigDecimal bdLoanAmount = loanApplication.getBigDecimal("loanAmt");
		
		String postingType = "D";
		String entrySequenceId = "00002";
		postTransactionEntry(delegator, bdLoanAmount, partyId,
				loanReceivableAccount, postingType, acctgTransId,
				acctgTransType, entrySequenceId);
	}

	public static void postTransactionEntry(Delegator delegator,
			BigDecimal bdLoanAmount, String partyId,
			String loanReceivableAccount, String postingType,
			String acctgTransId, String acctgTransType, String entrySequenceId) {
		GenericValue acctgTransEntry;
		acctgTransEntry = delegator
				.makeValidValue("AcctgTransEntry", UtilMisc.toMap(
						"acctgTransId", acctgTransId, "acctgTransEntrySeqId",
						entrySequenceId, "partyId", partyId, "glAccountTypeId",
						acctgTransType, "glAccountId", loanReceivableAccount,

						"organizationPartyId", "Company", "amount", bdLoanAmount,
						"currencyUomId", "KES", "origAmount", bdLoanAmount,
						"origCurrencyUomId", "KES", "debitCreditFlag",
						postingType, "reconcileStatusId", "AES_NOT_RECONCILED"));
		
		try {
			delegator.createOrStore(acctgTransEntry);
		} catch (GenericEntityException e) {
			e.printStackTrace();
			log.error("Could post an entry");
		}
	}

	/***
	 * @author Japheth Odonya @when Aug 22, 2014 2:59:02 PM Creates a record in
	 *         AcctgTransEntry for Member Deposit Account
	 * */
	private static void createMemberDepositEntry(GenericValue loanApplication,
			String acctgTransId, Map<String, String> userLogin,
			String acctgTransType) {
		Delegator delegator = loanApplication.getDelegator();
		// Credit Member Deposit Account
		String memberDepositAccount = getMemberDepositAccount(delegator);
		String partyId = (String) userLogin.get("partyId");
		String postingType = "C";
		String entrySequenceId = "00001";
		BigDecimal bdLoanAmount = loanApplication.getBigDecimal("loanAmt");
		postTransactionEntry(delegator, bdLoanAmount, partyId,
				memberDepositAccount, postingType, acctgTransId,
				acctgTransType, entrySequenceId);
	}

	/**
	 * Get Member Deposit Account
	 * 
	 * */
	private static String getMemberDepositAccount(Delegator delegator) {
		GenericValue accountHolderTransactionSetup = null;

		try {
			accountHolderTransactionSetup = delegator.findOne(
					"AccountHolderTransactionSetup", UtilMisc.toMap(
							"accountHolderTransactionSetupId",
							"LOANDISBURSEMENTACCOUNT"), false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
			log.error("######## Cannot Get Member Deposit Account in Loan Disbursement, make sure there is a rcord in Account Holder Transaction Setup with ID LOANDISBURSEMENTACCOUNT and accounts configured ");
			return "Cannot Get Loan Disbursement account";
		}

		String memberDepositAccountId = "";
		if (accountHolderTransactionSetup != null) {
			memberDepositAccountId = accountHolderTransactionSetup
					.getString("memberDepositAccId");
		} else {
			log.error("######## Cannot Get Member Deposit Account in Loan Disbursement, make sure there is a rcord in Account Holder Transaction Setup with ID LOANDISBURSEMENTACCOUNT and accounts configured ");
		}
		return memberDepositAccountId;
	}

	private static String getLoanReceivableAccount(Delegator delegator) {
		GenericValue accountHolderTransactionSetup = null;

		// SaccoProduct
		try {
			accountHolderTransactionSetup = delegator.findOne(
					"AccountHolderTransactionSetup", UtilMisc.toMap(
							"accountHolderTransactionSetupId",
							"LOANDISBURSEMENTACCOUNT"), false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
			log.error("######## Cannot Get Loan Receivable Account in Loan Disbursement, make sure there is a rcord in Account Holder Transaction Setup with ID LOANDISBURSEMENTACCOUNT and accounts configured ");
			return "Cannot Get Loan Receivable Account in Disbursement ";
		}

		String loanReceivableAccount = "";
		if (accountHolderTransactionSetup != null) {
			loanReceivableAccount = accountHolderTransactionSetup
					.getString("cashAccountId");
		} else {
			log.error("######## Cannot Get Loan Disbursement account, make sure there is a rcord in Account Holder Transaction Setup with ID LOANDISBURSEMENTACCOUNT and accounts configured ");
		}
		return loanReceivableAccount;
	}

	/***
	 * @author Japheth Odonya @when Aug 22, 2014 2:59:07 PM Creates a record in
	 *         AcctgTrans
	 * **/
	public static String createAccountingTransaction(
			GenericValue loanApplication, String acctgTransType,
			Map<String, String> userLogin) {

		GenericValue acctgTrans;
		String acctgTransId;
		Delegator delegator = loanApplication.getDelegator();
		acctgTransId = delegator.getNextSeqId("AcctgTrans");

		String partyId = (String) userLogin.get("partyId");
		String createdBy = (String) userLogin.get("userLoginId");

		Timestamp currentDateTime = new Timestamp(Calendar.getInstance()
				.getTimeInMillis());
		acctgTrans = delegator.makeValidValue("AcctgTrans", UtilMisc.toMap(
				"acctgTransId", acctgTransId,
				"acctgTransTypeId", acctgTransType,
				"transactionDate",
				currentDateTime, "isPosted", "Y", "postedDate",
				currentDateTime, "glFiscalTypeId", "ACTUAL", "partyId",
				partyId, "createdByUserLogin", createdBy, "createdDate",
				currentDateTime, "lastModifiedDate", currentDateTime,
				"lastModifiedByUserLogin", createdBy));
//		try {
//			acctgTrans = delegator.createSetNextSeqId(acctgTrans);
//		} catch (GenericEntityException e1) {
//			e1.printStackTrace();
//		}
		try {
			delegator.createOrStore(acctgTrans);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}

		return acctgTransId;
	}

}
